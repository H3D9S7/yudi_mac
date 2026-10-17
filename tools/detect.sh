#!/system/bin/sh
# 只读探测脚本：判断其他机型能否套用“persist 改 MAC”方案
# 用法：su -c "sh detect.sh"

echo "===== 设备 ====="
echo "厂商/型号: $(getprop ro.product.manufacturer) $(getprop ro.product.model)"
echo "代号     : $(getprop ro.product.device)"
echo "系统     : Android $(getprop ro.build.version.release) (SDK $(getprop ro.build.version.sdk))"
echo "指纹     : $(getprop ro.build.fingerprint)"

echo
echo "===== WiFi ====="
echo "芯片     : $(getprop ro.hardware.wlan.chip)"
echo "厂商     : $(getprop ro.hardware.wlan.vendor)"
echo "驱动状态 : $(getprop wlan.driver.status)"
echo "接口     : $(getprop wifi.interface)"
echo "当前 MAC : $(cat /sys/class/net/wlan0/address 2>/dev/null)"
echo "工厂 MAC : $(dumpsys wifi 2>/dev/null | grep -m1 -E '^[[:space:]]*wifi_sta_factory_mac_address=' | cut -d= -f2)"
echo "连接 MAC : $(cmd wifi status 2>/dev/null | sed -n 's/.*WifiInfo: SSID: "[^"]*", BSSID: [^,]*, MAC: \([0-9a-fA-F:]*\).*/\1/p' | head -1)"

echo
echo "===== 加载的 WiFi 驱动 ====="
lsmod 2>/dev/null | grep -iE 'wlan|wifi|qcacld|qca|bcmdhd|brcm|cnss' | head -20

echo
echo "===== 候选 MAC 文件 ====="
for f in \
    /mnt/vendor/persist/wlan/wlan_mac.bin \
    /mnt/vendor/persist/wifi/wlan_mac.bin \
    /mnt/vendor/persist/wlan_mac.bin \
    /persist/wlan_mac.bin \
    /persist/wlan/wlan_mac.bin \
    /persist/wifi/mac \
    /data/vendor/wifi/wlan_mac.bin \
    /data/vendor/wifi/mac
do
    if [ -f "$f" ]; then
        echo "--- $f"
        ls -l "$f" 2>/dev/null
        cat "$f" 2>/dev/null
        echo
    fi
done

echo "===== persist 挂载 ====="
mount 2>/dev/null | grep -i persist

echo
echo "===== persist 目录 ====="
for d in /mnt/vendor/persist /persist; do
    echo "--- $d"
    ls -la "$d" 2>/dev/null | head -40
done

echo
echo "===== 搜索 wlan/mac/wifi 文件 ====="
find /mnt/vendor/persist /persist -maxdepth 3 \
    \( -iname '*mac*' -o -iname '*wlan*' -o -iname '*wifi*' \) 2>/dev/null | head -40

echo
echo "===== 相关属性 ====="
getprop 2>/dev/null | grep -iE 'wlan|wifi|mac' | head -40
