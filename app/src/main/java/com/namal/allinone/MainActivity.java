package com.namal.allinone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.Callable;

public class MainActivity extends Activity {

    LinearLayout panel;
    TextView out;

    static final String DIR = "/sdcard/AllInOne";
    static final String RW = "(mount -o rw,remount /system 2>&1 || mount -o rw,remount / 2>&1)";
    static final String FIND_P =
            "P=$(ls /dev/block/by-name/up_param 2>/dev/null || find /dev/block -name up_param 2>/dev/null | head -n 1); ";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(32), dp(8), dp(8));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"Info", "build.prop", "Backup", "Network", "Boot/QMG", "UI", "Fonts/Sounds", "Hidden", "Modules", "Scan", "Terminal"};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            bar.addView(btn(names[i], () -> openTab(idx)));
        }
        hs.addView(bar);
        root.addView(hs);

        ScrollView ps = new ScrollView(this);
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        ps.addView(panel);
        root.addView(ps, new LinearLayout.LayoutParams(-1, 0, 1f));

        out = new TextView(this);
        out.setTextIsSelectable(true);
        out.setTextSize(11);
        out.setTypeface(Typeface.MONOSPACE);
        out.setText("Ready.");
        ScrollView os = new ScrollView(this);
        os.addView(out);
        root.addView(os, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
        openTab(0);
    }

    // ---------- helpers ----------

    int dp(int x) {
        return (int) (x * getResources().getDisplayMetrics().density);
    }

    Button btn(String t, Runnable r) {
        Button x = new Button(this);
        x.setText(t);
        x.setAllCaps(false);
        x.setOnClickListener(v -> r.run());
        return x;
    }

    EditText edit(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(13);
        return e;
    }

    TextView label(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(13);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(dp(4), dp(12), dp(4), dp(4));
        return v;
    }

    void confirm(String title, String msg, Runnable r) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("හරි", (d, w) -> r.run())
                .setNegativeButton("Cancel", null)
                .show();
    }

    void bg(Callable<String> c) {
        out.setText("Running...");
        new Thread(() -> {
            String r;
            try {
                r = c.call();
            } catch (Exception e) {
                r = "Error: " + e;
            }
            final String f = r;
            runOnUiThread(() -> out.setText(f));
        }).start();
    }

    void runSu(String cmd) {
        bg(() -> Sh.su(cmd));
    }

    void openTab(int i) {
        panel.removeAllViews();
        switch (i) {
            case 0: tabInfo(); break;
            case 1: tabProp(); break;
            case 2: tabBackup(); break;
            case 3: tabNet(); break;
            case 4: tabBoot(); break;
            case 5: tabUi(); break;
            case 6: tabMedia(); break;
            case 7: tabHidden(); break;
            case 8: tabModules(); break;
            case 9: tabScan(); break;
            case 10: tabTerm(); break;
        }
    }

    // ---------- Info ----------

    void tabInfo() {
        panel.addView(btn("Root check", () -> runSu("id")));
        panel.addView(btn("Device / system info", () -> bg(this::deviceInfo)));
    }

    String deviceInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Brand: ").append(Build.BRAND).append('\n');
        sb.append("Model: ").append(Build.MODEL).append('\n');
        sb.append("Device: ").append(Build.DEVICE).append('\n');
        sb.append("Android: ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Build: ").append(Build.DISPLAY).append('\n');
        sb.append("ABI: ").append(Build.SUPPORTED_ABIS[0]).append('\n');
        sb.append("Kernel: ").append(Sh.sh("uname -r").trim()).append('\n');
        sb.append("SELinux: ").append(Sh.sh("getenforce").trim()).append('\n');
        Intent bi = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bi != null) {
            int lvl = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int tmp = bi.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
            sb.append("Battery: ").append(lvl).append("%  ").append(tmp / 10.0).append(" C\n");
        }
        sb.append("\n").append(Sh.sh("head -n 3 /proc/meminfo"));
        return sb.toString();
    }

    // ---------- build.prop ----------

    void tabProp() {
        final EditText search = edit("search (හිස් නම් පළමු පේළි 80)");
        panel.addView(search);
        panel.addView(btn("Search build.prop", () -> {
            String s = search.getText().toString().trim();
            if (s.isEmpty()) runSu("head -n 80 /system/build.prop");
            else runSu("grep -i -- " + Sh.q(s) + " /system/build.prop");
        }));

        panel.addView(label("Edit"));
        final EditText key = edit("key (e.g. ro.sf.lcd_density)");
        final EditText val = edit("value");
        panel.addView(key);
        panel.addView(val);

        panel.addView(btn("1) setprop (තාවකාලික, reboot වෙනකම්)", () -> {
            String k = key.getText().toString().trim();
            String v = val.getText().toString().trim();
            if (!validKV(k, v)) return;
            runSu("setprop " + k + " " + Sh.q(v) + "; getprop " + k);
        }));

        panel.addView(btn("2) Magisk/KSU module එකට save (ආරක්ෂිතයි)", () -> {
            String k = key.getText().toString().trim();
            String v = val.getText().toString().trim();
            if (!validKV(k, v)) return;
            runSu(propModuleCmd(k, v));
        }));

        panel.addView(btn("3) /system/build.prop එකට ලියන්න", () -> {
            String k = key.getText().toString().trim();
            String v = val.getText().toString().trim();
            if (!validKV(k, v)) return;
            confirm("build.prop ලියන්නද?",
                    "Backup එකක් auto ගන්නවා. /system read-only නම් fail වෙනවා. "
                            + "වැරදි value එකකින් bootloop වෙන්න පුළුවන්.",
                    () -> runSu(writeCmd(k, v)));
        }));

        panel.addView(btn("Props module එක අයින් කරන්න", () ->
                runSu("rm -rf /data/adb/modules/allinone_props && echo 'removed. reboot කරන්න'")));
    }

    boolean validKV(String k, String v) {
        if (!k.matches("[A-Za-z0-9._-]+")) {
            out.setText("Key වැරදියි (A-Z a-z 0-9 . _ - විතරයි)");
            return false;
        }
        if (!v.matches("[A-Za-z0-9._,:+/@ -]*")) {
            out.setText("Value එකේ අකුරු අවසර නෑ");
            return false;
        }
        return true;
    }

    String writeCmd(String k, String v) {
        String kk = k.replace(".", "\\.");
        return "mkdir -p " + DIR + " && cp /system/build.prop " + DIR + "/build.prop.$(date +%s) && "
                + RW + "; "
                + "if grep -q '^" + kk + "=' /system/build.prop; then "
                + "sed -i 's|^" + kk + "=.*|" + k + "=" + v + "|' /system/build.prop; "
                + "else echo '" + k + "=" + v + "' >> /system/build.prop; fi; "
                + "grep '^" + kk + "=' /system/build.prop";
    }

    String propModuleCmd(String k, String v) {
        String kk = k.replace(".", "\\.");
        return "D=/data/adb/modules/allinone_props; mkdir -p $D && "
                + "{ [ -f $D/module.prop ] || printf 'id=allinone_props\\nname=AllInOne Props\\n"
                + "version=1\\nversionCode=1\\nauthor=Namal\\ndescription=Props from AllInOne\\n' > $D/module.prop; } && "
                + "touch $D/system.prop && sed -i '/^" + kk + "=/d' $D/system.prop && "
                + "echo '" + k + "=" + v + "' >> $D/system.prop && cat $D/system.prop && "
                + "echo '--- reboot කරන්න'";
    }

    // ---------- Backup ----------

    void tabBackup() {
        panel.addView(btn("build.prop BACKUP", () ->
                runSu("mkdir -p " + DIR + " && cp /system/build.prop " + DIR
                        + "/build.prop.$(date +%s) && ls -l " + DIR)));
        panel.addView(btn("Backups list", () -> runSu("ls -l " + DIR)));
        panel.addView(btn("Latest build.prop backup එක restore", () ->
                confirm("Restore කරන්නද?",
                        "අන්තිම build.prop backup එක /system එකට copy කරනවා.",
                        () -> runSu(restoreCmd()))));
    }

    String restoreCmd() {
        return "F=$(ls -t " + DIR + "/build.prop.* 2>/dev/null | head -n 1); "
                + "if [ -z \"$F\" ]; then echo 'backup නෑ'; else " + RW + "; "
                + "cp \"$F\" /system/build.prop && echo \"restored: $F\" || echo 'restore fail'; fi";
    }

    // ---------- Network ----------

    void tabNet() {
        panel.addView(btn("TCP info", () -> runSu(
                "for f in tcp_congestion_control tcp_available_congestion_control "
                        + "tcp_allowed_congestion_control; do echo $f: $(cat /proc/sys/net/ipv4/$f); done")));
        panel.addView(btn("BBR set කරන්න (reboot වලින් reset)", () -> runSu(
                "if grep -qw bbr /proc/sys/net/ipv4/tcp_available_congestion_control; then "
                        + "echo bbr > /proc/sys/net/ipv4/tcp_congestion_control; echo 'BBR set'; "
                        + "else echo 'මේ kernel එකේ BBR නෑ'; fi; "
                        + "cat /proc/sys/net/ipv4/tcp_congestion_control")));
        panel.addView(btn("Ping test (8.8.8.8)", () -> bg(() -> Sh.sh("ping -c 4 8.8.8.8"))));
    }

    // ---------- Boot / QMG ----------

    void tabBoot() {
        panel.addView(label("up_param (boot logo)"));
        panel.addView(btn("up_param BACKUP", () -> runSu(
                FIND_P + "if [ -z \"$P\" ]; then echo 'up_param partition නෑ'; else "
                        + "mkdir -p " + DIR + " && dd if=$P of=" + DIR + "/up_param.backup.img bs=4096 && "
                        + "ls -l " + DIR + "/up_param.backup.img; fi")));
        panel.addView(btn("Custom up_param FLASH", () ->
                confirm("up_param flash කරන්නද?",
                        DIR + "/up_param_custom.img එක partition එකට dd කරනවා. "
                                + "මුලින් BACKUP එක ගන්න. වැරදි image එකකින් boot logo අවුල් වෙන්න පුළුවන්.",
                        () -> runSu(FIND_P
                                + "C=" + DIR + "/up_param_custom.img; B=" + DIR + "/up_param.backup.img; "
                                + "if [ -z \"$P\" ]; then echo 'up_param නෑ'; "
                                + "elif [ ! -f $B ]; then echo 'මුලින් backup ගන්න'; "
                                + "elif [ ! -f $C ]; then echo \"$C නෑ\"; "
                                + "elif [ $(stat -c %s $C) -gt $(blockdev --getsize64 $P) ]; then echo 'image ලොකු වැඩියි'; "
                                + "else dd if=$C of=$P bs=4096 && sync && echo 'flashed. reboot කරන්න'; fi"))));
        panel.addView(btn("up_param RESTORE (backup එකෙන්)", () ->
                confirm("Restore කරන්නද?", "Backup image එක partition එකට dd කරනවා.",
                        () -> runSu(FIND_P
                                + "B=" + DIR + "/up_param.backup.img; "
                                + "if [ -z \"$P\" ] || [ ! -f $B ]; then echo 'partition හෝ backup නෑ'; "
                                + "else dd if=$B of=$P bs=4096 && sync && echo 'restored. reboot කරන්න'; fi"))));

        panel.addView(label("Boot animation (.qmg)"));
        panel.addView(btn("System .qmg files BACKUP", () -> runSu(
                "mkdir -p " + DIR + "/qmg_backup && cp /system/media/*.qmg " + DIR
                        + "/qmg_backup/ && ls -l " + DIR + "/qmg_backup")));
        panel.addView(btn("Custom .qmg install (Magisk/KSU module)", () ->
                confirm("QMG install කරන්නද?",
                        DIR + "/qmg/ ඇතුළේ තියෙන .qmg files (bootsamsung.qmg, shutdown.qmg වගේ නම්වලින්) "
                                + "module එකකට දාලා systemless replace කරනවා.",
                        () -> runSu(
                                "S=" + DIR + "/qmg; D=/data/adb/modules/allinone_qmg; "
                                        + "ls $S/*.qmg >/dev/null 2>&1 || { echo \"$S ඇතුළේ .qmg නෑ\"; exit 1; }; "
                                        + "mkdir -p $D/system/media && cp $S/*.qmg $D/system/media/ && "
                                        + "printf 'id=allinone_qmg\\nname=AllInOne QMG\\nversion=1\\nversionCode=1\\n"
                                        + "author=Namal\\ndescription=Custom boot animation\\n' > $D/module.prop && "
                                        + "(chcon u:object_r:system_file:s0 $D/system/media/* 2>/dev/null; true) && "
                                        + "ls -l $D/system/media && echo 'reboot කරන්න'"))));
        panel.addView(btn("QMG module අයින් කරන්න", () ->
                runSu("rm -rf /data/adb/modules/allinone_qmg && echo 'removed. reboot කරන්න'")));
    }

    // ---------- Scan (no root needed) ----------

    void tabScan() {
        panel.addView(btn("Phone scan දුවන්න", () -> bg(this::scan)));
    }

    void flag(StringBuilder sb, String m) {
        sb.append("[!] ").append(m).append('\n');
    }

    void ok(StringBuilder sb, String m) {
        sb.append("[OK] ").append(m).append('\n');
    }

    String scan() {
        StringBuilder sb = new StringBuilder("== Phone scan (heuristic) ==\n");

        String[] paths = {"/system/bin/su", "/system/xbin/su", "/sbin/su",
                "/system/app/Superuser.apk", "/data/adb/magisk", "/data/adb/ksu"};
        boolean rootTrace = false;
        for (String p : paths) {
            if (new File(p).exists()) {
                flag(sb, "Root trace: " + p);
                rootTrace = true;
            }
        }
        if (!rootTrace) ok(sb, "su files නෑ (app එකට පේන තැන්වල)");

        try {
            int dev = Settings.Global.getInt(getContentResolver(),
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0);
            int adb = Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0);
            if (dev == 1) flag(sb, "Developer options ON"); else ok(sb, "Developer options OFF");
            if (adb == 1) flag(sb, "USB debugging ON"); else ok(sb, "USB debugging OFF");
        } catch (Exception e) {
            sb.append("settings read fail\n");
        }

        if (android.os.Debug.isDebuggerConnected()) flag(sb, "Debugger connected");
        else ok(sb, "Debugger නෑ");

        String fp = Build.FINGERPRINT == null ? "" : Build.FINGERPRINT.toLowerCase();
        String hw = Build.HARDWARE == null ? "" : Build.HARDWARE.toLowerCase();
        if (fp.contains("generic") || hw.contains("goldfish") || hw.contains("ranchu"))
            flag(sb, "Emulator වගේ");
        else ok(sb, "Real device වගේ");

        PackageManager pm = getPackageManager();
        List<PackageInfo> pk = pm.getInstalledPackages(0);
        String[] bad = {"magisk", "kernelsu", "lsposed", "xposed", "frida", "gameguardian",
                "luckypatcher", "lucky patcher", "virtualxposed", "vphone", "dualspace",
                "parallel space", "modmenu", "mod menu", "injector", "cheat"};
        int hits = 0;
        for (PackageInfo p : pk) {
            String name = p.packageName.toLowerCase();
            String lab = "";
            try {
                lab = String.valueOf(pm.getApplicationLabel(p.applicationInfo)).toLowerCase();
            } catch (Exception e) {
                // ignore
            }
            for (String s : bad) {
                if (name.contains(s) || lab.contains(s)) {
                    flag(sb, "Package: " + p.packageName + "  (" + lab + ")");
                    hits++;
                    break;
                }
            }
        }
        if (hits == 0) ok(sb, "Suspicious package නෑ");

        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", 27042), 300);
            flag(sb, "Frida port 27042 open");
        } catch (Exception e) {
            ok(sb, "Frida port close");
        }

        sb.append("\n(heuristic scan එකක්, false positive තියෙන්න පුළුවන්)\n");

        try {
            File f = new File(getExternalFilesDir(null), "scan_report.txt");
            FileWriter w = new FileWriter(f);
            w.write(sb.toString());
            w.close();
            sb.append("Saved: ").append(f.getAbsolutePath()).append('\n');
        } catch (Exception e) {
            sb.append("Report save fail: ").append(e.getMessage()).append('\n');
        }
        return sb.toString();
    }


    // ---------- module helper ----------

    String modInit(String id, String name) {
        return "D=/data/adb/modules/" + id + "; mkdir -p $D && printf 'id=" + id + "\\nname=" + name
                + "\\nversion=1\\nversionCode=1\\nauthor=Namal\\ndescription=" + name + "\\n' > $D/module.prop";
    }

    // ---------- UI tweaks ----------

    String anim(String v) {
        return "for k in window_animation_scale transition_animation_scale animator_duration_scale; "
                + "do settings put global $k " + v + "; done; echo 'animation = " + v + "'";
    }

    void tabUi() {
        panel.addView(label("DPI"));
        final EditText dpi = edit("DPI (240-600)");
        dpi.setInputType(InputType.TYPE_CLASS_NUMBER);
        panel.addView(dpi);
        panel.addView(btn("දැන් DPI බලන්න", () -> runSu("wm density")));
        panel.addView(btn("DPI set කරන්න", () -> {
            String d = dpi.getText().toString().trim();
            if (!d.matches("[0-9]{3}") || Integer.parseInt(d) < 240 || Integer.parseInt(d) > 600) {
                out.setText("DPI 240-600 අතර ඕනේ");
                return;
            }
            confirm("DPI මාරු කරන්නද?",
                    "UI එක අවුල් වුණොත් Terminal tab එකෙන් 'wm density reset' ගහන්න.",
                    () -> runSu("wm density " + d + "; wm density"));
        }));
        panel.addView(btn("DPI reset", () -> runSu("wm density reset; wm density")));

        panel.addView(label("Animation speed"));
        panel.addView(btn("0.5x (වේගවත්)", () -> runSu(anim("0.5"))));
        panel.addView(btn("1x (normal)", () -> runSu(anim("1"))));
        panel.addView(btn("Off", () -> runSu(anim("0"))));

        panel.addView(label("Status bar (One UI version අනුව වැඩ නොකරන්න පුළුවන්)"));
        panel.addView(btn("Battery % පෙන්වන්න", () ->
                runSu("settings put system status_bar_show_battery_percent 1; echo done")));
        final EditText ic = edit("hide icons (e.g. rotate,headset)");
        panel.addView(ic);
        panel.addView(btn("Icons hide කරන්න", () -> {
            String v = ic.getText().toString().trim();
            if (!v.matches("[a-z_,]+")) {
                out.setText("icon නම් a-z _ , විතරයි");
                return;
            }
            runSu("settings put secure icon_blacklist " + v + "; echo done");
        }));
        panel.addView(btn("Icons reset", () -> runSu("settings delete secure icon_blacklist; echo reset")));
    }

    // ---------- Fonts / Sounds ----------

    void tabMedia() {
        panel.addView(label("Fonts"));
        panel.addView(btn("System fonts list", () -> runSu("ls /system/fonts | head -n 100")));
        panel.addView(btn("Fonts install (module)", () ->
                confirm("Font install කරන්නද?",
                        DIR + "/fonts/ ඇතුළේ තියෙන ttf files, /system/fonts ඇතුළේ තියෙන නමෙන්ම තියෙන්න ඕනේ. "
                                + "වැරදි font එකකින් UI අවුල් වෙන්න පුළුවන්. Module එක අයින් කරන්න පුළුවන් විදිහ කලින් බලාගන්න.",
                        () -> runSu("S=" + DIR + "/fonts; ls $S/* >/dev/null 2>&1 || { echo \"$S ඇතුළේ files නෑ\"; exit 1; }; "
                                + modInit("allinone_fonts", "AllInOne Fonts")
                                + " && mkdir -p $D/system/fonts && cp $S/* $D/system/fonts/ && "
                                + "(chcon -R u:object_r:system_file:s0 $D/system 2>/dev/null; true) && "
                                + "ls -l $D/system/fonts && echo 'reboot කරන්න'"))));
        panel.addView(btn("Fonts module අයින් කරන්න", () ->
                runSu("rm -rf /data/adb/modules/allinone_fonts && echo 'removed. reboot කරන්න'")));

        panel.addView(label("Sounds"));
        panel.addView(btn("System sounds list", () -> runSu("ls -R /system/media/audio | head -n 120")));
        panel.addView(btn("Sounds install (module)", () ->
                confirm("Sounds install කරන්නද?",
                        DIR + "/sounds/ ඇතුළේ ringtones/ notifications/ ui/ alarms/ folders දාන්න, "
                                + "system එකේ තියෙන ඒවගේම file නමින්.",
                        () -> runSu("S=" + DIR + "/sounds; ls $S/* >/dev/null 2>&1 || { echo \"$S ඇතුළේ files නෑ\"; exit 1; }; "
                                + modInit("allinone_sounds", "AllInOne Sounds")
                                + " && mkdir -p $D/system/media/audio && cp -r $S/. $D/system/media/audio/ && "
                                + "(chcon -R u:object_r:system_file:s0 $D/system 2>/dev/null; true) && "
                                + "echo 'installed. reboot කරන්න'"))));
        panel.addView(btn("Sounds module අයින් කරන්න", () ->
                runSu("rm -rf /data/adb/modules/allinone_sounds && echo 'removed. reboot කරන්න'")));
    }

    // ---------- Hidden Samsung features ----------

    void tabHidden() {
        final EditText pat = edit("search tag (හිස් නම් ඔක්කොම)");
        panel.addView(pat);
        panel.addView(btn("floating_feature බලන්න", () -> {
            String t = pat.getText().toString().trim();
            if (t.isEmpty()) runSu("head -n 120 /system/etc/floating_feature.xml");
            else runSu("grep -i -- " + Sh.q(t) + " /system/etc/floating_feature.xml");
        }));

        panel.addView(label("Tag එකක් මාරු කරන්න (module එකෙන්, systemless)"));
        final EditText tag = edit("TAG (e.g. SEC_FLOATING_FEATURE_COMMON_CONFIG_BLUR_SUPPORT)");
        final EditText fv = edit("value (e.g. TRUE)");
        panel.addView(tag);
        panel.addView(fv);
        panel.addView(btn("Apply (module)", () -> {
            String t = tag.getText().toString().trim();
            String v = fv.getText().toString().trim();
            if (!t.matches("[A-Za-z0-9_]+") || !v.matches("[A-Za-z0-9_.,:/ -]*")) {
                out.setText("Tag/value අකුරු අවසර නෑ");
                return;
            }
            confirm("Apply කරන්නද?",
                    "floating_feature.xml එකේ copy එකක් module එකට දාලා ඒක edit කරනවා. Original file එකට අත ගහන්නේ නෑ.",
                    () -> runSu(modInit("allinone_ff", "AllInOne FloatingFeature")
                            + " && mkdir -p $D/system/etc && F=$D/system/etc/floating_feature.xml && "
                            + "{ [ -f $F ] || cp /system/etc/floating_feature.xml $F; } && "
                            + "if grep -q '<" + t + ">' $F; then sed -i 's|<" + t + ">.*</" + t + ">|<" + t + ">" + v + "</" + t + ">|' $F; "
                            + "else sed -i 's|</SecFloatingFeatureSet>|<" + t + ">" + v + "</" + t + "></SecFloatingFeatureSet>|' $F; fi; "
                            + "(chcon u:object_r:system_file:s0 $F 2>/dev/null; true); grep '" + t + "' $F; echo 'reboot කරන්න'"));
        }));
        panel.addView(btn("floating_feature module අයින් කරන්න", () ->
                runSu("rm -rf /data/adb/modules/allinone_ff && echo 'removed. reboot කරන්න'")));
    }

    // ---------- Modules ----------

    void tabModules() {
        panel.addView(btn("Installed modules", () -> runSu("ls -l /data/adb/modules")));
        panel.addView(btn("AllInOne modules ඔක්කොම අයින් කරන්න", () ->
                confirm("ඔක්කොම අයින් කරන්නද?",
                        "allinone_ වලින් පටන් ගන්න modules (props, qmg, fonts, sounds, ff) delete වෙනවා.",
                        () -> runSu("rm -rf /data/adb/modules/allinone_* && ls /data/adb/modules; echo 'reboot කරන්න'"))));
    }

    // ---------- Terminal ----------

    void tabTerm() {
        final EditText cmd = edit("command");
        cmd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        panel.addView(cmd);
        panel.addView(btn("Run as ROOT", () -> {
            String c = cmd.getText().toString();
            if (!c.trim().isEmpty()) runSu(c);
        }));
        panel.addView(btn("Run (root නැතුව)", () -> {
            String c = cmd.getText().toString();
            if (!c.trim().isEmpty()) bg(() -> Sh.sh(c));
        }));
    }
}
