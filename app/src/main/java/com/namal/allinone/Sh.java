package com.namal.allinone;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Sh {

    public static String su(String cmd) {
        return exec(new String[]{"su", "-c", cmd});
    }

    public static String sh(String cmd) {
        return exec(new String[]{"sh", "-c", cmd});
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
