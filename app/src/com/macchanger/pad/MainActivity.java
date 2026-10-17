package com.macchanger.pad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {

    private static final int BG       = 0xFF0E1116;
    private static final int CARD     = 0xFF171B22;
    private static final int STROKE   = 0xFF262C36;
    private static final int TEXT     = 0xFFEDEFF2;
    private static final int MUTED    = 0xFF9BA3AF;
    private static final int ACCENT   = 0xFF4C8DFF;
    private static final int ACCENT_D = 0xFF2360C4;
    private static final int WARN     = 0xFFFFB020;
    private static final int WARN_BG  = 0xFF2A1F0E;
    private static final int WARN_ST  = 0xFF5A431A;
    private static final int DANGER   = 0xFFFF5D5D;
    private static final int OK       = 0xFF3DDC84;
    private static final int INPUT_BG = 0xFF0F1319;

    private static final String MACFILE = "/mnt/vendor/persist/wlan/wlan_mac.bin";
    private static final String BACKUPDIR = "/data/adb/wifimac";
    private static final String BACKUPFILE = BACKUPDIR + "/original.bin";

    private static final String STATUS_CMD =
            "echo \"@@FACTORY=$(dumpsys wifi 2>/dev/null | grep -m1 -E '^[[:space:]]*wifi_sta_factory_mac_address=' | cut -d= -f2)\";" +
            "echo \"@@PERSIST=$(cat " + MACFILE + " 2>/dev/null)\";" +
            "echo \"@@LINK=$(cat /sys/class/net/wlan0/address 2>/dev/null)\";" +
            "echo \"@@SSID=$(cmd wifi status 2>/dev/null | sed -n 's/.*WifiInfo: SSID: \"\\([^\"]*\\)\".*/\\1/p' | head -1)\";" +
            "echo \"@@CONN=$(cmd wifi status 2>/dev/null | sed -n 's/.*WifiInfo: SSID: \"[^\"]*\", BSSID: [^,]*, MAC: \\([0-9a-fA-F:]*\\).*/\\1/p' | head -1)\";" +
            "echo \"@@BACKUP=$([ -f " + BACKUPFILE + " ] && echo yes || echo no)\"";

    private final Handler ui = new Handler(Looper.getMainLooper());

    private TextView tvDevice, tvCurrent, tvPersist, tvBackup, tvRoot, tvWarn, tvNote;
    private LinearLayout warnBox;
    private EditText etMac;
    private Button btnApply, btnRestore, btnRandom, btnRefresh;
    private CheckBox cbReboot;
    private ProgressBar bar;

    private boolean rooted = false;
    private boolean working = false;

    private String deviceMac = "";
    private String currentMac = "";
    private String persistMac = "";
    private String ssid = "";
    private boolean hasBackup = false;

    private interface Callback { void done(int code, String out); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        setContentView(buildUi());
        checkRoot();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (rooted) refresh();
    }

    // ------------------------------------------------------------------ UI

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setLineSpacing(0f, 1.2f);
        return t;
    }

    private TextView mono(String s, float sp, int color) {
        TextView t = text(s, sp, color, false);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        return t;
    }

    private GradientDrawable shape(int fill, float radiusDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) g.setStroke(dp(1), strokeColor);
        return g;
    }

    private Button button(String label, int fill, int fg, int strokeColor) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(fg);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(16), dp(12), dp(16), dp(12));
        b.setStateListAnimator(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),
                shape(fill, 12, strokeColor), null));
        return b;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        c.setBackground(shape(CARD, 16, STROKE));
        return c;
    }

    private View kv(String key, TextView value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        row.addView(text(key, 13, MUTED, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(value, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void addBlock(LinearLayout parent, View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        parent.addView(v, lp);
    }

    private View buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(BG);
        sv.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(26), dp(20), dp(36));

        root.addView(text("WiFi MAC 修改器", 25, TEXT, true));
        TextView sub = text("Xiaomi " + Build.MODEL + " · Android " + Build.VERSION.RELEASE
                + " · qca6490", 12, MUTED, false);
        sub.setPadding(0, dp(5), 0, 0);
        root.addView(sub);

        // ---------- status card ----------
        LinearLayout sc = card();
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("设备状态", 15, TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnRefresh = button("刷新", 0x00000000, ACCENT, 0);
        btnRefresh.setTextSize(13);
        btnRefresh.setPadding(dp(10), dp(6), dp(10), dp(6));
        head.addView(btnRefresh);
        sc.addView(head);

        tvDevice = mono("—", 14, TEXT);
        tvCurrent = mono("—", 14, TEXT);
        tvPersist = mono("—", 13, TEXT);
        sc.addView(kv("设备 MAC", tvDevice));
        sc.addView(kv("当前网络", tvCurrent));
        sc.addView(kv("persist", tvPersist));

        tvBackup = text("原厂备份：检查中…", 11, MUTED, false);
        tvBackup.setPadding(0, dp(6), 0, 0);
        sc.addView(tvBackup);
        tvRoot = text("Root：检测中…", 11, MUTED, false);
        tvRoot.setPadding(0, dp(6), 0, 0);
        sc.addView(tvRoot);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        bar.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));
        bar.setVisibility(View.GONE);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(20), dp(20));
        blp.topMargin = dp(8);
        sc.addView(bar, blp);

        addBlock(root, sc);

        // ---------- warning ----------
        warnBox = new LinearLayout(this);
        warnBox.setOrientation(LinearLayout.VERTICAL);
        warnBox.setPadding(dp(16), dp(14), dp(16), dp(14));
        warnBox.setBackground(shape(WARN_BG, 16, WARN_ST));
        warnBox.setVisibility(View.GONE);
        tvWarn = text("", 12, 0xFFFFD08A, false);
        warnBox.addView(tvWarn);
        Button bWifi = button("打开 WLAN 设置", 0xFF3A2A10, 0xFFFFD08A, 0xFF6B4E1A);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = dp(12);
        warnBox.addView(bWifi, wlp);
        bWifi.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                } catch (Exception e) {
                    toast("无法打开 WLAN 设置");
                }
            }
        });
        addBlock(root, warnBox);

        // ---------- edit card ----------
        LinearLayout ec = card();
        ec.addView(text("修改 MAC", 16, TEXT, true));
        TextView d = text("输入 12 位十六进制地址。写入 persist 分区后，重启平板生效。", 12, MUTED, false);
        d.setPadding(0, dp(5), 0, dp(12));
        ec.addView(d);

        etMac = new EditText(this);
        etMac.setSingleLine(true);
        etMac.setTextSize(17);
        etMac.setTypeface(Typeface.MONOSPACE);
        etMac.setTextColor(TEXT);
        etMac.setHintTextColor(0xFF5C6470);
        etMac.setHint("B8:EA:98:12:34:56");
        etMac.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        etMac.setBackground(shape(INPUT_BG, 12, 0xFF2A313C));
        etMac.setPadding(dp(14), dp(13), dp(14), dp(13));
        ec.addView(etMac);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(10);
        btnRandom = button("随机生成", 0xFF232A35, TEXT, 0xFF30384A);
        tools.addView(btnRandom, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ec.addView(tools, tlp);

        cbReboot = new CheckBox(this);
        cbReboot.setText("写入后自动重启（推荐）");
        cbReboot.setTextSize(13);
        cbReboot.setTextColor(TEXT);
        cbReboot.setChecked(true);
        cbReboot.setButtonTintList(ColorStateList.valueOf(ACCENT));
        cbReboot.setPadding(0, dp(8), 0, dp(4));
        ec.addView(cbReboot);

        btnApply = button("应用新 MAC", ACCENT, 0xFFFFFFFF, 0);
        btnApply.setTextSize(15);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        alp.topMargin = dp(8);
        ec.addView(btnApply, alp);

        tvNote = text("驱动会强制首字节为全局地址：02/06/0A/0E 开头会变成 00/04/08/0C。", 11, MUTED, false);
        tvNote.setPadding(0, dp(10), 0, 0);
        ec.addView(tvNote);
        addBlock(root, ec);

        // ---------- restore card ----------
        LinearLayout rc = card();
        rc.addView(text("恢复原厂", 16, TEXT, true));
        TextView rd = text("首次应用时自动备份原厂 MAC 到 " + BACKUPFILE + "。", 12, MUTED, false);
        rd.setPadding(0, dp(5), 0, dp(12));
        rc.addView(rd);
        btnRestore = button("恢复原厂 MAC 并重启", 0x00000000, DANGER, 0xFF5A2A2A);
        rc.addView(btnRestore, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        addBlock(root, rc);

        // ---------- help ----------
        LinearLayout hc = card();
        hc.addView(text("使用说明", 15, TEXT, true));
        TextView help = text(
                "1. 输入 MAC（或点“随机生成”），点“应用新 MAC”，平板会自动重启。\n\n"
              + "2. 重启后进入 设置 → WLAN → 点击当前网络 → 隐私 → 选择“使用设备 MAC”。"
              + "否则路由器看到的仍是系统随机地址。\n\n"
              + "3. 修改只影响 WiFi 的 MAC，不会动序列号、蓝牙地址或 IMEI。\n\n"
              + "4. 若想换回，点“恢复原厂 MAC 并重启”即可。",
                12, MUTED, false);
        hc.addView(help, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addBlock(root, hc);

        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { checkRoot(); }
        });
        btnRandom.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { etMac.setText(randomMac()); etMac.setSelection(etMac.length()); }
        });
        btnApply.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onApplyClick(); }
        });
        btnRestore.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onRestoreClick(); }
        });

        sv.addView(root);
        return sv;
    }

    // ------------------------------------------------------------------ root

    private void checkRoot() {
        exec("id", new Callback() {
            @Override public void done(int code, String out) {
                rooted = out != null && out.contains("uid=0");
                tvRoot.setText(rooted ? "Root：已授权" : "Root：未授权，请在 KernelSU 中允许");
                tvRoot.setTextColor(rooted ? OK : DANGER);
                if (rooted) {
                    refresh();
                } else {
                    setBusy(false);
                    showRootError(out);
                }
            }
        });
    }

    private void showRootError(String out) {
        new AlertDialog.Builder(this)
                .setTitle("需要 Root 权限")
                .setMessage("请在 KernelSU 管理器中允许“MAC 修改器”使用 root，然后点“重试”。\n\n"
                        + (out == null ? "" : out.trim()))
                .setNegativeButton("关闭", null)
                .setNeutralButton("打开 KernelSU", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        try {
                            Intent i = getPackageManager()
                                    .getLaunchIntentForPackage("me.weishu.kernelsu");
                            if (i != null) startActivity(i);
                            else toast("未找到 KernelSU 管理器");
                        } catch (Exception e) {
                            toast("无法打开 KernelSU");
                        }
                    }
                })
                .setPositiveButton("重试", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { checkRoot(); }
                })
                .show();
    }

    private void refresh() {
        if (working) return;
        setBusy(true);
        exec(STATUS_CMD, new Callback() {
            @Override public void done(int code, String out) {
                setBusy(false);
                applyStatus(out);
            }
        });
    }

    private void applyStatus(String out) {
        String factory = val(out, "FACTORY");
        String persistRaw = val(out, "PERSIST");
        String conn = val(out, "CONN");
        String link = val(out, "LINK");
        String s = val(out, "SSID");
        hasBackup = "yes".equals(val(out, "BACKUP"));

        persistMac = parsePersist(persistRaw);
        deviceMac = fmtMac(factory);
        if (deviceMac.isEmpty()) deviceMac = fmtMac(link);
        if (deviceMac.isEmpty()) deviceMac = persistMac;

        if (conn == null || conn.length() != 17 || conn.startsWith("02:00:00:00:00:00")) conn = "";
        currentMac = conn.toUpperCase(Locale.US);
        ssid = (s == null || s.contains("unknown")) ? "" : s;

        tvDevice.setText(deviceMac.isEmpty() ? "—" : deviceMac);
        if (!currentMac.isEmpty()) {
            tvCurrent.setText((ssid.isEmpty() ? "" : ssid + " · ") + currentMac);
        } else {
            tvCurrent.setText(ssid.isEmpty() ? "未连接" : ssid + " · 未连接");
        }

        if (persistMac.isEmpty()) {
            tvPersist.setText("未找到");
            tvPersist.setTextColor(DANGER);
        } else if (!deviceMac.isEmpty() && !persistMac.equalsIgnoreCase(deviceMac)) {
            tvPersist.setText(persistMac + "（待重启生效）");
            tvPersist.setTextColor(WARN);
        } else {
            tvPersist.setText(persistMac);
            tvPersist.setTextColor(TEXT);
        }

        tvBackup.setText(hasBackup ? "原厂备份：已保存" : "原厂备份：首次应用时自动创建");
        tvBackup.setTextColor(hasBackup ? OK : MUTED);

        boolean randomInUse = !currentMac.isEmpty() && !deviceMac.isEmpty()
                && !currentMac.equalsIgnoreCase(deviceMac);
        if (randomInUse) {
            warnBox.setVisibility(View.VISIBLE);
            tvWarn.setText("当前网络" + (ssid.isEmpty() ? "" : " “" + ssid + "”")
                    + " 正在使用随机 MAC：" + currentMac
                    + "\n路由器看到的不是你的设备 MAC。请到 WLAN 设置里改为“使用设备 MAC”。");
        } else {
            warnBox.setVisibility(View.GONE);
        }
    }

    // ------------------------------------------------------------------ actions

    private void onApplyClick() {
        if (!rooted) { checkRoot(); return; }
        String hex = normalize(etMac.getText().toString());
        if (hex == null) {
            toast("格式不正确，请输入 12 位十六进制，如 B8:EA:98:12:34:56");
            return;
        }
        if (hex.equals("000000000000")) {
            toast("不能使用全 0 地址");
            return;
        }
        int first = Integer.parseInt(hex.substring(0, 2), 16);
        if ((first & 1) != 0) {
            toast("不能使用组播/广播地址（首字节最低位必须为 0）");
            return;
        }
        final String input = colonize(hex);
        final String effHex = String.format(Locale.US, "%02X", first & 0xFC) + hex.substring(2);
        final String eff = colonize(effHex);

        String msg = "将把设备 MAC 修改为：\n\n" + eff;
        if (!eff.equals(input)) {
            msg = "该驱动会强制首字节为全局地址：\n\n输入：" + input
                    + "\n实际生效：" + eff + "\n\n" + "将把设备 MAC 修改为：" + eff;
        }
        msg += "\n\n写入后需要重启平板才能生效。";

        new AlertDialog.Builder(this)
                .setTitle("确认修改")
                .setMessage(msg)
                .setNegativeButton("取消", null)
                .setPositiveButton("应用并重启", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        if (cbReboot.isChecked()) doApply(eff, effHex);
                        else confirmNoReboot(eff, effHex);
                    }
                })
                .show();
    }

    private void confirmNoReboot(final String eff, final String effHex) {
        new AlertDialog.Builder(this)
                .setTitle("仅写入")
                .setMessage("将写入 " + eff + "，但不会自动重启。请稍后手动重启使其生效。")
                .setNegativeButton("取消", null)
                .setPositiveButton("写入", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { doApply(eff, effHex); }
                })
                .show();
    }

    private void doApply(final String eff, final String effHex) {
        setBusy(true);
        String cmd = "mkdir -p " + BACKUPDIR + "; "
                + "if [ ! -f " + BACKUPFILE + " ]; then cp -f " + MACFILE + " " + BACKUPFILE + "; fi; "
                + "printf 'wlan0=" + effHex + "\\n' > " + MACFILE + ".new && "
                + "chmod 0666 " + MACFILE + ".new && "
                + "mv -f " + MACFILE + ".new " + MACFILE + " && sync && "
                + "cat " + MACFILE;
        exec(cmd, new Callback() {
            @Override public void done(int code, String out) {
                setBusy(false);
                if (out != null && out.contains("wlan0=" + effHex)) {
                    toast("写入成功");
                    refresh();
                    if (cbReboot.isChecked()) {
                        toast("正在重启…");
                        ui.postDelayed(new Runnable() {
                            @Override public void run() { exec("reboot", null); }
                        }, 1500);
                    }
                } else {
                    showError("写入失败", out == null ? "无输出" : out.trim());
                }
            }
        });
    }

    private void onRestoreClick() {
        if (!rooted) { checkRoot(); return; }
        exec("[ -f " + BACKUPFILE + " ] && echo YES || echo NO", new Callback() {
            @Override public void done(int code, String out) {
                if (out == null || !out.contains("YES")) {
                    showError("没有原厂备份", "还没有备份文件，无法恢复。\n\n备份路径：" + BACKUPFILE);
                    return;
                }
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("恢复原厂 MAC")
                        .setMessage("将用首次应用前备份的文件覆盖 persist，并重启平板。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("恢复并重启", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) { doRestore(); }
                        })
                        .show();
            }
        });
    }

    private void doRestore() {
        setBusy(true);
        String cmd = "cp -f " + BACKUPFILE + " " + MACFILE + ".new && "
                + "chmod 0666 " + MACFILE + ".new && "
                + "mv -f " + MACFILE + ".new " + MACFILE + " && sync && "
                + "cat " + MACFILE;
        exec(cmd, new Callback() {
            @Override public void done(int code, String out) {
                setBusy(false);
                if (out != null && out.contains("wlan0=")) {
                    toast("已恢复，正在重启…");
                    refresh();
                    ui.postDelayed(new Runnable() {
                        @Override public void run() { exec("reboot", null); }
                    }, 1500);
                } else {
                    showError("恢复失败", out == null ? "无输出" : out.trim());
                }
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    private void setBusy(boolean b) {
        working = b;
        bar.setVisibility(b ? View.VISIBLE : View.GONE);
        btnApply.setEnabled(!b && rooted);
        btnRestore.setEnabled(!b && rooted);
        btnRandom.setEnabled(!b);
        btnRefresh.setEnabled(!b);
    }

    private String randomMac() {
        Random r = new Random();
        int a = r.nextInt(256), b = r.nextInt(256), c = r.nextInt(256);
        String hex = String.format(Locale.US, "B8EA98%02X%02X%02X", a, b, c);
        return colonize(hex);
    }

    private String normalize(String s) {
        if (s == null) return null;
        String h = s.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.US);
        if (h.length() != 12) return null;
        for (int i = 0; i < 12; i++) {
            if (Character.digit(h.charAt(i), 16) < 0) return null;
        }
        return h;
    }

    private String colonize(String hex) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < hex.length(); i += 2) {
            if (i > 0) sb.append(':');
            sb.append(hex, i, i + 2);
        }
        return sb.toString().toUpperCase(Locale.US);
    }

    private String fmtMac(String raw) {
        if (raw == null) return "";
        String h = raw.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.US);
        if (h.length() != 12) return "";
        if (h.equals("000000000000")) return "";
        return colonize(h);
    }

    private String parsePersist(String raw) {
        if (raw == null) return "";
        int i = raw.indexOf('=');
        if (i < 0) return "";
        return fmtMac(raw.substring(i + 1));
    }

    private String val(String out, String key) {
        if (out == null) return "";
        String p = "@@" + key + "=";
        String[] lines = out.split("\n");
        for (String line : lines) {
            if (line.startsWith(p)) return line.substring(p.length()).trim();
        }
        return "";
    }

    private void exec(final String cmd, final Callback cb) {
        new Thread(new Runnable() {
            @Override public void run() {
                final Result r = runSu(cmd);
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (cb != null) cb.done(r.code, r.out);
                    }
                });
            }
        }, "mac-cmd").start();
    }

    private static class Result { int code; String out; }

    private Result runSu(String cmd) {
        Result r = new Result();
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("su", "-c", cmd);
            pb.redirectErrorStream(true);
            p = pb.start();
            r.out = readAll(p.getInputStream());
            if (!p.waitFor(30, TimeUnit.SECONDS)) {
                p.destroy();
                r.out = r.out + "\n[命令超时]";
            }
            try { r.code = p.exitValue(); } catch (Exception e) { r.code = -1; }
        } catch (Exception e) {
            r.code = -1;
            r.out = "EXEC_ERROR: " + e;
        } finally {
            if (p != null) try { p.destroy(); } catch (Exception ignored) { }
        }
        return r;
    }

    private String readAll(InputStream in) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toString("UTF-8");
        } catch (Exception e) {
            return "";
        } finally {
            try { in.close(); } catch (Exception ignored) { }
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void showError(String title, String msg) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("知道了", null)
                .show();
    }
}
