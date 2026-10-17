# yudi_mac

Xiaomi Pad 6 Max 14（`yudi` / `2307BRPDCC`）的 WiFi MAC 修改器，适用于已获取 KernelSU / Magisk root 的设备。

A small WiFi MAC changer for Xiaomi Pad 6 Max (yudi) on rooted Android (KernelSU / Magisk).

> 只修改 WiFi 的 MAC，不修改序列号、蓝牙地址或 IMEI。

## 适用设备

- 小米平板 6 Max 14（`yudi`，骁龙 8+ Gen 1）
- WiFi 芯片：`qca6490`（`ro.hardware.wlan.chip=qca6490`）
- Android 14 / HyperOS（开发时验证版本：1.0.17.0.UMHCNXM）
- 需要 root（KernelSU 或 Magisk）

**不是通用工具。** 其他机型如果 persist 路径或文件格式不同，需要自行适配，不要直接写入。

## 原理

这台机器的 WiFi 驱动不支持运行时修改 MAC：

```text
ip link set wlan0 address 02:11:22:33:44:55
RTNETLINK answers: Unknown error 524
```

真正的 MAC 来自：

```text
/mnt/vendor/persist/wlan/wlan_mac.bin
wlan0=001122334455
```

驱动只在上电/加载时读取该文件，因此写入后必须重启。
驱动还会强制首字节为全局地址，例如 `02:` 会被规范成 `00:`。

另外，Android 的“随机 MAC”是按 WiFi 网络单独保存的。修改 persist 后，还需要在：

```text
设置 → WLAN → 当前网络 → 隐私 → 使用设备 MAC
```

把该网络改成“使用设备 MAC”，否则路由器看到的仍然是随机地址。

## 功能

- 显示当前设备 MAC、当前网络 MAC、persist 文件内容
- 输入 MAC 或随机生成
- 写入 persist 后自动重启
- 首次应用自动备份原厂文件到 `/data/adb/wifimac/original.bin`
- 一键恢复原厂 MAC
- 对首字节的 `0x02` 规范化给出提示
- 检测当前网络是否还在使用随机 MAC

## 使用

1. 安装 `MacChanger.apk`，或自行编译。
2. 打开 App，授予 root 权限。
3. 输入 12 位十六进制 MAC，例如 `B8:EA:98:12:34:56`。
4. 点“应用新 MAC”，确认后平板重启。
5. 重启后到 WLAN 设置里把当前网络改成“使用设备 MAC”。
6. 想还原时点“恢复原厂 MAC 并重启”。

命令行方式：

```bash
adb push tools/mac.sh /data/local/tmp/
adb shell su -c "sh /data/local/tmp/mac.sh show"
adb shell su -c "sh /data/local/tmp/mac.sh set B8:EA:98:12:34:56"
adb shell su -c "sh /data/local/tmp/mac.sh restore"
```

## 编译

环境：

- Windows / PowerShell
- JDK 17（`javac`、`keytool` 在 PATH 中）
- Android SDK，包含 `build-tools` 和 `platforms`
- 设置 `ANDROID_SDK_ROOT` 或 `ANDROID_HOME`；未设置时默认使用 `%LOCALAPPDATA%\Android\Sdk`

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

输出：

```text
dist/MacChanger.apk
```

如果加 `-Install` 会自动执行 `adb install -r`。

首次编译会在 `keystore/debug.keystore` 生成本地调试签名，密码 `android`。
**不要把这个签名文件提交到仓库**；它已在 `.gitignore` 中排除。

## 目录

```text
app/
  AndroidManifest.xml
  res/                         图标、主题、字符串
  src/com/macchanger/pad/      MainActivity.java
tools/
  mac.sh                       命令行脚本
  detect.sh                    只读探测其他机型的 persist 路径/格式
  make_icon.py                 生成图标（可选）
build.ps1                      一键编译
```

## 其他机型

`tools/detect.sh` 只读地输出设备信息、WiFi 芯片、工厂 MAC、候选 persist 文件。换机型前先跑它：

```bash
adb push tools/detect.sh /data/local/tmp/
adb shell su -c "sh /data/local/tmp/detect.sh"
```

如果候选文件里有一个内容正好等于工厂 MAC，并且格式是 `wlan0=` 或 `Intf0MacAddress=`，才有适配的基础。
博通/联发科平台与高通完全不同，不能直接套用。

## 风险

- 写 persist 属于底层修改，操作前请确认自己有能力恢复；App 会备份原厂文件，但恢复出厂/清 data 可能丢失备份。
- 修改 MAC 可能违反校园网、公司网或运营商的使用条款；请只用于自己的设备和自己有权管理的网络。
- 不要用于绕过计费、认证、MAC 过滤等未授权场景。
- 本项目按“现状”提供，不对设备损坏、网络封禁或账号问题负责。

## License

MIT
