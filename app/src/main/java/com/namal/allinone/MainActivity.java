package com.namal.allinone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
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
    TextView rootChip;
    int current = -1;
    volatile boolean introPlaying = false;

    static final String DIR = "/sdcard/AllInOne";
    static final String RW = "(mount -o rw,remount /system 2>&1 || mount -o rw,remount / 2>&1)";
    static final String FIND_P =
            "P=$(ls /dev/block/by-name/up_param 2>/dev/null || find /dev/block -name up_param 2>/dev/null | head -n 1); ";

    static final int BG = 0xFF121418;
    static final int CARD = 0xFF1C1F26;
    static final int FIELD = 0xFF252932;
    static final int ACCENT = 0xFF4F8CFF;
    static final int TXT = 0xFFE6E8EC;
    static final int SUB = 0xFFB4B9C4;
    static final int DIM = 0xFF6B7280;

    static final String[] NAMES = {"Info", "build.prop", "Backup", "Network", "Boot / QMG",
            "UI Tweaks", "Fonts / Sounds", "Hidden", "Modules", "Scan", "Terminal"};
    static final String[] ICONS = {"ℹ️", "🛠️", "💾", "🌐", "🚀", "🎨", "🔤", "🔓", "🧩", "🛡️", "⌨️"};
    static final String[] SI = {"Phone සහ root විස්තර", "build.prop edit කරන්න", "Backup සහ restore",
            "Network වේගය", "Boot logo සහ animation", "DPI සහ animation", "Font සහ sound මාරු",
            "Samsung සැඟවූ features", "Modules කළමනාකරණය", "Phone scan", "Command run කරන්න"};
    static final String[] EN = {"Device & root info", "Edit build.prop", "Backup & restore",
            "Network speed", "Boot logo & animation", "DPI & animations", "Change fonts & sounds",
            "Hidden Samsung features", "Manage modules", "Scan the phone", "Run commands"};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        showHome();
        if (b == null && !muted()) playIntro();
    }

    @Override
    public void onBackPressed() {
        if (current >= 0) showHome();
        else super.onBackPressed();
    }

    // ---------- intro music ----------

    SharedPreferences prefs() {
        return getSharedPreferences("ui", MODE_PRIVATE);
    }

    boolean muted() {
        return prefs().getBoolean("mute", false);
    }

    void playIntro() {
        if (introPlaying) return;
        // Own song: put intro.mp3 in app/src/main/res/raw/ and it plays instead.
        int rid = getResources().getIdentifier("intro", "raw", getPackageName());
        if (rid != 0) {
            try {
                introPlaying = true;
                MediaPlayer mp = MediaPlayer.create(this, rid);
                if (mp != null) {
                    mp.setOnCompletionListener(m -> {
                        m.release();
                        introPlaying = false;
                    });
                    mp.start();
                    return;
                }
            } catch (Exception e) {
                // fall through to built-in tune
            }
            introPlaying = false;
        }
        introPlaying = true;
        new Thread(this::synthIntro).start();
    }

    void synthIntro() {
        AudioTrack at = null;
        try {
            final int sr = 22050;
            double[] f = {523.25, 659.25, 783.99, 880.00, 783.99, 659.25, 587.33,
                    659.25, 783.99, 1046.50, 880.00, 783.99, 1046.50};
            double[] d = {0.28, 0.28, 0.28, 0.45, 0.28, 0.28, 0.28,
                    0.28, 0.28, 0.55, 0.28, 0.28, 1.2};
            double total = 0;
            for (double x : d) total += x;
            double tail = 1.5;
            int n = (int) ((total + tail) * sr);
            double[] mix = new double[n];
            double start = 0;
            for (int i = 0; i < f.length; i++) {
                int s0 = (int) (start * sr);
                int len = (int) ((d[i] + 1.2) * sr);
                for (int k = 0; k < len && s0 + k < n; k++) {
                    double t = (double) k / sr;
                    double env = Math.exp(-3.2 * t) * Math.min(1.0, t / 0.008);
                    double w = 2 * Math.PI * f[i] * t;
                    mix[s0 + k] += 0.22 * env * (Math.sin(w) + 0.3 * Math.sin(2 * w) + 0.1 * Math.sin(3 * w));
                }
                start += d[i];
            }
            for (int k = 0; k < n; k++) {
                double t = (double) k / sr;
                double life = Math.min(1.0, t / 0.8) * Math.min(1.0, (n - k) / (sr * 0.8));
                mix[k] += 0.07 * life * (Math.sin(2 * Math.PI * 130.81 * t)
                        + Math.sin(2 * Math.PI * 196.0 * t) + Math.sin(2 * Math.PI * 261.63 * t));
            }
            short[] pcm = new short[n];
            for (int k = 0; k < n; k++) {
                double v = Math.max(-1.0, Math.min(1.0, mix[k]));
                pcm[k] = (short) (v * 32000);
            }
            at = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sr)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(n * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            at.write(pcm, 0, n);
            at.play();
            Thread.sleep((long) ((total + tail) * 1000) + 200);
        } catch (Exception e) {
            // ignore audio errors
        } finally {
            if (at != null) {
                try {
                    at.stop();
                } catch (Exception e) {
                    // ignore
                }
                at.release();
            }
            introPlaying = false;
        }
    }

    // ---------- screens ----------

    void showHome() {
        current = -1;
        LinearLayout root = column();
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(36), dp(16), dp(8));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = column();
        titles.addView(text("AllInOne", 28, TXT, true));
        titles.addView(text("Root phone toolkit  •  Root මෙවලම් එකතුව", 12, SUB, false));
        head.addView(titles, lp(0, -2, 1f, 0, 0, 0, 0));
        final TextView spk = text(muted() ? "🔇" : "🔊", 24, TXT, false);
        spk.setPadding(dp(10), dp(6), dp(6), dp(6));
        spk.setOnClickListener(v -> {
            boolean m = !muted();
            prefs().edit().putBoolean("mute", m).apply();
            spk.setText(m ? "🔇" : "🔊");
            if (!m) playIntro();
        });
        head.addView(spk);
        root.addView(head);

        rootChip = text("⏳ Root check කරනවා...", 12, SUB, false);
        rootChip.setBackground(rr(CARD, 20));
        rootChip.setPadding(dp(12), dp(6), dp(12), dp(6));
        root.addView(rootChip, lp(-2, -2, 0f, 0, dp(12), 0, dp(12)));

        ScrollView sv = new ScrollView(this);
        LinearLayout list = column();
        for (int i = 0; i < NAMES.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(card(i), lp(0, -1, 1f, 0, dp(6), dp(6), dp(6)));
            if (i + 1 < NAMES.length) row.addView(card(i + 1), lp(0, -1, 1f, dp(6), dp(6), 0, dp(6)));
            else row.addView(new View(this), lp(0, 1, 1f, dp(6), 0, 0, 0));
            list.addView(row);
        }
        sv.addView(list);
        root.addView(sv, lp(-1, 0, 1f, 0, 0, 0, 0));
        setContentView(root);

        new Thread(() -> {
            final boolean r = Sh.hasRoot();
            runOnUiThread(() -> {
                if (rootChip == null) return;
                rootChip.setText(r ? "🟢 Root OK" : "🔴 Root නෑ (su හොයාගන්න බෑ)");
                rootChip.setTextColor(r ? 0xFF4ADE80 : 0xFFFF6B6B);
            });
        }).start();
    }

    LinearLayout card(int i) {
        LinearLayout c = column();
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        c.setMinimumHeight(dp(120));
        c.setBackground(ripple(CARD, 20));
        c.setClickable(true);
        c.addView(text(ICONS[i], 28, TXT, false));
        c.addView(text(NAMES[i], 15, TXT, true), lp(-2, -2, 0f, 0, dp(8), 0, 0));
        c.addView(text(SI[i], 12, SUB, false), lp(-2, -2, 0f, 0, dp(2), 0, 0));
        c.addView(text(EN[i], 10, DIM, false));
        c.setOnClickListener(v -> showTab(i));
        return c;
    }

    void showTab(int i) {
        current = i;
        LinearLayout root = column();
        root.setBackgroundColor(BG);
        root.setPadding(dp(14), dp(36), dp(14), dp(8));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("←", 24, TXT, true);
        back.setPadding(dp(4), dp(4), dp(16), dp(4));
        back.setOnClickListener(v -> showHome());
        top.addView(back);
        LinearLayout tt = column();
        tt.addView(text(ICONS[i] + "  " + NAMES[i], 20, TXT, true));
        tt.addView(text(SI[i] + "  •  " + EN[i], 11, DIM, false));
        top.addView(tt);
        root.addView(top, lp(-1, -2, 0f, 0, 0, 0, dp(8)));

        ScrollView ps = new ScrollView(this);
        panel = column();
        ps.addView(panel);
        root.addView(ps, lp(-1, 0, 1.2f, 0, 0, 0, 0));

        root.addView(text("OUTPUT  •  ප්‍රතිඵලය", 11, ACCENT, true), lp(-1, -2, 0f, dp(4), dp(8), 0, dp(4)));
        out = text("Ready.", 11, 0xFFCDE7D8, false);
        out.setTypeface(Typeface.MONOSPACE);
        out.setTextIsSelectable(true);
        out.setPadding(dp(12), dp(10), dp(12), dp(10));
        ScrollView os = new ScrollView(this);
        os.setBackground(rr(FIELD, 14));
        os.addView(out);
        root.addView(os, lp(-1, 0, 0.7f, 0, 0, 0, 0));

        setContentView(root);
        openTab(i);
    }

    // ---------- ui helpers ----------

    int dp(int x) {
        return (int) (x * getResources().getDisplayMetrics().density);
    }

    LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout.LayoutParams lp(int w, int h, float wt, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, wt);
        p.setMargins(l, t, r, b);
        return p;
    }

    GradientDrawable rr(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    Drawable ripple(int color, int radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), rr(color, radiusDp), null);
    }

    TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    // action card: English title, Sinhala line, English line
    void act(String title, String si, String en, Runnable r) {
        LinearLayout c = column();
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(ripple(CARD, 14));
        c.setClickable(true);
        c.addView(text(title, 15, TXT, true));
        c.addView(text(si, 12, SUB, false), lp(-1, -2, 0f, 0, dp(4), 0, 0));
        c.addView(text(en, 11, DIM, false), lp(-1, -2, 0f, 0, dp(2), 0, 0));
        c.setOnClickListener(v -> r.run());
        panel.addView(c, lp(-1, -2, 0f, 0, dp(5), 0, dp(5)));
    }

    EditText edit(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(DIM);
        e.setTextColor(TXT);
        e.setTextSize(14);
        e.setBackground(rr(FIELD, 12));
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.addView(e, lp(-1, -2, 0f, 0, dp(5), 0, dp(5)));
        return e;
    }

    void label(String t) {
        panel.addView(text(t, 12, ACCENT, true), lp(-1, -2, 0f, dp(4), dp(16), 0, dp(4)));
    }

    void confirm(String title, String msg, Runnable r) {
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
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
        label("CHECK  •  පරීක්ෂා කරන්න");
        act("Root Check", "Root access තියෙනවද කියලා පරීක්ෂා කරනවා.",
                "Checks whether root access is available.",
                () -> bg(() -> "su path: " + Sh.findSu() + "\n" + Sh.su("id")));
        act("Device Info", "Phone එකේ model, Android, kernel, battery, RAM විස්තර පෙන්නනවා.",
                "Shows model, Android, kernel, battery and RAM details.",
                () -> bg(this::deviceInfo));
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
        label("FIND  •  සොයන්න");
        final EditText search = edit("search text (හිස් නම් පළමු පේළි 80)");
        act("Search build.prop", "build.prop එකේ ඕනේ දෙයක් හොයනවා. හිස් නම් පළමු පේළි 80 පෙන්නනවා.",
                "Searches build.prop. Empty search shows the first 80 lines.", () -> {
                    String s = search.getText().toString().trim();
                    if (s.isEmpty()) runSu("head -n 80 /system/build.prop");
                    else runSu("grep -i -- " + Sh.q(s) + " /system/build.prop");
                });

        label("EDIT  •  වෙනස් කරන්න");
        final EditText key = edit("key  (e.g. ro.sf.lcd_density)");
        final EditText val = edit("value");

        act("Temporary setprop", "Value එක තාවකාලිකව මාරු කරනවා. Reboot කළාම පරණ විදිහටම එනවා.",
                "Changes the value only until the next reboot.", () -> {
                    String k = key.getText().toString().trim();
                    String v = val.getText().toString().trim();
                    if (!validKV(k, v)) return;
                    runSu("setprop " + k + " " + Sh.q(v) + "; getprop " + k);
                });

        act("Save as Module (Safe)", "Magisk/KSU module එකක් විදිහට save කරනවා. අවුලක් වුණොත් ලේසියෙන් අයින් කරන්න පුළුවන්.",
                "Saves the value as a removable Magisk/KSU module.", () -> {
                    String k = key.getText().toString().trim();
                    String v = val.getText().toString().trim();
                    if (!validKV(k, v)) return;
                    runSu(propModuleCmd(k, v));
                });

        act("Write to build.prop", "/system/build.prop එකටම ලියනවා. Backup එකක් ඉබේම ගන්නවා. අවදානම් වැඩක්.",
                "Writes directly to /system/build.prop with an auto backup. Risky.", () -> {
                    String k = key.getText().toString().trim();
                    String v = val.getText().toString().trim();
                    if (!validKV(k, v)) return;
                    confirm("build.prop ලියන්නද?",
                            "Backup එකක් auto ගන්නවා. /system read-only නම් fail වෙනවා. "
                                    + "වැරදි value එකකින් bootloop වෙන්න පුළුවන්.",
                            () -> runSu(writeCmd(k, v)));
                });

        label("CLEANUP  •  අයින් කරන්න");
        act("Remove Props Module", "උඩින් හදපු props module එක අයින් කරනවා.",
                "Deletes the props module created above.", () ->
                        runSu("rm -rf /data/adb/modules/allinone_props && echo 'removed. reboot කරන්න'"));
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
        label("BACKUP  •  සුරකින්න");
        act("Backup build.prop", "build.prop එකේ copy එකක් /sdcard/AllInOne එකට ගන්නවා.",
                "Copies build.prop to /sdcard/AllInOne.", () ->
                        runSu("mkdir -p " + DIR + " && cp /system/build.prop " + DIR
                                + "/build.prop.$(date +%s) && ls -l " + DIR));
        act("List Backups", "ගත්ත backups ඔක්කොම ලිස්ට් කරලා පෙන්නනවා.",
                "Lists all saved backups.", () -> runSu("ls -l " + DIR));

        label("RESTORE  •  ආපහු දාන්න");
        act("Restore Latest", "අන්තිම build.prop backup එක ආපහු /system එකට දානවා.",
                "Restores the newest build.prop backup.", () ->
                        confirm("Restore කරන්නද?",
                                "අන්තිම build.prop backup එක /system එකට copy කරනවා.",
                                () -> runSu(restoreCmd())));
    }

    String restoreCmd() {
        return "F=$(ls -t " + DIR + "/build.prop.* 2>/dev/null | head -n 1); "
                + "if [ -z \"$F\" ]; then echo 'backup නෑ'; else " + RW + "; "
                + "cp \"$F\" /system/build.prop && echo \"restored: $F\" || echo 'restore fail'; fi";
    }

    // ---------- Network ----------

    void tabNet() {
        label("INFO  •  විස්තර");
        act("TCP Info", "දැන් පාවිච්චි වෙන TCP algorithm එකයි තියෙන ඒවයි පෙන්නනවා.",
                "Shows the current and available TCP congestion control.", () -> runSu(
                        "for f in tcp_congestion_control tcp_available_congestion_control "
                                + "tcp_allowed_congestion_control; do echo $f: $(cat /proc/sys/net/ipv4/$f); done"));
        act("Ping Test", "Internet connection එක ping කරලා බලනවා (8.8.8.8).",
                "Pings 8.8.8.8 to test connectivity.", () -> bg(() -> Sh.sh("ping -c 4 8.8.8.8")));

        label("TUNE  •  වැඩි දියුණු කරන්න");
        act("Enable BBR", "Kernel එකේ තියෙනවා නම් BBR set කරනවා. Reboot කළාම reset වෙනවා.",
                "Switches to BBR if the kernel supports it. Resets on reboot.", () -> runSu(
                        "if grep -qw bbr /proc/sys/net/ipv4/tcp_available_congestion_control; then "
                                + "echo bbr > /proc/sys/net/ipv4/tcp_congestion_control; echo 'BBR set'; "
                                + "else echo 'මේ kernel එකේ BBR නෑ'; fi; "
                                + "cat /proc/sys/net/ipv4/tcp_congestion_control"));
    }

    // ---------- Boot / QMG ----------

    void tabBoot() {
        label("BOOT LOGO (up_param)  •  boot logo එක");
        act("Backup up_param", "up_param partition එකේ backup එකක් /sdcard/AllInOne එකට ගන්නවා. මුලින්ම මේක කරන්න.",
                "Saves the up_param partition to /sdcard/AllInOne. Do this first.", () -> runSu(
                        FIND_P + "if [ -z \"$P\" ]; then echo 'up_param partition නෑ'; else "
                                + "mkdir -p " + DIR + " && dd if=$P of=" + DIR + "/up_param.backup.img bs=4096 && "
                                + "ls -l " + DIR + "/up_param.backup.img; fi"));
        act("Flash Custom up_param", "up_param_custom.img එක partition එකට දානවා. Backup එක මුලින් ගත්තද බලන්න.",
                "Flashes /sdcard/AllInOne/up_param_custom.img to the partition. Needs a backup first.", () ->
                        confirm("up_param flash කරන්නද?",
                                DIR + "/up_param_custom.img එක partition එකට dd කරනවා. "
                                        + "මුලින් BACKUP එක ගන්න. වැරදි image එකකින් boot logo අවුල් වෙන්න පුළුවන්.",
                                () -> runSu(FIND_P
                                        + "C=" + DIR + "/up_param_custom.img; B=" + DIR + "/up_param.backup.img; "
                                        + "if [ -z \"$P\" ]; then echo 'up_param නෑ'; "
                                        + "elif [ ! -f $B ]; then echo 'මුලින් backup ගන්න'; "
                                        + "elif [ ! -f $C ]; then echo \"$C නෑ\"; "
                                        + "elif [ $(stat -c %s $C) -gt $(blockdev --getsize64 $P) ]; then echo 'image ලොකු වැඩියි'; "
                                        + "else dd if=$C of=$P bs=4096 && sync && echo 'flashed. reboot කරන්න'; fi")));
        act("Restore up_param", "ගත්ත backup එකෙන් up_param ආපහු දානවා.",
                "Writes the saved backup back to the up_param partition.", () ->
                        confirm("Restore කරන්නද?", "Backup image එක partition එකට dd කරනවා.",
                                () -> runSu(FIND_P
                                        + "B=" + DIR + "/up_param.backup.img; "
                                        + "if [ -z \"$P\" ] || [ ! -f $B ]; then echo 'partition හෝ backup නෑ'; "
                                        + "else dd if=$B of=$P bs=4096 && sync && echo 'restored. reboot කරන්න'; fi")));

        label("BOOT ANIMATION (QMG)  •  boot animation එක");
        act("Backup QMG Files", "System එකේ .qmg files /sdcard/AllInOne/qmg_backup එකට copy කරනවා.",
                "Copies the system .qmg files to /sdcard/AllInOne/qmg_backup.", () -> runSu(
                        "mkdir -p " + DIR + "/qmg_backup && cp /system/media/*.qmg " + DIR
                                + "/qmg_backup/ && ls -l " + DIR + "/qmg_backup"));
        act("Install Custom QMG", "/sdcard/AllInOne/qmg එකේ .qmg files module එකක් විදිහට systemless දානවා.",
                "Installs .qmg files from /sdcard/AllInOne/qmg as a systemless module.", () ->
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
                                                + "ls -l $D/system/media && echo 'reboot කරන්න'")));
        act("Remove QMG Module", "Install කරපු custom boot animation module එක අයින් කරනවා.",
                "Deletes the custom boot animation module.", () ->
                        runSu("rm -rf /data/adb/modules/allinone_qmg && echo 'removed. reboot කරන්න'"));
    }

    // ---------- UI tweaks ----------

    String anim(String v) {
        return "for k in window_animation_scale transition_animation_scale animator_duration_scale; "
                + "do settings put global $k " + v + "; done; echo 'animation = " + v + "'";
    }

    void tabUi() {
        label("DPI  •  screen size");
        final EditText dpi = edit("DPI  (240 - 600)");
        dpi.setInputType(InputType.TYPE_CLASS_NUMBER);
        act("Show DPI", "දැන් තියෙන DPI අගය පෙන්නනවා.", "Shows the current screen density.",
                () -> runSu("wm density"));
        act("Set DPI", "උඩ දාපු DPI අගයට screen එක මාරු කරනවා.",
                "Applies the DPI value you typed above.", () -> {
                    String d = dpi.getText().toString().trim();
                    if (!d.matches("[0-9]{3}") || Integer.parseInt(d) < 240 || Integer.parseInt(d) > 600) {
                        out.setText("DPI 240-600 අතර ඕනේ");
                        return;
                    }
                    confirm("DPI මාරු කරන්නද?",
                            "UI එක අවුල් වුණොත් Terminal එකෙන් 'wm density reset' ගහන්න.",
                            () -> runSu("wm density " + d + "; wm density"));
                });
        act("Reset DPI", "DPI එක original අගයට හරවනවා.", "Restores the default density.",
                () -> runSu("wm density reset; wm density"));

        label("ANIMATION  •  animation වේගය");
        act("Fast (0.5x)", "Animation දෙගුණයක් වේගවත් කරනවා.", "Doubles the animation speed.",
                () -> runSu(anim("0.5")));
        act("Normal (1x)", "Animation සාමාන්‍ය වේගයට හරවනවා.", "Restores normal animation speed.",
                () -> runSu(anim("1")));
        act("Off", "Animation සම්පූර්ණයෙන්ම නවත්තනවා.", "Turns animations off completely.",
                () -> runSu(anim("0")));

        label("STATUS BAR  •  status bar එක");
        act("Show Battery %", "Battery percentage එක status bar එකේ පෙන්නනවා. (One UI අනුව වැඩ නොකරන්නත් පුළුවන්)",
                "Shows battery percentage in the status bar. May not work on every One UI.",
                () -> runSu("settings put system status_bar_show_battery_percent 1; echo done"));
        final EditText ic = edit("icons  (e.g. rotate,headset)");
        act("Hide Icons", "උඩ ලියපු status bar icons හංගනවා.", "Hides the status bar icons you typed above.", () -> {
            String v = ic.getText().toString().trim();
            if (!v.matches("[a-z_,]+")) {
                out.setText("icon නම් a-z _ , විතරයි");
                return;
            }
            runSu("settings put secure icon_blacklist " + v + "; echo done");
        });
        act("Reset Icons", "හංගපු icons ආපහු පෙන්නනවා.", "Shows all hidden icons again.",
                () -> runSu("settings delete secure icon_blacklist; echo reset"));
    }

    // ---------- Fonts / Sounds ----------

    void tabMedia() {
        label("FONTS  •  අකුරු");
        act("List Fonts", "System එකේ තියෙන font files ලිස්ට් කරනවා. ඔයාගේ font එකට මේ නම්ම දෙන්න.",
                "Lists the font files in /system/fonts. Name your font files the same.",
                () -> runSu("ls /system/fonts | head -n 100"));
        act("Install Fonts", "/sdcard/AllInOne/fonts එකේ ttf files module එකක් විදිහට දානවා.",
                "Installs fonts from /sdcard/AllInOne/fonts as a module.", () ->
                        confirm("Font install කරන්නද?",
                                DIR + "/fonts/ ඇතුළේ තියෙන ttf files, /system/fonts ඇතුළේ තියෙන නමෙන්ම තියෙන්න ඕනේ. "
                                        + "වැරදි font එකකින් UI අවුල් වෙන්න පුළුවන්.",
                                () -> runSu("S=" + DIR + "/fonts; ls $S/* >/dev/null 2>&1 || { echo \"$S ඇතුළේ files නෑ\"; exit 1; }; "
                                        + modInit("allinone_fonts", "AllInOne Fonts")
                                        + " && mkdir -p $D/system/fonts && cp $S/* $D/system/fonts/ && "
                                        + "(chcon -R u:object_r:system_file:s0 $D/system 2>/dev/null; true) && "
                                        + "ls -l $D/system/fonts && echo 'reboot කරන්න'")));
        act("Remove Fonts Module", "Install කරපු font module එක අයින් කරනවා.",
                "Deletes the installed fonts module.", () ->
                        runSu("rm -rf /data/adb/modules/allinone_fonts && echo 'removed. reboot කරන්න'"));

        label("SOUNDS  •  ශබ්ද");
        act("List Sounds", "System එකේ තියෙන sound files ලිස්ට් කරනවා.",
                "Lists the system sound files.", () -> runSu("ls -R /system/media/audio | head -n 120"));
        act("Install Sounds", "/sdcard/AllInOne/sounds එකේ files module එකක් විදිහට දානවා.",
                "Installs sounds from /sdcard/AllInOne/sounds as a module.", () ->
                        confirm("Sounds install කරන්නද?",
                                DIR + "/sounds/ ඇතුළේ ringtones/ notifications/ ui/ alarms/ folders දාන්න, "
                                        + "system එකේ තියෙන ඒවගේම file නමින්.",
                                () -> runSu("S=" + DIR + "/sounds; ls $S/* >/dev/null 2>&1 || { echo \"$S ඇතුළේ files නෑ\"; exit 1; }; "
                                        + modInit("allinone_sounds", "AllInOne Sounds")
                                        + " && mkdir -p $D/system/media/audio && cp -r $S/. $D/system/media/audio/ && "
                                        + "(chcon -R u:object_r:system_file:s0 $D/system 2>/dev/null; true) && "
                                        + "echo 'installed. reboot කරන්න'")));
        act("Remove Sounds Module", "Install කරපු sounds module එක අයින් කරනවා.",
                "Deletes the installed sounds module.", () ->
                        runSu("rm -rf /data/adb/modules/allinone_sounds && echo 'removed. reboot කරන්න'"));
    }

    String modInit(String id, String name) {
        return "D=/data/adb/modules/" + id + "; mkdir -p $D && printf 'id=" + id + "\\nname=" + name
                + "\\nversion=1\\nversionCode=1\\nauthor=Namal\\ndescription=" + name + "\\n' > $D/module.prop";
    }

    // ---------- Hidden Samsung features ----------

    void tabHidden() {
        label("VIEW  •  බලන්න");
        final EditText pat = edit("search tag  (හිස් නම් ඔක්කොම)");
        act("View floating_feature", "Samsung floating_feature.xml එකේ tags පෙන්නනවා.",
                "Shows tags in Samsung's floating_feature.xml.", () -> {
                    String t = pat.getText().toString().trim();
                    if (t.isEmpty()) runSu("head -n 120 /system/etc/floating_feature.xml");
                    else runSu("grep -i -- " + Sh.q(t) + " /system/etc/floating_feature.xml");
                });

        label("CHANGE  •  වෙනස් කරන්න");
        final EditText tag = edit("TAG  (e.g. SEC_FLOATING_FEATURE_COMMON_CONFIG_BLUR_SUPPORT)");
        final EditText fv = edit("value  (e.g. TRUE)");
        act("Apply Feature (Module)", "Tag එකේ අගය module එකකින් (systemless) මාරු කරනවා. Original file එකට අත ගහන්නේ නෑ.",
                "Changes the tag via a systemless module. The original file is untouched.", () -> {
                    String t = tag.getText().toString().trim();
                    String v = fv.getText().toString().trim();
                    if (!t.matches("[A-Za-z0-9_]+") || !v.matches("[A-Za-z0-9_.,:/ -]*")) {
                        out.setText("Tag/value අකුරු අවසර නෑ");
                        return;
                    }
                    confirm("Apply කරන්නද?",
                            "floating_feature.xml එකේ copy එකක් module එකට දාලා ඒක edit කරනවා.",
                            () -> runSu(modInit("allinone_ff", "AllInOne FloatingFeature")
                                    + " && mkdir -p $D/system/etc && F=$D/system/etc/floating_feature.xml && "
                                    + "{ [ -f $F ] || cp /system/etc/floating_feature.xml $F; } && "
                                    + "if grep -q '<" + t + ">' $F; then sed -i 's|<" + t + ">.*</" + t + ">|<" + t + ">" + v + "</" + t + ">|' $F; "
                                    + "else sed -i 's|</SecFloatingFeatureSet>|<" + t + ">" + v + "</" + t + "></SecFloatingFeatureSet>|' $F; fi; "
                                    + "(chcon u:object_r:system_file:s0 $F 2>/dev/null; true); grep '" + t + "' $F; echo 'reboot කරන්න'"));
                });

        label("CLEANUP  •  අයින් කරන්න");
        act("Remove Feature Module", "Feature module එක අයින් කරලා original එකට හරවනවා.",
                "Deletes the feature module and restores the original.", () ->
                        runSu("rm -rf /data/adb/modules/allinone_ff && echo 'removed. reboot කරන්න'"));
    }

    // ---------- Modules ----------

    void tabModules() {
        label("MANAGE  •  කළමනාකරණය");
        act("List Modules", "Install වෙලා තියෙන Magisk/KSU modules ලිස්ට් කරනවා.",
                "Lists the installed Magisk/KSU modules.", () -> runSu("ls -l /data/adb/modules"));
        act("Remove All AllInOne Modules", "AllInOne හදපු modules (props, qmg, fonts, sounds, ff) ඔක්කොම අයින් කරනවා.",
                "Deletes every module created by this app.", () ->
                        confirm("ඔක්කොම අයින් කරන්නද?",
                                "allinone_ වලින් පටන් ගන්න modules (props, qmg, fonts, sounds, ff) delete වෙනවා.",
                                () -> runSu("rm -rf /data/adb/modules/allinone_* && ls /data/adb/modules; echo 'reboot කරන්න'")));
    }

    // ---------- Scan (no root needed) ----------

    void tabScan() {
        label("SCAN  •  පරීක්ෂාව");
        act("Run Phone Scan", "Root, debug, suspicious apps, Frida වගේ දේවල් බලලා report එකක් හදනවා. Root ඕනේ නෑ.",
                "Checks root, debugging, suspicious apps and Frida, then saves a report. No root needed.",
                () -> bg(this::scan));
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

    // ---------- Terminal ----------

    void tabTerm() {
        label("COMMAND  •  command එක");
        final EditText cmd = edit("type a command");
        cmd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        act("Run as Root", "උඩ ලියපු command එක root එකෙන් run කරනවා.",
                "Runs the command above with root.", () -> {
                    String c = cmd.getText().toString();
                    if (!c.trim().isEmpty()) runSu(c);
                });
        act("Run (No Root)", "Command එක root නැතුව run කරනවා.",
                "Runs the command without root.", () -> {
                    String c = cmd.getText().toString();
                    if (!c.trim().isEmpty()) bg(() -> Sh.sh(c));
                });
    }
}
