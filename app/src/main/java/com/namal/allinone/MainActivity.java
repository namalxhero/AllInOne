package com.namal.allinone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
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
import android.widget.FrameLayout;
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

    FrameLayout frame;
    SceneView scene;
    LinearLayout panel;
    TextView out;
    TextView rootChip;
    int current = -1;

    AudioTrack track;
    MediaPlayer mp;
    volatile boolean loading = false;
    boolean visible = false;

    static final String DIR = "/sdcard/AllInOne";
    static final String RW = "(mount -o rw,remount /system 2>&1 || mount -o rw,remount / 2>&1)";
    static final String FIND_P =
            "P=$(ls /dev/block/by-name/up_param 2>/dev/null || find /dev/block -name up_param 2>/dev/null | head -n 1); ";

    static final int TXT = 0xFFFFFFFF;
    static final int SUB = 0xFFEDEAF7;
    static final int DIM = 0xFFC9C5DC;
    static final int ACCENT = 0xFFFFC2E2;

    static final String[] NAMES = {"Info", "build.prop", "Backup", "Network", "Boot / QMG",
            "UI Tweaks", "Fonts / Sounds", "Hidden", "Modules", "Scan", "Terminal"};
    static final String[] ICONS = {"ℹ️", "🛠️", "💾", "🌐", "🚀", "🎨", "🔤", "🔓", "🧩", "🛡️", "⌨️"};
    static final String[] DESC = {"Device & root info", "Edit system properties", "Backup & restore",
            "Speed & TCP tuning", "Boot logo & animation", "DPI & animations", "Custom fonts & sounds",
            "Hidden Samsung features", "Manage modules", "Scan the phone", "Run commands"};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        frame = new FrameLayout(this);
        scene = new SceneView(this);
        frame.addView(scene, new FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);
        showHome();
    }

    @Override
    protected void onStart() {
        super.onStart();
        visible = true;
        musicStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        visible = false;
        musicPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (track != null) {
                track.stop();
                track.release();
                track = null;
            }
            if (mp != null) {
                mp.release();
                mp = null;
            }
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void onBackPressed() {
        if (current >= 0) showHome();
        else super.onBackPressed();
    }

    // ---------- music (original phonk loop) ----------

    SharedPreferences prefs() {
        return getSharedPreferences("ui", MODE_PRIVATE);
    }

    boolean muted() {
        return prefs().getBoolean("mute", false);
    }

    void musicStart() {
        if (muted() || !visible) return;
        try {
            if (mp != null) {
                mp.start();
                return;
            }
            if (track != null) {
                track.play();
                return;
            }
            if (loading) return;
            // Your own song: put intro.mp3 in app/src/main/res/raw/ and it plays instead.
            int rid = getResources().getIdentifier("intro", "raw", getPackageName());
            if (rid != 0) {
                mp = MediaPlayer.create(this, rid);
                if (mp != null) {
                    mp.setLooping(true);
                    mp.start();
                    return;
                }
            }
            loading = true;
            new Thread(() -> {
                final short[] pcm = Phonk.make(22050);
                runOnUiThread(() -> {
                    loading = false;
                    if (isDestroyed()) return;
                    try {
                        AudioTrack at = new AudioTrack.Builder()
                                .setAudioAttributes(new AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_MEDIA)
                                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                                .setAudioFormat(new AudioFormat.Builder()
                                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                        .setSampleRate(22050)
                                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                                .setBufferSizeInBytes(pcm.length * 2)
                                .setTransferMode(AudioTrack.MODE_STATIC)
                                .build();
                        at.write(pcm, 0, pcm.length);
                        at.setLoopPoints(0, pcm.length, -1);
                        at.setVolume(0.9f);
                        track = at;
                        if (visible && !muted()) at.play();
                    } catch (Exception e) {
                        // ignore audio errors
                    }
                });
            }).start();
        } catch (Exception e) {
            // ignore audio errors
        }
    }

    void musicPause() {
        try {
            if (mp != null && mp.isPlaying()) mp.pause();
            if (track != null) track.pause();
        } catch (Exception e) {
            // ignore
        }
    }

    // ---------- screens ----------

    void setScreen(View v) {
        if (frame.getChildCount() > 1) frame.removeViewAt(1);
        frame.addView(v, new FrameLayout.LayoutParams(-1, -1));
    }

    void showHome() {
        current = -1;
        LinearLayout root = column();
        root.setPadding(dp(16), dp(40), dp(16), dp(24));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = column();
        titles.addView(text("AllInOne", 32, TXT, true));
        titles.addView(text("✦  Root Toolkit  ✦", 12, ACCENT, true));
        head.addView(titles, lp(0, -2, 1f, 0, 0, 0, 0));
        final TextView spk = text(muted() ? "🔇" : "🔊", 22, TXT, false);
        spk.setGravity(Gravity.CENTER);
        spk.setBackground(circle());
        spk.setOnClickListener(v -> {
            boolean m = !muted();
            prefs().edit().putBoolean("mute", m).apply();
            spk.setText(m ? "🔇" : "🔊");
            if (m) musicPause();
            else musicStart();
        });
        head.addView(spk, lp(dp(46), dp(46), 0f, 0, 0, 0, 0));
        root.addView(head);

        rootChip = text("⏳  Checking root...", 12, SUB, false);
        rootChip.setBackground(glass(20, 0x55101428));
        rootChip.setPadding(dp(14), dp(7), dp(14), dp(7));
        root.addView(rootChip, lp(-2, -2, 0f, 0, dp(14), 0, dp(10)));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
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
        setScreen(root);

        new Thread(() -> {
            final boolean r = Sh.hasRoot();
            runOnUiThread(() -> {
                if (rootChip == null) return;
                rootChip.setText(r ? "🟢  Root OK" : "🔴  No root (su not found)");
                rootChip.setTextColor(r ? 0xFF8CFFB4 : 0xFFFF9C9C);
            });
        }).start();
    }

    LinearLayout card(int i) {
        LinearLayout c = column();
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        c.setMinimumHeight(dp(124));
        c.setBackground(glassRipple(22));
        c.setClickable(true);
        TextView ic = text(ICONS[i], 22, TXT, false);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(circle());
        c.addView(ic, lp(dp(46), dp(46), 0f, 0, 0, 0, 0));
        c.addView(text(NAMES[i], 16, TXT, true), lp(-2, -2, 0f, 0, dp(10), 0, 0));
        c.addView(text(DESC[i], 12, SUB, false), lp(-2, -2, 0f, 0, dp(2), 0, 0));
        c.setOnClickListener(v -> showTab(i));
        return c;
    }

    void showTab(int i) {
        current = i;
        LinearLayout root = column();
        root.setPadding(dp(14), dp(40), dp(14), dp(24));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 28, TXT, true);
        back.setGravity(Gravity.CENTER);
        back.setBackground(circle());
        back.setOnClickListener(v -> showHome());
        top.addView(back, lp(dp(44), dp(44), 0f, 0, 0, dp(12), 0));
        LinearLayout tt = column();
        tt.addView(text(ICONS[i] + "  " + NAMES[i], 22, TXT, true));
        tt.addView(text(DESC[i], 12, SUB, false));
        top.addView(tt);
        root.addView(top, lp(-1, -2, 0f, 0, 0, 0, dp(8)));

        ScrollView ps = new ScrollView(this);
        ps.setVerticalScrollBarEnabled(false);
        panel = column();
        ps.addView(panel);
        root.addView(ps, lp(-1, 0, 1.5f, 0, 0, 0, 0));

        root.addView(text("✦  OUTPUT", 12, ACCENT, true), lp(-1, -2, 0f, dp(4), dp(8), 0, dp(4)));
        out = text("Ready.", 11, 0xFFD8F5E4, false);
        out.setTypeface(Typeface.MONOSPACE);
        out.setTextIsSelectable(true);
        out.setPadding(dp(12), dp(10), dp(12), dp(10));
        ScrollView os = new ScrollView(this);
        os.setBackground(glass(16, 0x88080C1C));
        os.addView(out);
        root.addView(os, lp(-1, 0, 0.6f, 0, 0, 0, 0));

        setScreen(root);
        openTab(i);
    }

    // ---------- glass ui helpers ----------

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

    Drawable glass(int radiusDp, int tint) {
        GradientDrawable base = new GradientDrawable();
        base.setColor(tint);
        base.setCornerRadius(dp(radiusDp));
        GradientDrawable shine = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0x66FFFFFF, 0x12FFFFFF, 0x2EFFFFFF});
        shine.setCornerRadius(dp(radiusDp));
        shine.setStroke(dp(1), 0x88FFFFFF);
        return new LayerDrawable(new Drawable[]{base, shine});
    }

    Drawable glassRipple(int radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), glass(radiusDp, 0x66101428), null);
    }

    Drawable circle() {
        GradientDrawable base = new GradientDrawable();
        base.setShape(GradientDrawable.OVAL);
        base.setColor(0x55101428);
        GradientDrawable shine = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0x77FFFFFF, 0x14FFFFFF});
        shine.setShape(GradientDrawable.OVAL);
        shine.setStroke(dp(1), 0x99FFFFFF);
        return new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF),
                new LayerDrawable(new Drawable[]{base, shine}), null);
    }

    TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setShadowLayer(dp(3), 0, dp(1), 0x99000000);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    // action row: icon, English title, English description
    void act(String icon, String title, String desc, Runnable r) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(glassRipple(18));
        row.setClickable(true);

        TextView ic = text(icon, 20, TXT, false);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(circle());
        row.addView(ic, lp(dp(44), dp(44), 0f, 0, 0, dp(14), 0));

        LinearLayout col = column();
        col.addView(text(title, 15, TXT, true));
        col.addView(text(desc, 12, SUB, false), lp(-1, -2, 0f, 0, dp(2), 0, 0));
        row.addView(col, lp(0, -2, 1f, 0, 0, 0, 0));

        row.setOnClickListener(v -> r.run());
        panel.addView(row, lp(-1, -2, 0f, 0, dp(5), 0, dp(5)));
    }

    EditText edit(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(DIM);
        e.setTextColor(TXT);
        e.setTextSize(14);
        e.setBackground(glass(14, 0x66101428));
        e.setPadding(dp(16), dp(12), dp(16), dp(12));
        panel.addView(e, lp(-1, -2, 0f, 0, dp(5), 0, dp(5)));
        return e;
    }

    void label(String t) {
        panel.addView(text("✦  " + t, 12, ACCENT, true), lp(-1, -2, 0f, dp(4), dp(16), 0, dp(4)));
    }

    void confirm(String title, String msg, Runnable r) {
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("OK", (d, w) -> r.run())
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
        label("CHECK");
        act("🔑", "Root Check", "Checks whether root access is available.",
                () -> bg(() -> "su path: " + Sh.findSu() + "\n" + Sh.su("id")));
        act("📱", "Device Info", "Shows model, Android, kernel, battery and RAM details.",
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
        label("FIND");
        final EditText search = edit("search text (empty = first 80 lines)");
        act("🔎", "Search build.prop", "Searches build.prop. Empty search shows the first 80 lines.", () -> {
            String s = search.getText().toString().trim();
            if (s.isEmpty()) runSu("head -n 80 /system/build.prop");
            else runSu("grep -i -- " + Sh.q(s) + " /system/build.prop");
        });

        label("EDIT");
        final EditText key = edit("key  (e.g. ro.sf.lcd_density)");
        final EditText val = edit("value");

        act("⚡", "Temporary setprop", "Changes the value only until the next reboot.", () -> {
            String k = key.getText().toString().trim();
            String v = val.getText().toString().trim();
            if (!validKV(k, v)) return;
            runSu("setprop " + k + " " + Sh.q(v) + "; getprop " + k);
        });

        act("📦", "Save as Module (Safe)", "Saves the value as a removable Magisk/KSU module.", () -> {
            String k = key.getText().toString().trim();
            String v = val.getText().toString().trim();
            if (!validKV(k, v)) return;
            runSu(propModuleCmd(k, v));
        });

        act("✍️", "Write to build.prop", "Writes directly to /system/build.prop with an auto backup. Risky.", () -> {
            String k = key.getText().toString().trim();
            String v = val.getText().toString().trim();
            if (!validKV(k, v)) return;
            confirm("Write to build.prop?",
                    "An automatic backup is taken first. This fails if /system is read-only, "
                            + "and a wrong value can cause a bootloop.",
                    () -> runSu(writeCmd(k, v)));
        });

        label("CLEANUP");
        act("🗑️", "Remove Props Module", "Deletes the props module created above.", () ->
                runSu("rm -rf /data/adb/modules/allinone_props && echo 'Removed. Reboot to apply.'"));
    }

    boolean validKV(String k, String v) {
        if (!k.matches("[A-Za-z0-9._-]+")) {
            out.setText("Invalid key (use A-Z a-z 0-9 . _ - only)");
            return false;
        }
        if (!v.matches("[A-Za-z0-9._,:+/@ -]*")) {
            out.setText("Value contains characters that are not allowed");
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
                + "echo '--- Reboot to apply'";
    }

    // ---------- Backup ----------

    void tabBackup() {
        label("BACKUP");
        act("💾", "Backup build.prop", "Copies build.prop to /sdcard/AllInOne.", () ->
                runSu("mkdir -p " + DIR + " && cp /system/build.prop " + DIR
                        + "/build.prop.$(date +%s) && ls -l " + DIR));
        act("📋", "List Backups", "Lists all saved backups.", () -> runSu("ls -l " + DIR));

        label("RESTORE");
        act("♻️", "Restore Latest", "Restores the newest build.prop backup.", () ->
                confirm("Restore latest backup?",
                        "The newest build.prop backup will be copied back to /system.",
                        () -> runSu(restoreCmd())));
    }

    String restoreCmd() {
        return "F=$(ls -t " + DIR + "/build.prop.* 2>/dev/null | head -n 1); "
                + "if [ -z \"$F\" ]; then echo 'No backup found'; else " + RW + "; "
                + "cp \"$F\" /system/build.prop && echo \"Restored: $F\" || echo 'Restore failed'; fi";
    }

    // ---------- Network ----------

    void tabNet() {
        label("INFO");
        act("📡", "TCP Info", "Shows the current and available TCP congestion control.", () -> runSu(
                "for f in tcp_congestion_control tcp_available_congestion_control "
                        + "tcp_allowed_congestion_control; do echo $f: $(cat /proc/sys/net/ipv4/$f); done"));
        act("🏓", "Ping Test", "Pings 8.8.8.8 to test connectivity.", () -> bg(() -> Sh.sh("ping -c 4 8.8.8.8")));

        label("TUNE");
        act("🚄", "Enable BBR", "Switches to BBR if the kernel supports it. Resets on reboot.", () -> runSu(
                "if grep -qw bbr /proc/sys/net/ipv4/tcp_available_congestion_control; then "
                        + "echo bbr > /proc/sys/net/ipv4/tcp_congestion_control; echo 'BBR enabled'; "
                        + "else echo 'BBR is not available in this kernel'; fi; "
                        + "cat /proc/sys/net/ipv4/tcp_congestion_control"));
    }

    // ---------- Boot / QMG ----------

    void tabBoot() {
        label("BOOT LOGO (up_param)");
        act("💾", "Backup up_param", "Saves the up_param partition to /sdcard/AllInOne. Do this first.", () -> runSu(
                FIND_P + "if [ -z \"$P\" ]; then echo 'up_param partition not found'; else "
                        + "mkdir -p " + DIR + " && dd if=$P of=" + DIR + "/up_param.backup.img bs=4096 && "
                        + "ls -l " + DIR + "/up_param.backup.img; fi"));
        act("🔥", "Flash Custom up_param", "Flashes up_param_custom.img to the partition. Needs a backup first.", () ->
                confirm("Flash up_param?",
                        DIR + "/up_param_custom.img will be written to the partition. "
                                + "Take a BACKUP first. A wrong image can break the boot logo.",
                        () -> runSu(FIND_P
                                + "C=" + DIR + "/up_param_custom.img; B=" + DIR + "/up_param.backup.img; "
                                + "if [ -z \"$P\" ]; then echo 'up_param not found'; "
                                + "elif [ ! -f $B ]; then echo 'Take a backup first'; "
                                + "elif [ ! -f $C ]; then echo \"Missing file: $C\"; "
                                + "elif [ $(stat -c %s $C) -gt $(blockdev --getsize64 $P) ]; then echo 'Image is too large'; "
                                + "else dd if=$C of=$P bs=4096 && sync && echo 'Flashed. Reboot to apply.'; fi")));
        act("♻️", "Restore up_param", "Writes the saved backup back to the up_param partition.", () ->
                confirm("Restore up_param?", "The backup image will be written back to the partition.",
                        () -> runSu(FIND_P
                                + "B=" + DIR + "/up_param.backup.img; "
                                + "if [ -z \"$P\" ] || [ ! -f $B ]; then echo 'Partition or backup not found'; "
                                + "else dd if=$B of=$P bs=4096 && sync && echo 'Restored. Reboot to apply.'; fi")));

        label("BOOT ANIMATION (QMG)");
        act("🎞️", "Backup QMG Files", "Copies the system .qmg files to /sdcard/AllInOne/qmg_backup.", () -> runSu(
                "mkdir -p " + DIR + "/qmg_backup && cp /system/media/*.qmg " + DIR
                        + "/qmg_backup/ && ls -l " + DIR + "/qmg_backup"));
        act("🎬", "Install Custom QMG", "Installs .qmg files from /sdcard/AllInOne/qmg as a systemless module.", () ->
                confirm("Install custom QMG?",
                        ".qmg files in " + DIR + "/qmg/ (named like bootsamsung.qmg, shutdown.qmg) "
                                + "will be installed as a systemless module.",
                        () -> runSu(
                                "S=" + DIR + "/qmg; D=/data/adb/modules/allinone_qmg; "
                                        + "ls $S/*.qmg >/dev/null 2>&1 || { echo \"No .qmg files in $S\"; exit 1; }; "
                                        + "mkdir -p $D/system/media && cp $S/*.qmg $D/system/media/ && "
                                        + "printf 'id=allinone_qmg\\nname=AllInOne QMG\\nversion=1\\nversionCode=1\\n"
                                        + "author=Namal\\ndescription=Custom boot animation\\n' > $D/module.prop && "
                                        + "(chcon u:object_r:system_file:s0 $D/system/media/* 2>/dev/null; true) && "
                                        + "ls -l $D/system/media && echo 'Reboot to apply'")));
        act("🗑️", "Remove QMG Module", "Deletes the custom boot animation module.", () ->
                runSu("rm -rf /data/adb/modules/allinone_qmg && echo 'Removed. Reboot to apply.'"));
    }

    // ---------- UI tweaks ----------

    String anim(String v) {
        return "for k in window_animation_scale transition_animation_scale animator_duration_scale; "
                + "do settings put global $k " + v + "; done; echo 'animation scale = " + v + "'";
    }

    void tabUi() {
        label("DPI");
        final EditText dpi = edit("DPI  (240 - 600)");
        dpi.setInputType(InputType.TYPE_CLASS_NUMBER);
        act("📐", "Show DPI", "Shows the current screen density.", () -> runSu("wm density"));
        act("🔧", "Set DPI", "Applies the DPI value you typed above.", () -> {
            String d = dpi.getText().toString().trim();
            if (!d.matches("[0-9]{3}") || Integer.parseInt(d) < 240 || Integer.parseInt(d) > 600) {
                out.setText("DPI must be between 240 and 600");
                return;
            }
            confirm("Change DPI?",
                    "If the UI breaks, run 'wm density reset' from the Terminal tab.",
                    () -> runSu("wm density " + d + "; wm density"));
        });
        act("♻️", "Reset DPI", "Restores the default density.", () -> runSu("wm density reset; wm density"));

        label("ANIMATION SPEED");
        act("⏩", "Fast (0.5x)", "Doubles the animation speed.", () -> runSu(anim("0.5")));
        act("▶️", "Normal (1x)", "Restores normal animation speed.", () -> runSu(anim("1")));
        act("⏹️", "Off", "Turns animations off completely.", () -> runSu(anim("0")));

        label("STATUS BAR");
        act("🔋", "Show Battery %", "Shows battery percentage in the status bar. May not work on every One UI.",
                () -> runSu("settings put system status_bar_show_battery_percent 1; echo done"));
        final EditText ic = edit("icons  (e.g. rotate,headset)");
        act("🙈", "Hide Icons", "Hides the status bar icons you typed above.", () -> {
            String v = ic.getText().toString().trim();
            if (!v.matches("[a-z_,]+")) {
                out.setText("Icon names: a-z _ , only");
                return;
            }
            runSu("settings put secure icon_blacklist " + v + "; echo done");
        });
        act("👁️", "Reset Icons", "Shows all hidden icons again.",
                () -> runSu("settings delete secure icon_blacklist; echo reset"));
    }

    // ---------- Fonts / Sounds ----------

    void tabMedia() {
        label("FONTS");
        act("🔤", "List Fonts", "Lists the font files in /system/fonts. Name your font files the same.",
                () -> runSu("ls /system/fonts | head -n 100"));
        act("🅰️", "Install Fonts", "Installs fonts from /sdcard/AllInOne/fonts as a module.", () ->
                confirm("Install fonts?",
                        "TTF files in " + DIR + "/fonts/ must use the same names as the files in /system/fonts. "
                                + "A wrong font can break the UI.",
                        () -> runSu("S=" + DIR + "/fonts; ls $S/* >/dev/null 2>&1 || { echo \"No files in $S\"; exit 1; }; "
                                + modInit("allinone_fonts", "AllInOne Fonts")
                                + " && mkdir -p $D/system/fonts && cp $S/* $D/system/fonts/ && "
                                + "(chcon -R u:object_r:system_file:s0 $D/system 2>/dev/null; true) && "
                                + "ls -l $D/system/fonts && echo 'Reboot to apply'")));
        act("🗑️", "Remove Fonts Module", "Deletes the installed fonts module.", () ->
                runSu("rm -rf /data/adb/modules/allinone_fonts && echo 'Removed. Reboot to apply.'"));

        label("SOUNDS");
        act("🎵", "List Sounds", "Lists the system sound files.", () -> runSu("ls -R /system/media/audio | head -n 120"));
        act("🔊", "Install Sounds", "Installs sounds from /sdcard/AllInOne/sounds as a module.", () ->
                confirm("Install sounds?",
                        "Put ringtones/ notifications/ ui/ alarms/ folders in " + DIR + "/sounds/, "
                                + "using the same file names as the system sounds.",
                        () -> runSu("S=" + DIR + "/sounds; ls $S/* >/dev/null 2>&1 || { echo \"No files in $S\"; exit 1; }; "
                                + modInit("allinone_sounds", "AllInOne Sounds")
                                + " && mkdir -p $D/system/media/audio && cp -r $S/. $D/system/media/audio/ && "
                                + "(chcon -R u:object_r:system_file:s0 $D/system 2>/dev/null; true) && "
                                + "echo 'Installed. Reboot to apply.'")));
        act("🗑️", "Remove Sounds Module", "Deletes the installed sounds module.", () ->
                runSu("rm -rf /data/adb/modules/allinone_sounds && echo 'Removed. Reboot to apply.'"));
    }

    String modInit(String id, String name) {
        return "D=/data/adb/modules/" + id + "; mkdir -p $D && printf 'id=" + id + "\\nname=" + name
                + "\\nversion=1\\nversionCode=1\\nauthor=Namal\\ndescription=" + name + "\\n' > $D/module.prop";
    }

    // ---------- Hidden Samsung features ----------

    void tabHidden() {
        label("VIEW");
        final EditText pat = edit("search tag (empty = show all)");
        act("👀", "View floating_feature", "Shows tags in Samsung's floating_feature.xml.", () -> {
            String t = pat.getText().toString().trim();
            if (t.isEmpty()) runSu("head -n 120 /system/etc/floating_feature.xml");
            else runSu("grep -i -- " + Sh.q(t) + " /system/etc/floating_feature.xml");
        });

        label("CHANGE");
        final EditText tag = edit("TAG  (e.g. SEC_FLOATING_FEATURE_COMMON_CONFIG_BLUR_SUPPORT)");
        final EditText fv = edit("value  (e.g. TRUE)");
        act("🧬", "Apply Feature (Module)", "Changes the tag via a systemless module. The original file is untouched.", () -> {
            String t = tag.getText().toString().trim();
            String v = fv.getText().toString().trim();
            if (!t.matches("[A-Za-z0-9_]+") || !v.matches("[A-Za-z0-9_.,:/ -]*")) {
                out.setText("Tag or value contains characters that are not allowed");
                return;
            }
            confirm("Apply feature?",
                    "A copy of floating_feature.xml is placed in a module and edited there.",
                    () -> runSu(modInit("allinone_ff", "AllInOne FloatingFeature")
                            + " && mkdir -p $D/system/etc && F=$D/system/etc/floating_feature.xml && "
                            + "{ [ -f $F ] || cp /system/etc/floating_feature.xml $F; } && "
                            + "if grep -q '<" + t + ">' $F; then sed -i 's|<" + t + ">.*</" + t + ">|<" + t + ">" + v + "</" + t + ">|' $F; "
                            + "else sed -i 's|</SecFloatingFeatureSet>|<" + t + ">" + v + "</" + t + "></SecFloatingFeatureSet>|' $F; fi; "
                            + "(chcon u:object_r:system_file:s0 $F 2>/dev/null; true); grep '" + t + "' $F; echo 'Reboot to apply'"));
        });

        label("CLEANUP");
        act("🗑️", "Remove Feature Module", "Deletes the feature module and restores the original.", () ->
                runSu("rm -rf /data/adb/modules/allinone_ff && echo 'Removed. Reboot to apply.'"));
    }

    // ---------- Modules ----------

    void tabModules() {
        label("MANAGE");
        act("📃", "List Modules", "Lists the installed Magisk/KSU modules.", () -> runSu("ls -l /data/adb/modules"));
        act("🧹", "Remove All AllInOne Modules", "Deletes every module created by this app.", () ->
                confirm("Remove all AllInOne modules?",
                        "Modules starting with allinone_ (props, qmg, fonts, sounds, ff) will be deleted.",
                        () -> runSu("rm -rf /data/adb/modules/allinone_* && ls /data/adb/modules; echo 'Reboot to apply'")));
    }

    // ---------- Scan (no root needed) ----------

    void tabScan() {
        label("SCAN");
        act("🛡️", "Run Phone Scan",
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
        if (!rootTrace) ok(sb, "No su files visible to the app");

        try {
            int dev = Settings.Global.getInt(getContentResolver(),
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0);
            int adb = Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0);
            if (dev == 1) flag(sb, "Developer options ON"); else ok(sb, "Developer options OFF");
            if (adb == 1) flag(sb, "USB debugging ON"); else ok(sb, "USB debugging OFF");
        } catch (Exception e) {
            sb.append("Could not read settings\n");
        }

        if (android.os.Debug.isDebuggerConnected()) flag(sb, "Debugger connected");
        else ok(sb, "No debugger attached");

        String fp = Build.FINGERPRINT == null ? "" : Build.FINGERPRINT.toLowerCase();
        String hw = Build.HARDWARE == null ? "" : Build.HARDWARE.toLowerCase();
        if (fp.contains("generic") || hw.contains("goldfish") || hw.contains("ranchu"))
            flag(sb, "Looks like an emulator");
        else ok(sb, "Looks like a real device");

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
        if (hits == 0) ok(sb, "No suspicious packages");

        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", 27042), 300);
            flag(sb, "Frida port 27042 is open");
        } catch (Exception e) {
            ok(sb, "Frida port is closed");
        }

        sb.append("\n(Heuristic scan, false positives are possible)\n");

        try {
            File f = new File(getExternalFilesDir(null), "scan_report.txt");
            FileWriter w = new FileWriter(f);
            w.write(sb.toString());
            w.close();
            sb.append("Saved: ").append(f.getAbsolutePath()).append('\n');
        } catch (Exception e) {
            sb.append("Could not save report: ").append(e.getMessage()).append('\n');
        }
        return sb.toString();
    }

    // ---------- Terminal ----------

    void tabTerm() {
        label("COMMAND");
        final EditText cmd = edit("type a command");
        cmd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        act("👑", "Run as Root", "Runs the command above with root.", () -> {
            String c = cmd.getText().toString();
            if (!c.trim().isEmpty()) runSu(c);
        });
        act("▶️", "Run (No Root)", "Runs the command without root.", () -> {
            String c = cmd.getText().toString();
            if (!c.trim().isEmpty()) bg(() -> Sh.sh(c));
        });
    }
}
