#!/system/bin/sh
# WiFi MAC 修改脚本 — 小米平板 6 Max (yudi / qca6490)
#
# 用法：
#   su -c "sh mac.sh show"
#   su -c "sh mac.sh set B8:EA:98:12:34:56"
#   su -c "sh mac.sh set B8:EA:98:12:34:56 --no-reboot"
#   su -c "sh mac.sh random"
#   su -c "sh mac.sh restore"
#
# 原理：驱动只在上电时读取 /mnt/vendor/persist/wlan/wlan_mac.bin，
#       所以写入后必须重启。驱动会强制首字节为全局地址（02 会变 00）。

set -u

FILE=/mnt/vendor/persist/wlan/wlan_mac.bin
BAK=/data/adb/wifimac/original.bin

usage() {
    echo "用法:"
    echo "  sh mac.sh show"
    echo "  sh mac.sh set XX:XX:XX:XX:XX:XX [--no-reboot]"
    echo "  sh mac.sh random [--no-reboot]"
    echo "  sh mac.sh restore"
    exit 1
}

need_root() {
    [ "$(id -u)" = "0" ] || { echo "错误：需要 root（su）"; exit 1; }
}

show() {
    echo "persist  : $(cat "$FILE" 2>/dev/null)"
    echo "设备 MAC : $(cat /sys/class/net/wlan0/address 2>/dev/null)"
    echo "工厂 MAC : $(dumpsys wifi 2>/dev/null | grep -m1 -E '^[[:space:]]*wifi_sta_factory_mac_address=' | cut -d= -f2)"
    if [ -f "$BAK" ]; then echo "原厂备份 : 有 ($BAK)"; else echo "原厂备份 : 无"; fi
}

normalize() {
    echo "$1" | sed 's/[^0-9A-Fa-f]//g' | tr 'abcdef' 'ABCDEF'
}

valid() {
    [ ${#1} -eq 12 ] || return 1
    case "$1" in
        *[!0-9A-F]*) return 1 ;;
    esac
    [ "$1" = "000000000000" ] && return 1
    first=$((0x$(echo "$1" | cut -c1-2)))
    [ $((first & 1)) -eq 0 ] || return 1
    return 0
}

do_set() {
    raw="$1"
    noreboot="${2:-}"
    hex=$(normalize "$raw")

    if ! valid "$hex"; then
        echo "错误：MAC 无效（需要 12 位十六进制、单播、不能全 0）"
        exit 1
    fi

    first=$((0x$(echo "$hex" | cut -c1-2)))
    orig=$(printf '%02X' "$first")
    eff=$(printf '%02X' $((first & 0xFC)))
    effhex="$eff$(echo "$hex" | cut -c3-12)"

    if [ "$eff" != "$orig" ]; then
        echo "注意：驱动会把首字节 $orig 规范为 $eff"
    fi

    mkdir -p /data/adb/wifimac
    [ -f "$BAK" ] || cp -f "$FILE" "$BAK"

    printf 'wlan0=%s\n' "$effhex" > "$FILE.new" || exit 1
    chmod 0666 "$FILE.new"
    mv -f "$FILE.new" "$FILE" || exit 1
    sync

    echo "已写入: $(cat "$FILE")"
    if [ "$noreboot" = "--no-reboot" ]; then
        echo "请手动重启平板后生效。"
    else
        echo "正在重启…"
        reboot
    fi
}

do_restore() {
    [ -f "$BAK" ] || { echo "错误：没有原厂备份 $BAK"; exit 1; }
    cp -f "$BAK" "$FILE.new" || exit 1
    chmod 0666 "$FILE.new"
    mv -f "$FILE.new" "$FILE" || exit 1
    sync
    echo "已恢复: $(cat "$FILE")"
    echo "正在重启…"
    reboot
}

need_root

case "${1:-}" in
    show)
        show
        ;;
    set)
        [ $# -ge 2 ] || usage
        do_set "$2" "${3:-}"
        ;;
    random)
        r() { printf '%02X' $((RANDOM % 256)); }
        do_set "B8EA98$(r)$(r)$(r)" "${2:-}"
        ;;
    restore)
        do_restore
        ;;
    *)
        usage
        ;;
esac
