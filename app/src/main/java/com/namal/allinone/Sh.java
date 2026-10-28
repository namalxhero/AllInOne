package com.namal.allinone;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class Sh {

    static final String[] SU_PATHS = {
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su",
            "/data/adb/ksu/bin/su", "/data/adb/ap/bin/su", "/su/bin/su",
            "/vendor/bin/su", "/product/bin/su"
    };

    public static String findSu() {
        for (String p : SU_PATHS) {
            if (new File(p).exists()) return p;
        }
        return "su";
    }

    public static String su(String cmd) {
        String r = exec(new String[]{findSu(), "-c", cmd});
        if (r.startsWith("Error: Cannot run program")) {
            return "❌ su හොයාගන්න බෑ.\n"
                    + "මේ phone එකේ root නෑ, නැත්නම් Magisk/KernelSU එකෙන් "
                    + "AllInOne එකට root permission දීලා නෑ.\n\n" + r;
        }
        return r;
    }

    public static String sh(String cmd) {
        return exec(new String[]{"sh", "-c", cmd});
    }

    public static boolean hasRoot() {
        return su("id").contains("uid=0");
    }

    static String exec(String[] args) {
        StringBuilder sb = new StringBuilder();
        try {
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String l;
            while ((l = r.readLine()) != null) sb.append(l).append('\n');
            p.waitFor();
        } catch (Exception e) {
            sb.append("Error: ").append(e.getMessage());
        }
        return sb.toString();
    }

    // shell single-quote
    public static String q(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
