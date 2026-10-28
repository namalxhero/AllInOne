package com.namal.allinone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/** Animated anime-style nature background: 3 scenes, rotating every minute. */
public class SceneView extends View {

    static final long PERIOD = 60000L;
    static final long FADE = 2500L;

    static final float[] WIND = {1.0f, 0.6f, 2.2f};

    static final int[][] SKY = {
            {0xFFFFA8C8, 0xFFFFCBB0, 0xFFFFEFD0},
            {0xFF070B26, 0xFF241A5A, 0xFF5A3B8C},
            {0xFFFF8A4C, 0xFFFFB86B, 0xFFFFE2A6}};
    static final int[] ORB = {0xFFFFF0F5, 0xFFFFF6D6, 0xFFFFF4C8};
    static final int[] GLOW = {0xAAFFD6E8, 0x88FFF2B0, 0xAAFFD27A};
    static final int[] MTN_FAR = {0xFFE9A6C6, 0xFF1D1A4A, 0xFFE08A55};
    static final int[] MTN_MID = {0xFFD088B0, 0xFF15123A, 0xFFBE6440};
    static final int[] GROUND = {0xFF7A4468, 0xFF0C1E2E, 0xFF6B3E24};
    static final int[] CAN_BACK = {0xFFF2A0C4, 0xFF1C5B5E, 0xFFE59A45};
    static final int[] CAN_FRONT = {0xFFE0679A, 0xFF0F3F42, 0xFFC8481F};
    static final int[] CAN_HI = {0xFFFFC9DE, 0xFF2F8F84, 0xFFFFC866};
    static final int[] TRUNK = {0xFF6A3B4D, 0xFF120E24, 0xFF4A2B1C};
    static final int[] GRASS = {0xFF86C07F, 0xFF1F7A5A, 0xFFC09A44};
    static final int[][] LEAF = {
            {0xFFFFB7D0, 0xFFFFFFFF, 0xFFFF9DBE},
            {0xFF7DF9C2, 0xFFB6FFE0, 0xFF4FD6A5},
            {0xFFE4572E, 0xFFF2A93B, 0xFFB33A1E}};
    static final int[] OVERLAY = {0x44000000, 0x14000000, 0x4A000000};

    static final int N = 36;   // leaves / petals
    static final int T = 10;   // trees
    static final int G = 70;   // grass blades
    static final int S = 60;   // stars
    static final int F = 16;   // fireflies
    static final int C = 4;    // clouds
    static final int W = 6;    // wind streaks

    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Path path = new Path();
    final Path mtnFar = new Path();
    final Path mtnMid = new Path();
    final Path ground = new Path();
    final Random rnd = new Random(7);

    final float d;
    int w, h;
    long t0 = SystemClock.uptimeMillis();
    long last = t0;
    float ox, oy, orbR;

    Shader[] skySh = new Shader[3];
    Shader[] glowSh = new Shader[3];

    float[] lx = new float[N], ly = new float[N], ls = new float[N], lv = new float[N];
    float[] lph = new float[N], lr = new float[N], lrs = new float[N];
    float[] tx = new float[T], ty = new float[T], tsc = new float[T], tph = new float[T];
    boolean[] tfront = new boolean[T];
    float[] gx = new float[G], gb = new float[G], gh = new float[G], gph = new float[G];
    float[] sx = new float[S], sy = new float[S], sph = new float[S], sr = new float[S];
    float[] fx = new float[F], fy = new float[F], fph = new float[F];
    float[] cx = new float[C], cy = new float[C], cs = new float[C], cv = new float[C];
    float[] wx = new float[W], wy = new float[W], wl = new float[W], wv = new float[W];

    public SceneView(Context ctx) {
        super(ctx);
        d = ctx.getResources().getDisplayMetrics().density;
    }

    public String modeName() {
        int m = (int) (((SystemClock.uptimeMillis() - t0) / PERIOD) % 3);
        return new String[]{"Sakura Dawn", "Moonlit Forest", "Autumn Gale"}[m];
    }

    @Override
    protected void onSizeChanged(int nw, int nh, int ow, int oh) {
        w = nw;
        h = nh;
        if (w == 0 || h == 0) return;
        ox = w * 0.74f;
        oy = h * 0.2f;
        orbR = Math.min(w, h) * 0.085f;

        for (int m = 0; m < 3; m++) {
            skySh[m] = new LinearGradient(0, 0, 0, h * 0.9f, SKY[m],
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
            glowSh[m] = new RadialGradient(ox, oy, orbR * 3.4f,
                    new int[]{GLOW[m], GLOW[m] & 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
        }

        int step = Math.max(8, w / 40);
        mtnFar.reset();
        mtnFar.moveTo(0, h);
        for (int x = 0; x <= w + step; x += step) {
            float y = h * 0.58f - (float) (Math.sin(x * 0.004 + 1) * h * 0.05 + Math.sin(x * 0.011) * h * 0.025);
            mtnFar.lineTo(x, y);
        }
        mtnFar.lineTo(w + step, h);
        mtnFar.close();

        mtnMid.reset();
        mtnMid.moveTo(0, h);
        for (int x = 0; x <= w + step; x += step) {
            float y = h * 0.74f - (float) (Math.sin(x * 0.006 + 2) * h * 0.03 + Math.sin(x * 0.015) * h * 0.012);
            mtnMid.lineTo(x, y);
        }
        mtnMid.lineTo(w + step, h);
        mtnMid.close();

        ground.reset();
        ground.moveTo(0, h);
        for (int x = 0; x <= w + step; x += step) {
            float y = h * 0.87f + (float) (Math.sin(x * 0.006) * h * 0.012);
            ground.lineTo(x, y);
        }
        ground.lineTo(w + step, h);
        ground.close();

        float[] bx = {0.04f, 0.17f, 0.30f, 0.44f, 0.58f, 0.72f, 0.88f};
        for (int i = 0; i < 7; i++) {
            tx[i] = w * bx[i] + (rnd.nextFloat() - 0.5f) * d * 20;
            ty[i] = h * 0.80f;
            tsc[i] = (0.55f + rnd.nextFloat() * 0.2f) * d;
            tph[i] = rnd.nextFloat() * 6.28f;
            tfront[i] = false;
        }
        float[] fxp = {0.03f, 0.52f, 0.97f};
        for (int i = 0; i < 3; i++) {
            int k = 7 + i;
            tx[k] = w * fxp[i];
            ty[k] = h * 0.93f;
            tsc[k] = (1.0f + rnd.nextFloat() * 0.2f) * d;
            tph[k] = rnd.nextFloat() * 6.28f;
            tfront[k] = true;
        }

        for (int i = 0; i < N; i++) {
            lx[i] = rnd.nextFloat() * w;
            ly[i] = rnd.nextFloat() * h;
            ls[i] = (4f + rnd.nextFloat() * 5f) * d;
            lv[i] = rnd.nextFloat();
            lph[i] = rnd.nextFloat() * 6.28f;
            lr[i] = rnd.nextFloat() * 360f;
            lrs[i] = (rnd.nextFloat() - 0.5f) * 160f;
        }
        for (int i = 0; i < G; i++) {
            gx[i] = rnd.nextFloat() * w;
            gb[i] = h * 0.89f + rnd.nextFloat() * h * 0.1f;
            gh[i] = (12f + rnd.nextFloat() * 18f) * d;
            gph[i] = rnd.nextFloat() * 6.28f;
        }
        for (int i = 0; i < S; i++) {
            sx[i] = rnd.nextFloat() * w;
            sy[i] = rnd.nextFloat() * h * 0.5f;
            sph[i] = rnd.nextFloat() * 6.28f;
            sr[i] = (0.8f + rnd.nextFloat() * 1.0f) * d;
        }
        for (int i = 0; i < F; i++) {
            fx[i] = rnd.nextFloat() * w;
            fy[i] = h * (0.45f + rnd.nextFloat() * 0.45f);
            fph[i] = rnd.nextFloat() * 6.28f;
        }
        for (int i = 0; i < C; i++) {
            cx[i] = rnd.nextFloat() * w;
            cy[i] = h * (0.08f + rnd.nextFloat() * 0.25f);
            cs[i] = (40f + rnd.nextFloat() * 40f) * d;
            cv[i] = (6f + rnd.nextFloat() * 10f) * d;
        }
        for (int i = 0; i < W; i++) {
            wx[i] = rnd.nextFloat() * w;
            wy[i] = h * (0.1f + rnd.nextFloat() * 0.7f);
            wl[i] = (80f + rnd.nextFloat() * 90f) * d;
            wv[i] = 0.7f + rnd.nextFloat() * 0.6f;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        if (w == 0 || h == 0) return;
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(0.05f, (now - last) / 1000f);
        last = now;
        float t = (now - t0) / 1000f;
        long el = now - t0;
        int m = (int) ((el / PERIOD) % 3);
        long inP = el % PERIOD;

        float gust = 0.55f + 0.35f * (float) Math.sin(t * 0.5) + 0.2f * (float) Math.sin(t * 1.3 + 1);
        update(dt, gust * WIND[m]);

        drawScene(c, m, t, gust * WIND[m]);
        if (inP > PERIOD - FADE) {
            float a = (inP - (PERIOD - FADE)) / (float) FADE;
            int nm = (m + 1) % 3;
            int sc = c.saveLayerAlpha(0, 0, w, h, (int) (a * 255));
            drawScene(c, nm, t, gust * WIND[nm]);
            c.restoreToCount(sc);
        }
        postInvalidateOnAnimation();
    }

    void update(float dt, float wind) {
        for (int i = 0; i < N; i++) {
            lph[i] += dt * (1.2f + lv[i]);
            float vx = d * (22f + 80f * wind) * (0.6f + lv[i]);
            float vy = d * (16f + 22f * lv[i]);
            lx[i] += vx * dt + (float) Math.sin(lph[i]) * d * 10f * dt;
            ly[i] += vy * dt + (float) Math.cos(lph[i] * 0.8f) * d * 6f * dt;
            lr[i] += lrs[i] * dt * (1f + wind);
            if (lx[i] > w + 20 * d) {
                lx[i] = -20 * d;
                ly[i] = rnd.nextFloat() * h;
            }
            if (ly[i] > h + 20 * d) {
                ly[i] = -20 * d;
                lx[i] = rnd.nextFloat() * w;
            }
        }
        for (int i = 0; i < W; i++) {
            wx[i] += d * (260f + 380f * wind) * wv[i] * dt;
            if (wx[i] - wl[i] > w) {
                wx[i] = 0;
                wy[i] = h * (0.1f + rnd.nextFloat() * 0.7f);
            }
        }
    }

    static int blend(int a, int b, float f) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        int r = (int) (ar + (br - ar) * f);
        int g = (int) (ag + (bg - ag) * f);
        int bl = (int) (ab + (bb - ab) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    void drawScene(Canvas c, int m, float t, float wind) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(skySh[m]);
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        if (m == 1) {
            for (int i = 0; i < S; i++) {
                int al = (int) (90 + 120 * (0.5 + 0.5 * Math.sin(t * 1.5 + sph[i])));
                p.setColor((al << 24) | 0x00FFFFFF);
                c.drawCircle(sx[i], sy[i], sr[i], p);
            }
        }

        p.setShader(glowSh[m]);
        c.drawCircle(ox, oy, orbR * 3.4f, p);
        p.setShader(null);
        p.setColor(ORB[m]);
        c.drawCircle(ox, oy, orbR, p);
        if (m == 1) {
            p.setColor(SKY[1][1]);
            c.drawCircle(ox + orbR * 0.38f, oy - orbR * 0.15f, orbR * 0.9f, p);
        }

        p.setColor(m == 1 ? 0x33B8A8FF : 0x66FFFFFF);
        for (int i = 0; i < C; i++) {
            float x = (cx[i] + t * cv[i]) % (w + 300 * d) - 150 * d;
            float y = cy[i];
            float s = cs[i];
            c.drawOval(x - s, y - s * 0.28f, x + s, y + s * 0.28f, p);
            c.drawOval(x - s * 0.6f, y - s * 0.5f, x + s * 0.5f, y + s * 0.1f, p);
            c.drawOval(x - s * 0.1f, y - s * 0.4f, x + s * 0.9f, y + s * 0.15f, p);
        }

        p.setColor(MTN_FAR[m]);
        c.drawPath(mtnFar, p);
        p.setColor(MTN_MID[m]);
        c.drawPath(mtnMid, p);

        for (int i = 0; i < T; i++) if (!tfront[i]) drawTree(c, i, m, t, wind);
        p.setColor(GROUND[m]);
        c.drawPath(ground, p);
        for (int i = 0; i < T; i++) if (tfront[i]) drawTree(c, i, m, t, wind);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.2f * d);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(GRASS[m]);
        for (int i = 0; i < G; i++) {
            float sway = (float) Math.sin(t * 2f + gph[i]) * d * (2f + 6f * wind);
            c.drawLine(gx[i], gb[i], gx[i] + sway, gb[i] - gh[i], p);
        }

        int wa = (int) (40 + 70 * Math.min(1f, wind));
        p.setColor((wa << 24) | 0x00FFFFFF);
        p.setStrokeWidth(1.6f * d);
        for (int i = 0; i < W; i++) {
            path.reset();
            path.moveTo(wx[i] - wl[i], wy[i]);
            path.quadTo(wx[i] - wl[i] * 0.5f, wy[i] - 10 * d * (float) Math.sin(t * 2 + i), wx[i], wy[i]);
            c.drawPath(path, p);
        }
        p.setStyle(Paint.Style.FILL);

        for (int i = 0; i < N; i++) {
            c.save();
            c.translate(lx[i], ly[i]);
            c.rotate(lr[i]);
            float flip = 0.35f + 0.65f * Math.abs((float) Math.cos(lph[i] * 1.7f));
            c.scale(1f, flip);
            p.setColor(LEAF[m][i % 3]);
            c.drawOval(-ls[i], -ls[i] * 0.5f, ls[i], ls[i] * 0.5f, p);
            c.restore();
        }

        if (m == 1) {
            for (int i = 0; i < F; i++) {
                float x = fx[i] + (float) Math.sin(t * 0.7 + fph[i]) * 30 * d;
                float y = fy[i] + (float) Math.cos(t * 0.9 + fph[i]) * 18 * d;
                int al = (int) (80 + 150 * (0.5 + 0.5 * Math.sin(t * 2.2 + fph[i])));
                p.setColor(((al / 3) << 24) | 0x00CFFF7A);
                c.drawCircle(x, y, 9 * d, p);
                p.setColor((al << 24) | 0x00EFFF9A);
                c.drawCircle(x, y, 3 * d, p);
            }
        }

        p.setColor(OVERLAY[m]);
        c.drawRect(0, 0, w, h, p);
    }

    void drawTree(Canvas c, int i, int m, float t, float wind) {
        float sc = tsc[i];
        float sway = (float) Math.sin(t * 1.25 + tph[i]) * (1.2f + 3.2f * wind) + wind * 2.0f;
        int dark = tfront[i] ? CAN_FRONT[m] : CAN_BACK[m];
        int hi = CAN_HI[m];
        int mid = blend(dark, hi, 0.35f);

        c.save();
        c.translate(tx[i], ty[i]);
        c.rotate(sway * 0.6f);
        float th = 110f * sc;
        float tw = 9f * sc;
        p.setColor(TRUNK[m]);
        path.reset();
        path.moveTo(-tw, 0);
        path.quadTo(-tw * 0.6f, -th * 0.5f, -tw * 0.3f, -th);
        path.lineTo(tw * 0.3f, -th);
        path.quadTo(tw * 0.6f, -th * 0.5f, tw, 0);
        path.close();
        c.drawPath(path, p);

        c.translate(0, -th);
        c.rotate(sway * 0.8f);
        float r = 38f * sc;
        p.setColor(dark);
        c.drawCircle(-r * 0.7f, r * 0.2f, r * 0.9f, p);
        c.drawCircle(r * 0.7f, r * 0.2f, r * 0.9f, p);
        c.drawCircle(0, -r * 0.5f, r, p);
        p.setColor(mid);
        c.drawCircle(-r * 0.6f, 0, r * 0.8f, p);
        c.drawCircle(r * 0.6f, 0, r * 0.8f, p);
        c.drawCircle(0, -r * 0.7f, r * 0.9f, p);
        p.setColor(hi);
        c.drawCircle(-r * 0.3f, -r * 0.95f, r * 0.45f, p);
        c.drawCircle(r * 0.35f, -r * 0.6f, r * 0.3f, p);
        c.restore();
    }
}
