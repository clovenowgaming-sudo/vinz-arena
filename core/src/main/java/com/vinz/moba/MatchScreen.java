package com.vinz.moba;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;

public class MatchScreen extends ScreenAdapter {
    static class Fx {
        ModelInstance mi; BlendingAttribute ba; int type;
        float t, dur, x0, z0, x1, z1, y0, y1, r;
    }

    final int me = G.myIdx, myTeam = G.team(G.myIdx);
    final World world = new World();
    final ModelBatch mb = new ModelBatch();
    final Environment env = new Environment();
    final PerspectiveCamera cam;
    final Heroes.Rig[] rig = new Heroes.Rig[10];
    final ModelInstance[] ring = new ModelInstance[10];
    final Model ringM, sphereM, boxM;
    final ShapeRenderer sr = new ShapeRenderer();
    final SpriteBatch sb = new SpriteBatch();
    final ArrayList<Fx> fxs = new ArrayList<Fx>();
    final Vector3 tmp = new Vector3();
    float W, H, s;
    float px, pz, prot, cx, cz, t, sendT, atkT, dashT, dvx, dvz;
    int panim;
    final float[] cd = new float[4];
    final float[] vx = new float[10], vz = new float[10], vr = new float[10];
    final float[] hx = new float[10], hy = new float[10];
    final boolean[] hv = new boolean[10];
    float jx, jy, jbx, jby;
    int jptr = -1, bptr = -1;
    boolean holdBasic, keyMove;
    final float[] bx = new float[4], by = new float[4], br = new float[4];
    long endAt;

    public MatchScreen() {
        W = Gdx.graphics.getWidth();
        H = Gdx.graphics.getHeight();
        s = H / 360f;
        env.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.6f, 0.6f, 0.65f, 1f));
        env.add(new DirectionalLight().set(0.9f, 0.88f, 0.8f, -0.5f, -1f, -0.4f));
        cam = new PerspectiveCamera(45, W, H);
        cam.near = 1;
        cam.far = 160;
        ModelBuilder b = new ModelBuilder();
        ringM = b.createCylinder(2, 0.1f, 2, 24, new Material(), Usage.Position | Usage.Normal);
        sphereM = b.createSphere(1, 1, 1, 12, 10, new Material(), Usage.Position | Usage.Normal);
        boxM = b.createBox(1, 1, 1, new Material(), Usage.Position | Usage.Normal);
        for (int i = 0; i < 10; i++) {
            rig[i] = new Heroes.Rig(G.hero[i]);
            vx[i] = G.x[i]; vz[i] = G.z[i]; vr[i] = G.rot[i];
            ring[i] = new ModelInstance(ringM);
            Color c = i == me ? new Color(0.3f, 1f, 0.4f, 1) : (G.team(i) == 0 ? World.BLUE : World.RED);
            Material m = ring[i].materials.get(0);
            m.set(ColorAttribute.createDiffuse(c));
            m.set(ColorAttribute.createEmissive(c));
            m.set(new BlendingAttribute(true, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.65f));
        }
        px = cx = G.x[me]; pz = cz = G.z[me]; prot = G.rot[me];
        layout();
    }

    void layout() {
        bx[0] = W - 80 * s; by[0] = 75 * s; br[0] = 46 * s;
        bx[1] = W - 175 * s; by[1] = 50 * s; br[1] = 32 * s;
        bx[2] = W - 165 * s; by[2] = 120 * s; br[2] = 32 * s;
        bx[3] = W - 100 * s; by[3] = 155 * s; br[3] = 32 * s;
    }

    static float ang(float dx, float dz) { return (float) Math.toDegrees(Math.atan2(dx, dz)); }

    static float lerpAngle(float a, float b, float k) {
        float d = (((b - a) % 360f) + 540f) % 360f - 180f;
        return a + d * k;
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            public boolean touchDown(int x, int y, int ptr, int btn) {
                float ux = x, uy = H - y;
                if (G.ended || G.net.state >= 3) {
                    if (System.currentTimeMillis() - endAt > 1200) leave();
                    return true;
                }
                for (int k = 0; k < 4; k++) {
                    float dx = ux - bx[k], dy = uy - by[k];
                    if (Math.sqrt(dx * dx + dy * dy) <= br[k] * 1.15f) {
                        if (k == 0) { holdBasic = true; bptr = ptr; }
                        cast(k);
                        return true;
                    }
                }
                if (ux < W * 0.5f && jptr < 0) { jptr = ptr; jbx = jx = ux; jby = jy = uy; }
                return true;
            }
            public boolean touchDragged(int x, int y, int ptr) {
                if (ptr == jptr) { jx = x; jy = H - y; }
                return true;
            }
            public boolean touchUp(int x, int y, int ptr, int btn) {
                if (ptr == jptr) jptr = -1;
                if (ptr == bptr) { holdBasic = false; bptr = -1; }
                return true;
            }
            public boolean keyDown(int key) {
                if (key == Input.Keys.J) cast(0);
                if (key == Input.Keys.U) cast(1);
                if (key == Input.Keys.I) cast(2);
                if (key == Input.Keys.O) cast(3);
                return true;
            }
        });
    }

    void leave() {
        boolean lost = G.net.state >= 3;
        G.resetMatch();
        if (lost) { G.loggedIn = false; MobaGame.I.setScreen(new LoginScreen()); }
        else MobaGame.I.setScreen(new LobbyScreen());
    }

    int nearest(float range) {
        int best = -1;
        float bd = range * range;
        for (int j = 0; j < 10; j++) {
            if (G.team(j) == myTeam || !G.alive[j]) continue;
            float dx = vx[j] - px, dz = vz[j] - pz, d = dx * dx + dz * dz;
            if (d < bd) { bd = d; best = j; }
        }
        return best;
    }

    void hit(int j, float dmg) {
        G.net.send("HIT " + j + " " + (int) dmg);
        flash(vx[j], vz[j], Heroes.ACC[G.hero[me]]);
    }

    Fx mk(Model m, Color c, int type, float dur) {
        Fx f = new Fx();
        f.mi = new ModelInstance(m);
        Material mt = f.mi.materials.get(0);
        mt.set(ColorAttribute.createDiffuse(c));
        mt.set(ColorAttribute.createEmissive(c));
        f.ba = new BlendingAttribute(true, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.8f);
        mt.set(f.ba);
        f.type = type; f.dur = dur;
        fxs.add(f);
        return f;
    }

    void ring(float x, float z, float r, Color c, float dur) { Fx f = mk(ringM, c, 0, dur); f.x0 = x; f.z0 = z; f.r = r; }
    void flash(float x, float z, Color c) { Fx f = mk(sphereM, c, 3, 0.25f); f.x0 = x; f.z0 = z; }
    void bolt(float x0, float z0, float y0, float x1, float z1, float y1, Color c, float dur) {
        Fx f = mk(sphereM, c, 1, dur);
        f.x0 = x0; f.z0 = z0; f.y0 = y0; f.x1 = x1; f.z1 = z1; f.y1 = y1;
    }
    void beam(float x0, float z0, float x1, float z1, float w, Color c, float dur) {
        Fx f = mk(boxM, c, 2, dur);
        f.x0 = x0; f.z0 = z0; f.x1 = x1; f.z1 = z1; f.r = w;
    }

    void cast(int k) {
        if (!G.alive[me] || G.ended || !G.started || cd[k] > 0 || dashT > 0) return;
        int h = G.hero[me];
        float[] sk = Heroes.S[h][k];
        int kind = (int) sk[0];
        float a = sk[1], b = sk[2], dmg = sk[3];
        boolean moving = (jptr >= 0 && Math.hypot(jx - jbx, jy - jby) > 8 * s) || keyMove;
        if (!moving && (kind == 0 || kind == 2 || kind == 3 || kind == 4)) {
            int j = nearest(a + 2);
            if (j >= 0) prot = ang(vx[j] - px, vz[j] - pz);
        }
        float fx0 = (float) Math.sin(Math.toRadians(prot)), fz0 = (float) Math.cos(Math.toRadians(prot));
        cd[k] = sk[4];
        panim = 2 + k;
        atkT = k == 0 ? 0.42f : 0.75f;
        Color col = Heroes.ACC[h];
        switch (kind) {
            case 0:
            case 1:
                for (int j = 0; j < 10; j++) {
                    if (G.team(j) == myTeam || !G.alive[j]) continue;
                    float dx = vx[j] - px, dz = vz[j] - pz, d = (float) Math.sqrt(dx * dx + dz * dz);
                    boolean ok = d <= a + 0.7f;
                    if (kind == 0 && d > 1.3f && (dx * fx0 + dz * fz0) / d < -0.3f) ok = false;
                    if (ok) hit(j, dmg);
                }
                if (kind == 0) ring(px + fx0 * a * 0.5f, pz + fz0 * a * 0.5f, a * 0.7f, col, 0.25f);
                else ring(px, pz, a, col, 0.45f);
                break;
            case 2:
            case 4: {
                float len = a;
                for (int j = 0; j < 10; j++) {
                    if (G.team(j) == myTeam || !G.alive[j]) continue;
                    float dx = vx[j] - px, dz = vz[j] - pz;
                    float proj = dx * fx0 + dz * fz0, lat = Math.abs(dx * fz0 - dz * fx0);
                    if (proj > -0.5f && proj < len + 1f && lat <= b / 2 + 0.8f && dmg > 0) hit(j, dmg);
                }
                beam(px, pz, px + fx0 * len, pz + fz0 * len, kind == 2 ? b : b * 0.5f, col, kind == 2 ? 0.3f : 0.35f);
                if (kind == 2) { dashT = 0.22f; dvx = fx0 * len / 0.22f; dvz = fz0 * len / 0.22f; }
                break;
            }
            case 3: {
                int j = nearest(a);
                if (j >= 0) {
                    prot = ang(vx[j] - px, vz[j] - pz);
                    hit(j, dmg);
                    bolt(px, pz, 1.6f, vx[j], vz[j], 1.3f, col, 0.18f);
                } else bolt(px, pz, 1.6f, px + fx0 * a, pz + fz0 * a, 1.3f, col, 0.3f);
                break;
            }
            case 5: {
                int j = nearest(a);
                float tx, tz;
                if (j >= 0) { tx = vx[j]; tz = vz[j]; } else { tx = px + fx0 * a * 0.6f; tz = pz + fz0 * a * 0.6f; }
                bolt(tx, tz, 14, tx, tz, 0.5f, col, 0.35f);
                ring(tx, tz, b, col, 0.6f);
                for (int q = 0; q < 10; q++) {
                    if (G.team(q) == myTeam || !G.alive[q]) continue;
                    float dx = vx[q] - tx, dz = vz[q] - tz;
                    if (dx * dx + dz * dz <= (b + 0.6f) * (b + 0.6f)) hit(q, dmg);
                }
                break;
            }
            default:
                dashT = 0.2f; dvx = -fx0 * a / 0.2f; dvz = -fz0 * a / 0.2f;
                ring(px, pz, 2f, col, 0.3f);
        }
    }

    void update(float dt, boolean lost) {
        for (int k = 0; k < 4; k++) cd[k] = Math.max(0, cd[k] - dt);
        float kk = Math.min(1, dt * 14);
        for (int i = 0; i < 10; i++) {
            vx[i] += (G.x[i] - vx[i]) * kk;
            vz[i] += (G.z[i] - vz[i]) * kk;
            vr[i] = lerpAngle(vr[i], G.rot[i], kk);
        }
        if (G.alive[me] && G.started && !G.ended && !lost) {
            float mx = 0, mz = 0;
            keyMove = false;
            if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) mx -= 1;
            if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) mx += 1;
            if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) mz -= 1;
            if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) mz += 1;
            if (mx != 0 || mz != 0) {
                keyMove = true;
                float l = (float) Math.sqrt(mx * mx + mz * mz);
                mx /= l; mz /= l;
            }
            if (jptr >= 0) {
                float dx = jx - jbx, dy = jy - jby, d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d > 8 * s) {
                    float k = Math.min(1, d / (55 * s));
                    mx = dx / d * k;
                    mz = -dy / d * k;
                }
            }
            boolean moving = Math.abs(mx) + Math.abs(mz) > 0.12f;
            if (dashT > 0) {
                px += dvx * dt; pz += dvz * dt; dashT -= dt;
            } else if (moving) {
                float sp = Heroes.SPEED[G.hero[me]];
                px += mx * sp * dt; pz += mz * sp * dt;
                prot = lerpAngle(prot, ang(mx, mz), Math.min(1, dt * 16));
            }
            px = Math.max(-48, Math.min(48, px));
            pz = Math.max(-28, Math.min(28, pz));
            if (atkT > 0) atkT -= dt; else panim = moving ? 1 : 0;
            if (holdBasic && cd[0] <= 0) cast(0);
            sendT += dt;
            if (sendT > 0.05f) {
                sendT = 0;
                G.net.send("POS " + (Math.round(px * 10) / 10.0) + " " + (Math.round(pz * 10) / 10.0) + " " + (Math.round(prot * 10) / 10.0) + " " + panim);
            }
        }
        vx[me] = px; vz[me] = pz;
    }

    void updFx(Fx f, float dt) {
        f.t += dt;
        float p = Math.min(1, f.t / f.dur);
        switch (f.type) {
            case 0: {
                float r = f.r * (0.3f + 0.7f * p);
                f.mi.transform.setToTranslation(f.x0, 0.12f, f.z0).scale(r, 1, r);
                f.ba.opacity = 0.75f * (1 - p);
                break;
            }
            case 1: {
                float x = f.x0 + (f.x1 - f.x0) * p, z = f.z0 + (f.z1 - f.z0) * p, y = f.y0 + (f.y1 - f.y0) * p;
                f.mi.transform.setToTranslation(x, y, z).scale(0.8f, 0.8f, 0.8f);
                f.ba.opacity = 0.95f;
                break;
            }
            case 2: {
                float dx = f.x1 - f.x0, dz = f.z1 - f.z0, len = (float) Math.sqrt(dx * dx + dz * dz);
                f.mi.transform.setToTranslation((f.x0 + f.x1) / 2, 1.2f, (f.z0 + f.z1) / 2).rotate(Vector3.Y, ang(dx, dz)).scale(f.r, 0.35f, len);
                f.ba.opacity = 0.7f * (1 - p);
                break;
            }
            default: {
                float sz = 0.5f + 1.4f * p;
                f.mi.transform.setToTranslation(f.x0, 1.4f, f.z0).scale(sz, sz, sz);
                f.ba.opacity = 0.8f * (1 - p);
            }
        }
    }

    @Override
    public void render(float dt) {
        dt = Math.min(dt, 0.05f);
        t += dt;
        G.pump();
        boolean lost = G.net.state >= 3 && !G.ended;
        if ((G.ended || lost) && endAt == 0) endAt = System.currentTimeMillis();
        if (G.respawnPending) {
            px = G.x[me]; pz = G.z[me]; prot = G.rot[me];
            G.respawnPending = false;
        }
        update(dt, lost);

        cx += (px - cx) * Math.min(1, dt * 8);
        cz += (pz - cz) * Math.min(1, dt * 8);
        cam.position.set(cx, 19, cz + 14);
        cam.up.set(0, 1, 0);
        cam.lookAt(cx, 0, cz);
        cam.update();
        Gdx.gl.glClearColor(0.45f, 0.7f, 0.95f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        mb.begin(cam);
        world.render(mb, env, cam);
        for (int i = 0; i < 10; i++) {
            boolean al = G.alive[i];
            int an = al ? (i == me ? panim : G.anim[i]) : 6;
            rig[i].update(vx[i], vz[i], i == me ? prot : vr[i], an, t + i * 0.37f);
            mb.render(rig[i].mi, env);
            if (al) {
                ring[i].transform.setToTranslation(vx[i], 0.07f, vz[i]).scale(1.15f, 1, 1.15f);
                mb.render(ring[i], env);
            }
        }
        for (int i = fxs.size() - 1; i >= 0; i--) {
            Fx f = fxs.get(i);
            updFx(f, dt);
            if (f.t >= f.dur) { fxs.remove(i); continue; }
            mb.render(f.mi, env);
        }
        mb.end();
        hud(lost);
    }

    static Color tierColor(int tier) {
        switch (tier) {
            case 1: return Color.WHITE;
            case 2: return new Color(1f, 0.95f, 0.4f, 1);
            case 3: return new Color(1f, 0.65f, 0.2f, 1);
            case 4: return new Color(1f, 0.35f, 0.2f, 1);
            case 5: return new Color(1f, 0.3f, 0.85f, 1);
            default: return new Color(1f, 0.85f, 0.2f, 1);
        }
    }

    void hud(boolean lost) {
        long now = System.currentTimeMillis();
        int h = G.hero[me];
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < 10; i++) {
            hv[i] = false;
            if (!G.alive[i]) continue;
            tmp.set(vx[i], 3.0f * rig[i].scale + 0.3f, vz[i]);
            cam.project(tmp);
            if (tmp.z > 1) continue;
            hv[i] = true; hx[i] = tmp.x; hy[i] = tmp.y;
            float bw = 44 * s, frac = Math.max(0, Math.min(1f, G.hp[i] / (float) Heroes.HP[G.hero[i]]));
            sr.setColor(0, 0, 0, 0.65f);
            sr.rect(tmp.x - bw / 2 - s, tmp.y - s, bw + 2 * s, 7 * s);
            if (i == me) sr.setColor(0.3f, 1f, 0.4f, 1); else if (G.team(i) == 0) sr.setColor(0.3f, 0.6f, 1f, 1); else sr.setColor(1f, 0.3f, 0.3f, 1);
            sr.rect(tmp.x - bw / 2, tmp.y, bw * frac, 5 * s);
        }
        // minimap
        float mw = 150 * s, mh = 90 * s, m0x = 8 * s, m0y = H - 8 * s - mh;
        sr.setColor(0, 0, 0, 0.5f);
        sr.rect(m0x, m0y, mw, mh);
        sr.setColor(0.2f, 0.5f, 1f, 0.9f);
        sr.circle(m0x + 0.07f * mw, m0y + mh / 2, 5 * s);
        sr.setColor(1f, 0.3f, 0.3f, 0.9f);
        sr.circle(m0x + 0.93f * mw, m0y + mh / 2, 5 * s);
        for (int i = 0; i < 10; i++) {
            if (!G.alive[i]) continue;
            float mx = m0x + (vx[i] + 50) / 100f * mw, my = m0y + (1 - (vz[i] + 30) / 60f) * mh;
            if (i == me) sr.setColor(1, 1, 1, 1); else if (G.team(i) == 0) sr.setColor(0.3f, 0.6f, 1f, 1); else sr.setColor(1f, 0.3f, 0.3f, 1);
            sr.circle(mx, my, (i == me ? 4 : 3) * s);
        }
        // score panel
        float pw = 240 * s, ph = 30 * s, p0x = W / 2 - pw / 2, p0y = H - ph - 6 * s;
        sr.setColor(0.15f, 0.4f, 0.9f, 0.7f);
        sr.rect(p0x, p0y, pw / 2, ph);
        sr.setColor(0.9f, 0.2f, 0.2f, 0.7f);
        sr.rect(p0x + pw / 2, p0y, pw / 2, ph);
        // joystick
        float jr = 55 * s;
        if (jptr >= 0) {
            float dx = jx - jbx, dy = jy - jby, d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > jr) { dx = dx / d * jr; dy = dy / d * jr; }
            sr.setColor(1, 1, 1, 0.2f);
            sr.circle(jbx, jby, jr);
            sr.setColor(1, 1, 1, 0.5f);
            sr.circle(jbx + dx, jby + dy, 24 * s);
        } else {
            sr.setColor(1, 1, 1, 0.1f);
            sr.circle(100 * s, 90 * s, jr);
        }
        // buttons
        for (int k = 0; k < 4; k++) {
            if (k == 0) sr.setColor(0.85f, 0.25f, 0.2f, 0.8f);
            else { Color c = Heroes.MAIN[h]; sr.setColor(c.r, c.g, c.b, 0.85f); }
            sr.circle(bx[k], by[k], br[k]);
            sr.setColor(1, 1, 1, 0.35f);
            sr.circle(bx[k], by[k], br[k] * 0.86f);
            if (k == 0) sr.setColor(0.85f, 0.25f, 0.2f, 0.9f); else { Color c = Heroes.MAIN[h]; sr.setColor(c.r, c.g, c.b, 0.9f); }
            sr.circle(bx[k], by[k], br[k] * 0.8f);
            if (cd[k] > 0) {
                sr.setColor(0, 0, 0, 0.65f);
                sr.arc(bx[k], by[k], br[k], 90, 360f * Math.min(1, cd[k] / Heroes.S[h][k][4]));
            }
        }
        if (!G.alive[me] && !G.ended) { sr.setColor(0.2f, 0.2f, 0.25f, 0.45f); sr.rect(0, 0, W, H); }
        if (G.ended || lost) { sr.setColor(0, 0, 0, 0.65f); sr.rect(0, 0, W, H); }
        sr.end();

        sb.begin();
        for (int i = 0; i < 10; i++) {
            if (!hv[i]) continue;
            Color c = i == me ? new Color(0.5f, 1f, 0.6f, 1) : (G.team(i) == 0 ? new Color(0.6f, 0.8f, 1f, 1) : new Color(1f, 0.6f, 0.6f, 1));
            Ui.text(sb, G.names[i], hx[i], hy[i] + 12 * s, 0.6f, c, 1);
        }
        Ui.text(sb, String.valueOf(G.teamKills[0]), p0x + pw * 0.25f, p0y + ph / 2, 1.5f, Color.WHITE, 1);
        Ui.text(sb, String.valueOf(G.teamKills[1]), p0x + pw * 0.75f, p0y + ph / 2, 1.5f, Color.WHITE, 1);
        long sec = Math.max(0, (now - G.startAt) / 1000);
        Ui.text(sb, String.format("%02d:%02d", sec / 60, sec % 60), W / 2, p0y - 8 * s, 0.8f, Color.WHITE, 1);
        Ui.text(sb, "K " + G.kills[me] + "  D " + G.deaths[me], m0x, m0y - 8 * s, 0.7f, Color.WHITE, 0);
        for (int k = 0; k < 4; k++) {
            Ui.text(sb, k == 0 ? "ATK" : Heroes.ATK[h][k], bx[k], by[k], k == 0 ? 0.9f : 0.5f, Color.WHITE, 1);
            if (cd[k] > 0) Ui.text(sb, String.valueOf((int) Math.ceil(cd[k])), bx[k], by[k] + br[k] * 0.1f, 1.4f, Color.YELLOW, 1);
        }
        int fi = 0;
        for (int i = G.feed.size() - 1; i >= 0; i--) {
            G.Msg m = G.feed.get(i);
            if (now - m.at > 6000) continue;
            Ui.text(sb, m.text, W - 8 * s, H - 14 * s - fi * 16 * s, 0.8f, m.team == 0 ? new Color(0.6f, 0.8f, 1f, 1) : new Color(1f, 0.6f, 0.6f, 1), 2);
            fi++;
        }
        if (!G.announce.isEmpty()) {
            G.Msg m = G.announce.get(G.announce.size() - 1);
            float age = (now - m.at) / 1000f;
            if (age < 2.6f) {
                float mul = 1.5f + 0.35f * m.tier;
                if (age < 0.2f) mul *= 0.5f + 2.5f * age;
                Color c = tierColor(m.tier).cpy();
                if (age > 2.0f) c.a = Math.max(0, 1 - (age - 2.0f) / 0.6f);
                Ui.text(sb, m.text, W / 2, H * 0.72f, mul, c, 1);
                Color sc2 = (m.team == 0 ? new Color(0.6f, 0.8f, 1f, 1) : new Color(1f, 0.6f, 0.6f, 1));
                sc2.a = c.a;
                Ui.text(sb, m.sub, W / 2, H * 0.72f - (18 + 6 * m.tier) * s, 0.9f, sc2, 1);
            }
        }
        if (!G.alive[me] && !G.ended) Ui.text(sb, "YOU DIED - respawning...", W / 2, H * 0.5f, 1.6f, Color.WHITE, 1);
        if (!G.started) Ui.text(sb, "Waiting for match...", W / 2, H / 2, 1.5f, Color.WHITE, 1);
        if (G.ended) {
            boolean win = G.winner == myTeam;
            Ui.text(sb, win ? "VICTORY" : "DEFEAT", W / 2, H * 0.62f, 4f, win ? new Color(1f, 0.85f, 0.2f, 1) : new Color(1f, 0.35f, 0.3f, 1), 1);
            String rk = G.matchMode.equals("rank") ? "Rank " + (G.rankChange >= 0 ? "+" : "") + G.rankChange + " pts  (" + G.tier(G.rank) + " " + G.rank + ")" : "Classic match";
            Ui.text(sb, rk, W / 2, H * 0.48f, 1.3f, Color.WHITE, 1);
            Ui.text(sb, "Kills " + G.kills[me] + "   Deaths " + G.deaths[me], W / 2, H * 0.4f, 1.1f, Color.LIGHT_GRAY, 1);
            Ui.text(sb, "Tap to continue", W / 2, H * 0.28f, 1.0f, Color.YELLOW, 1);
        } else if (lost) {
            Ui.text(sb, "Connection lost", W / 2, H * 0.55f, 2.5f, new Color(1f, 0.4f, 0.3f, 1), 1);
            Ui.text(sb, "Tap to continue", W / 2, H * 0.4f, 1.0f, Color.YELLOW, 1);
        }
        sb.end();
    }

    @Override
    public void resize(int w, int h) {
        W = w; H = h; s = H / 360f;
        cam.viewportWidth = w;
        cam.viewportHeight = h;
        sb.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        layout();
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
        world.dispose(); mb.dispose(); sr.dispose(); sb.dispose();
        ringM.dispose(); sphereM.dispose(); boxM.dispose();
    }
}
