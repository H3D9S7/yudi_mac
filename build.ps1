param(
    [switch]$Install
)

$ErrorActionPreference = "Stop"

$root        = $PSScriptRoot
$app         = Join-Path $root "app"
$build       = Join-Path $app "build"
$dist        = Join-Path $root "dist"
$keystoreDir = Join-Path $root "keystore"
$keystore    = Join-Path $keystoreDir "debug.keystore"

# ---- Android SDK ----
$sdk = $env:ANDROID_SDK_ROOT
if (-not $sdk) { $sdk = $env:ANDROID_HOME }
if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
if (-not (Test-Path $sdk)) {
    throw "Android SDK not found. Set ANDROID_SDK_ROOT or ANDROID_HOME."
}

$btDir = Get-ChildItem (Join-Path $sdk "build-tools") -Directory -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending | Select-Object -First 1
$platDir = Get-ChildItem (Join-Path $sdk "platforms") -Directory -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not $btDir -or -not $platDir) {
    throw "SDK is missing build-tools or platforms."
}
$bt  = $btDir.FullName
$jar = Join-Path $platDir.FullName "android.jar"

# ---- JDK ----
$javac = (Get-Command javac -ErrorAction SilentlyContinue).Source
if (-not $javac) { throw "javac not found. Install JDK 17 and add it to PATH." }

$keytool = (Get-Command keytool -ErrorAction SilentlyContinue).Source
if (-not $keytool) {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME "bin\keytool.exe") }
    $candidates += (Join-Path (Split-Path $javac -Parent) "keytool.exe")
    $javaRoot = Join-Path $env:ProgramFiles "Java"
    if (Test-Path $javaRoot) {
        $candidates += @(Get-ChildItem $javaRoot -Directory |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName "bin\keytool.exe" })
    }
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) { $keytool = $candidate; break }
    }
}
if (-not $keytool) { throw "keytool not found. Set JAVA_HOME or add the JDK bin directory to PATH." }

if (-not (Test-Path $jar)) { throw "android.jar not found: $jar" }
if (-not (Test-Path (Join-Path $bt "aapt.exe"))) { throw "Incomplete build-tools: $bt" }

Remove-Item $build -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory $build -Force | Out-Null
New-Item -ItemType Directory $dist  -Force | Out-Null

Write-Host "[1/6] resources"
& "$bt\aapt.exe" package -f -M "$app\AndroidManifest.xml" -S "$app\res" -I $jar -F "$build\app.unsigned.apk"
if ($LASTEXITCODE -ne 0) { throw "aapt failed" }

Write-Host "[2/6] java"
$src = Get-ChildItem "$app\src" -Recurse -Filter *.java | ForEach-Object FullName
$oldEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
& $javac -nowarn -encoding UTF-8 -source 8 -target 8 -bootclasspath $jar -d "$build\classes" $src 2>$null
$javacExit = $LASTEXITCODE
$ErrorActionPreference = $oldEap
if ($javacExit -ne 0) { throw "javac failed" }

Write-Host "[3/6] dex"
New-Item -ItemType Directory "$build\dex" -Force | Out-Null
& "$bt\d8.bat" --lib $jar --min-api 29 --output "$build\dex" `
    (Get-ChildItem "$build\classes" -Recurse -Filter *.class | ForEach-Object FullName)
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Host "[4/6] package dex"
Push-Location "$build\dex"
& "$bt\aapt.exe" add "$build\app.unsigned.apk" classes.dex | Out-Null
Pop-Location
& "$bt\zipalign.exe" -f 4 "$build\app.unsigned.apk" "$build\app.aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Host "[5/6] sign"
New-Item -ItemType Directory $keystoreDir -Force | Out-Null
if (-not (Test-Path $keystore)) {
    & $keytool -genkeypair -keystore $keystore -storepass android -keypass android `
        -alias androiddebugkey -dname "CN=Android Debug,O=Android,C=US" `
        -keyalg RSA -keysize 2048 -validity 10000 | Out-Null
}
& "$bt\apksigner.bat" sign --ks $keystore --ks-pass pass:android --key-pass pass:android `
    --out "$dist\MacChanger.apk" "$build\app.aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

Write-Host "[6/6] verify"
& "$bt\apksigner.bat" verify --print-certs "$dist\MacChanger.apk" | Select-Object -First 2

Write-Host ""
Write-Host "Done: $dist\MacChanger.apk"
Get-Item "$dist\MacChanger.apk" | Select-Object Name, Length

if ($Install) {
    adb install -r "$dist\MacChanger.apk"
}
