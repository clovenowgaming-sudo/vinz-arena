package com.vinz.moba;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;

import java.util.HashMap;
import java.util.Random;

/** Sound effects synthesized at startup (no audio files needed). */
public class Sfx {
    static final int SR = 22050;
    static final HashMap<String, Sound> snd = new HashMap<String, Sound>();
    static final Random R = new Random(5);
    static boolean ok;

    public static void init() {
        try {
            add("click", mix(0.08f, tone(0, 0.08f, 1400, 900, 0.5f, 0)));
            add("atk", mix(0.16f, noise(0, 0.14f, 0.5f), tone(0, 0.14f, 700, 200, 0.3f, 1)));
            add("s1", mix(0.35f, tone(0, 0.3f, 300, 1000, 0.35f, 1), noise(0, 0.3f, 0.3f)));
            add("s2", mix(0.35f, tone(0, 0.3f, 200, 600, 0.4f, 1), tone(0.05f, 0.3f, 400, 900, 0.2f, 0)));
            add("s3", mix(0.7f, tone(0, 0.6f, 160, 50, 0.7f, 0), noise(0, 0.5f, 0.6f)));
            add("hit", mix(0.12f, noise(0, 0.08f, 0.6f), tone(0, 0.1f, 260, 90, 0.5f, 0)));
            add("shot", mix(0.12f, tone(0, 0.1f, 900, 400, 0.3f, 0)));
            for (int t = 1; t <= 6; t++) add("k" + t, kill(t));
            add("death", mix(0.7f, tone(0, 0.6f, 420, 70, 0.6f, 1)));
            add("respawn", mix(0.3f, tone(0, 0.25f, 300, 900, 0.4f, 0)));
            add("win", mix(1.2f, notes(new float[]{523, 659, 784, 1046, 784, 1046}, 0.17f, 0.5f)));
            add("lose", mix(1.2f, notes(new float[]{392, 330, 262, 196}, 0.26f, 0.5f)));
            add("tower", mix(0.9f, noise(0, 0.7f, 0.7f), tone(0, 0.8f, 110, 35, 0.8f, 0)));
            add("buff", mix(0.5f, notes(new float[]{440, 660, 880}, 0.12f, 0.4f)));
            ok = true;
        } catch (Throwable t) {
            ok = false;
        }
    }

    public static void play(String n) { play(n, 1f); }

    public static void play(String n, float vol) {
        if (!ok) return;
        Sound s = snd.get(n);
        if (s != null) s.play(Math.min(1f, vol));
    }

    static void add(String n, short[] pcm) {
        FileHandle fh = Gdx.files.local("sfx/" + n + ".wav");
        fh.writeBytes(wav(pcm), false);
        snd.put(n, Gdx.audio.newSound(fh));
    }

    // a buffer part: {startSample, samples...}
    static float[] tone(float t0, float dur, float f0, float f1, float vol, int wave) {
        int n = (int) (dur * SR), off = (int) (t0 * SR);
        float[] b = new float[off + n];
        double ph = 0;
        for (int i = 0; i < n; i++) {
            float p = i / (float) n;
            double f = f0 + (f1 - f0) * p;
            ph += 2 * Math.PI * f / SR;
            float s = wave == 0 ? (float) Math.sin(ph) : (Math.sin(ph) > 0 ? 0.6f : -0.6f);
            float env = Math.min(1f, i / 120f) * (float) Math.pow(1 - p, 1.6);
            b[off + i] = s * env * vol;
        }
        return b;
    }

    static float[] noise(float t0, float dur, float vol) {
        int n = (int) (dur * SR), off = (int) (t0 * SR);
        float[] b = new float[off + n];
        float lp = 0;
        for (int i = 0; i < n; i++) {
            float p = i / (float) n;
            lp += ((R.nextFloat() * 2 - 1) - lp) * 0.45f;
            b[off + i] = lp * (float) Math.pow(1 - p, 2) * vol;
        }
        return b;
    }

    static float[] notes(float[] f, float step, float vol) {
        float[] out = new float[0];
        for (int i = 0; i < f.length; i++) out = add2(out, tone(i * step, step * 1.6f, f[i], f[i], vol, 1));
        return out;
    }

    static short[] kill(int tier) {
        float[] base = {392, 494, 587, 659, 784, 988, 1175, 1319};
        int cnt = Math.min(8, tier + 2);
        float[] f = new float[cnt];
        for (int i = 0; i < cnt; i++) f[i] = base[i];
        float[] out = notes(f, 0.09f, 0.45f);
        if (tier >= 4) out = add2(out, tone(0, 0.5f, 100, 60, 0.5f, 0));
        if (tier >= 5) out = add2(out, noise(0, 0.4f, 0.4f));
        return mix(0.1f * cnt + 0.5f, out);
    }

    static float[] add2(float[] a, float[] b) {
        float[] o = new float[Math.max(a.length, b.length)];
        for (int i = 0; i < a.length; i++) o[i] += a[i];
        for (int i = 0; i < b.length; i++) o[i] += b[i];
        return o;
    }

    static short[] mix(float dur, float[]... parts) {
        float[] o = new float[0];
        for (float[] p : parts) o = add2(o, p);
        int n = Math.max(o.length, (int) (dur * SR) / 2);
        short[] s = new short[n];
        for (int i = 0; i < o.length; i++) {
            float v = Math.max(-1f, Math.min(1f, o[i]));
            s[i] = (short) (v * 30000);
        }
        return s;
    }

    static byte[] wav(short[] pcm) {
        int dl = pcm.length * 2;
        byte[] b = new byte[44 + dl];
        put(b, 0, "RIFF"); le32(b, 4, 36 + dl); put(b, 8, "WAVE"); put(b, 12, "fmt ");
        le32(b, 16, 16); b[20] = 1; b[22] = 1; le32(b, 24, SR); le32(b, 28, SR * 2); b[32] = 2; b[34] = 16;
        put(b, 36, "data"); le32(b, 40, dl);
        for (int i = 0; i < pcm.length; i++) { b[44 + i * 2] = (byte) (pcm[i] & 0xff); b[45 + i * 2] = (byte) ((pcm[i] >> 8) & 0xff); }
        return b;
    }

    static void put(byte[] b, int o, String s) { for (int i = 0; i < s.length(); i++) b[o + i] = (byte) s.charAt(i); }
    static void le32(byte[] b, int o, int v) { b[o] = (byte) v; b[o + 1] = (byte) (v >> 8); b[o + 2] = (byte) (v >> 16); b[o + 3] = (byte) (v >> 24); }
}
