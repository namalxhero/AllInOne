package com.namal.allinone;

import java.util.Random;

/** Original synthesized phonk-style loop: 808 bass, cowbell lead, kick, clap, hats. 140 BPM, A minor. */
public class Phonk {

    static final double BPM = 140.0;
    static final double STEP = 60.0 / BPM / 4.0;

    static int idx(int stepIndex, int sr) {
        return (int) (stepIndex * STEP * sr);
    }

    public static short[] make(int sr) {
        final int steps = 128; // 8 bars
        final int n = (int) (steps * STEP * sr);
        double[] drums = new double[n];
        double[] bass = new double[n];
        double[] lead = new double[n];
        Random rnd = new Random(42);

        double[] roots = {55.0, 43.65, 49.0, 41.2}; // A1 F1 G1 E1

        for (int bar = 0; bar < 8; bar++) {
            int b = bar * 16;

            int[] kicks = (bar % 4 == 3) ? new int[]{0, 6, 10, 13} : new int[]{0, 6, 10};
            for (int k : kicks) kick(drums, idx(b + k, sr), sr);

            clap(drums, idx(b + 4, sr), sr, rnd);
            clap(drums, idx(b + 12, sr), sr, rnd);

            for (int s = 0; s < 16; s += 2) hat(drums, idx(b + s, sr), sr, rnd, 0.10, 90.0, 0.06);
            hat(drums, idx(b + 7, sr), sr, rnd, 0.07, 22.0, 0.22);
            if (bar % 2 == 1) {
                hat(drums, idx(b + 13, sr), sr, rnd, 0.11, 90.0, 0.06);
                hat(drums, idx(b + 14, sr), sr, rnd, 0.12, 90.0, 0.06);
                hat(drums, idx(b + 15, sr), sr, rnd, 0.13, 90.0, 0.06);
            }

            double root = roots[bar % 4];
            bass808(bass, idx(b, sr), (int) (6 * STEP * sr), root, root, sr);
            bass808(bass, idx(b + 6, sr), (int) (4 * STEP * sr), root, root, sr);
            bass808(bass, idx(b + 10, sr), (int) (6 * STEP * sr), root, root * 1.5, sr);
        }

        int[] ls = {0, 3, 6, 8, 11, 14, 16, 19, 22, 24, 27, 30};
        double[] lf = {659.25, 659.25, 587.33, 523.25, 440.0, 523.25,
                659.25, 659.25, 783.99, 659.25, 587.33, 523.25};
        for (int rep = 0; rep < 4; rep++) {
            for (int i = 0; i < ls.length; i++) {
                cowbell(lead, idx(rep * 32 + ls[i], sr), lf[i], sr);
            }
        }

        double y = 0;
        short[] pcm = new short[n];
        for (int i = 0; i < n; i++) {
            y += 0.25 * (lead[i] - y);
            double v = drums[i] + bass[i] + y;
            v = Math.tanh(1.1 * v) * 0.9;
            pcm[i] = (short) (v * 32767);
        }
        return pcm;
    }

    static void kick(double[] o, int s, int sr) {
        int len = (int) (0.32 * sr);
        double ph = 0;
        for (int k = 0; k < len && s + k < o.length; k++) {
            double t = (double) k / sr;
            double f = 45 + 105 * Math.exp(-t * 40);
            ph += 2 * Math.PI * f / sr;
            o[s + k] += 0.9 * Math.exp(-t * 9) * Math.sin(ph);
        }
    }

    static void clap(double[] o, int s, int sr, Random r) {
        int len = (int) (0.18 * sr);
        double prev = 0;
        for (int k = 0; k < len && s + k < o.length; k++) {
            double t = (double) k / sr;
            double nz = r.nextDouble() * 2 - 1;
            double hp = nz - prev;
            prev = nz;
            double burst = (t % 0.012 < 0.006) ? 1.0 : 0.6;
            o[s + k] += 0.30 * hp * Math.exp(-t * 28) * burst;
        }
    }

    static void hat(double[] o, int s, int sr, Random r, double amp, double decay, double dur) {
        int len = (int) (dur * sr);
        double prev = 0;
        for (int k = 0; k < len && s + k < o.length; k++) {
            double t = (double) k / sr;
            double nz = r.nextDouble() * 2 - 1;
            double hp = nz - prev;
            prev = nz;
            o[s + k] += amp * hp * Math.exp(-t * decay);
        }
    }

    static void bass808(double[] o, int s, int len, double f0, double f1, int sr) {
        double ph = 0;
        int rel = (int) (0.02 * sr);
        for (int k = 0; k < len + rel && s + k < o.length; k++) {
            double t = (double) k / sr;
            double f = f1 + (f0 * 1.0 - f1) * Math.exp(-t * 18) + 18 * Math.exp(-t * 60);
            ph += 2 * Math.PI * f / sr;
            double env = Math.min(1.0, t / 0.003) * Math.exp(-t * 2.2);
            if (k > len) env *= 1.0 - (double) (k - len) / rel;
            o[s + k] += 0.8 * Math.tanh(2.4 * Math.sin(ph) * env);
        }
    }

    static double sq(double f, double t) {
        return Math.signum(Math.sin(2 * Math.PI * f * t));
    }

    static void cowbell(double[] o, int s, double f, int sr) {
        int len = (int) (0.25 * sr);
        for (int k = 0; k < len && s + k < o.length; k++) {
            double t = (double) k / sr;
            o[s + k] += 0.12 * Math.exp(-t * 14) * (sq(f, t) + sq(f * 1.504, t));
        }
    }
}
