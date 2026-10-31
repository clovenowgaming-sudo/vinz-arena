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

import net.mgsx.gltf.scene3d.lights.DirectionalLightEx;

import java.util.ArrayList;

/** Render-only match screen: the server simulates everything, this sends input and draws snapshots. */
public class MatchScreen extends ScreenAdapter {
    static class Fx {
        ModelInstance mi; BlendingAttribute ba; int type;
        float t, dur, x0, z0, x1, z1, y0, y1, r;
    }

    final int me = G.myIdx, myTeam = G.team(G.myIdx);
    final World world = new World();
    ModelBatch mbA, mbP;
    final Environment env = new Environment(), envP = new Environment();
    final PerspectiveCamera cam;
    final Actor3D[] hero = new Actor3D[10];
    final ModelInstance[] ring = new ModelInstance[10];
    ModelInstance targetRing;
    final Model ringM, sphereM, boxM;
    final ShapeRenderer sr = new ShapeRenderer();
    final SpriteBatch sb = new SpriteBatch();
    final ArrayList<Fx> fxs = new ArrayList<Fx>();
    final ArrayList<float[]> nums = new ArrayList<float[]>();
    final Vector3 tmp = new Vector3();
    float W, H, s;
    float px, pz, prot, cx, cz, t, mvT, smx, smz;
    final float[] vx = new float[10], vz = new float[10], vr = new float[10];
    float jx, jy, jbx, jby;
    int jptr = -1, bptr = -1;
    boolean keyMove, prevAlive = true;
    final float[] bx = new float[4], by = new float[4], br = new float[4];
    long endAt;
    int annSeen;
    float tgtX, tgtZ;
    boolean hasTgt;
    final java.util.ArrayList<float[]> proj = new java.util.ArrayList<float[]>(); // screen positions for hp bars

    public MatchScreen() {
        W = Gdx.graphics.getWidth();
        H = Gdx.graphics.getHeight();
        s = H / 360f;
        mbA = new ModelBatch();
        mbP = Actor3D.modelsOk ? Actor3D.pbrBatch() : null;
        env.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.6f, 0.6f, 0.65f, 1f));
        env.add(new DirectionalLight().set(0.9f, 0.88f, 0.8f, -0.5f, -1f, -0.4f));
        envP.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.75f, 0.75f, 0.8f, 1f));
        DirectionalLightEx l1 = new DirectionalLightEx();
        l1.direction.set(-0.5f, -1f, -0.4f).nor();
        l1.color.set(1f, 0.95f, 0.85f, 1f);
        l1.intensity = 3f;
        envP.add(l1);
        DirectionalLightEx l2 = new DirectionalLightEx();
        l2.direction.set(0.6f, -0.4f, 0.7f).nor();
        l2.color.set(0.7f, 0.8f, 1f, 1f);
        l2.intensity = 1.2f;
        envP.add(l2);
        cam = new PerspectiveCamera(45, W, H);
        cam.near = 1;
        cam.far = 160;
        ModelBuilder b = new ModelBuilder();
        ringM = b.createCylinder(2, 0.1f, 2, 24, new Material(), Usage.Position | Usage.Normal);
        sphereM = b.createSphere(1, 1, 1, 12, 10, new Material(), Usage.Position | Usage.Normal);
        boxM = b.createBox(1, 1, 1, new Material(), Usage.Position | Usage.Normal);
        for (int i = 0; i < 10; i++) {
            hero[i] = Actor3D.hero(G.hero[i]);
            vx[i] = G.x[i]; vz[i] = G.z[i]; vr[i] = G.rot[i];
            Color c = i == me ? new Color(0.3f, 1f, 0.4f, 1) : (G.team(i) == 0 ? World.BLUE : World.RED);
            ring[i] = ringInst(c, 0.65f);
        }
        targetRing = ringInst(new Color(1f, 0.15f, 0.1f, 1), 0.8f);
        px = cx = G.x[me]; pz = cz = G.z[me]; prot = G.rot[me];
        layout();
    }

    ModelInstance ringInst(Color c, float a) {
        ModelInstance mi = new ModelInstance(ringM);
        Material m = mi.materials.get(0);
        m.set(ColorAttribute.createDiffuse(c));
        m.set(ColorAttribute.createEmissive(c));
        m.set(new BlendingAttribute(true, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, a));
        return mi;
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

    static String f2(float v) { return String.valueOf(Math.round(v * 100) / 100.0); }

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
                        if (k == 0) { bptr = ptr; G.net.send("ATK 1"); }
                        G.net.send("CAST " + k);
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
                if (ptr == bptr) { bptr = -1; G.net.send("ATK 0"); }
                return true;
            }
            public boolean keyDown(int key) {
                if (key == Input.Keys.J) { G.net.send("ATK 1"); G.net.send("CAST 0"); }
                if (key == Input.Keys.U) G.net.send("CAST 1");
                if (key == Input.Keys.I) G.net.send("CAST 2");
                if (key == Input.Keys.O) G.net.send("CAST 3");
                return true;
            }
            public boolean keyUp(int key) {
                if (key == Input.Keys.J) G.net.send("ATK 0");
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

    // ------------------------------------------------------------ effects
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

    void handleFx(float[] f) {
        int type = (int) f[0], hero = (int) f[6], sk = (int) f[7];
        Color c = (sk >= 8) ? new Color(1f, 0.7f, 0.3f, 1) : Heroes.ACC[Math.max(0, Math.min(3, hero))];
        float x0 = f[1], z0 = f[2], x1 = f[3], z1 = f[4], r = f[5];
        float d = (float) Math.hypot(x0 - px, z0 - pz);
        float vol = Math.max(0.12f, 1f - d / 32f);
        if (type == 0 && sk == 9) {
            Fx a = mk(ringM, new Color(1f, 0.5f, 0.2f, 1), 0, 0.9f);
            a.x0 = x0; a.z0 = z0; a.r = 7;
            Sfx.play("tower", vol);
            return;
        }
        if (type == 0) { Fx a = mk(ringM, c, 0, r > 5 ? 0.45f : 0.28f); a.x0 = x0; a.z0 = z0; a.r = Math.max(1.2f, r); }
        else if (type == 1) {
            Fx a = mk(sphereM, c, 1, 0.2f);
            a.x0 = x0; a.z0 = z0; a.x1 = x1; a.z1 = z1;
            a.y0 = sk == 9 ? 6f : (sk == 8 ? 1.2f : 1.6f); a.y1 = 1.3f;
        } else if (type == 2) {
            Fx a = mk(boxM, c, 2, 0.32f);
            a.x0 = x0; a.z0 = z0; a.x1 = x1; a.z1 = z1; a.r = Math.max(0.4f, r);
        } else if (type == 4) {
            Fx a = mk(sphereM, c, 1, 0.35f);
            a.x0 = x0; a.z0 = z0; a.x1 = x0; a.z1 = z0; a.y0 = 14; a.y1 = 0.5f;
            Fx b = mk(ringM, c, 0, 0.6f);
            b.x0 = x0; b.z0 = z0; b.r = r;
        }
        if (sk == 0) Sfx.play("atk", vol * 0.7f);
        else if (sk >= 1 && sk <= 3) Sfx.play("s" + sk, vol);
        else if (sk >= 8) Sfx.play("shot", vol * 0.3f);
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
            default: {
                float dx = f.x1 - f.x0, dz = f.z1 - f.z0, len = (float) Math.sqrt(dx * dx + dz * dz);
                f.mi.transform.setToTranslation((f.x0 + f.x1) / 2, 1.2f, (f.z0 + f.z1) / 2).rotate(Vector3.Y, ang(dx, dz)).scale(f.r, 0.35f, len);
                f.ba.opacity = 0.7f * (1 - p);
            }
        }
    }

    // ------------------------------------------------------------ update
    void update(float dt, boolean lost) {
        float k14 = Math.min(1, dt * 14);
        for (int i = 0; i < 10; i++) {
            vx[i] += (G.x[i] - vx[i]) * k14;
            vz[i] += (G.z[i] - vz[i]) * k14;
            vr[i] = lerpAngle(vr[i], G.rot[i], k14);
        }
        for (G.Ent e : G.ents.values()) {
            e.vx += (e.x - e.vx) * k14;
            e.vz += (e.z - e.vz) * k14;
            e.vr = lerpAngle(e.vr, e.rot, k14);
        }
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
        boolean active = G.alive[me] && G.started && !G.ended && !lost;
        if (!active) { mx = 0; mz = 0; }
        // tell the server our input (it does all the simulation)
        mvT += dt;
        boolean ch = Math.abs(mx - smx) > 0.04f || Math.abs(mz - smz) > 0.04f;
        if (G.started && (ch || (mvT > 0.2f && (mx != 0 || mz != 0)) || mvT > 0.4f)) {
            G.net.send("MV " + f2(mx) + " " + f2(mz));
            smx = mx; smz = mz; mvT = 0;
        }
        // light client prediction for our own hero, corrected by the server snapshot
        float sp = Heroes.SPEED[G.hero[me]];
        px += mx * sp * dt; pz += mz * sp * dt;
        float ex = G.x[me] - px, ez = G.z[me] - pz;
        if (ex * ex + ez * ez > 25) { px = G.x[me]; pz = G.z[me]; }
        else { float k = Math.min(1, dt * 6); px += ex * k; pz += ez * k; }
        if (Math.abs(mx) + Math.abs(mz) > 0.12f) prot = lerpAngle(prot, ang(mx, mz), Math.min(1, dt * 16));
        else prot = lerpAngle(prot, G.rot[me], Math.min(1, dt * 10));
        vx[me] = px; vz[me] = pz;
        // auto-target marker: nearest enemy in reach
        hasTgt = false;
        float bd = 10 * 10;
        for (int i = 0; i < 10; i++) {
            if (G.team(i) == myTeam || !G.alive[i]) continue;
            float dx = vx[i] - px, dz = vz[i] - pz, d = dx * dx + dz * dz;
            if (d < bd) { bd = d; tgtX = vx[i]; tgtZ = vz[i]; hasTgt = true; }
        }
        for (G.Ent e : G.ents.values()) {
            if (e.team == myTeam) continue;
            float dx = e.vx - px, dz = e.vz - pz, d = dx * dx + dz * dz;
            if (d < bd) { bd = d; tgtX = e.vx; tgtZ = e.vz; hasTgt = true; }
        }
        // events from the server
        for (float[] f : G.fxQ) handleFx(f);
        G.fxQ.clear();
        for (float[] d : G.dmgQ) {
            nums.add(new float[]{d[0], d[1], d[2], 0, d[3] != myTeam ? 1 : 0});
            float dd = (float) Math.hypot(d[0] - px, d[1] - pz);
            if (dd < 25) Sfx.play("hit", d[3] != myTeam ? 0.35f : 0.2f);
        }
        G.dmgQ.clear();
        for (int i = nums.size() - 1; i >= 0; i--) {
            nums.get(i)[3] += dt;
            if (nums.get(i)[3] > 1f) nums.remove(i);
        }
        while (annSeen < G.announce.size()) {
            G.Msg m = G.announce.get(annSeen++);
            if (m.kill) Sfx.play("k" + Math.max(1, Math.min(6, m.tier)));
            else if (m.tier == 4) { } else Sfx.play("buff");
        }
        boolean al = G.alive[me];
        if (prevAlive && !al) Sfx.play("death");
        if (!prevAlive && al) Sfx.play("respawn");
        prevAlive = al;
    }

    @Override
    public void render(float dt) {
        dt = Math.min(dt, 0.05f);
        t += dt;
        G.pump();
        boolean lost = G.net.state >= 3 && !G.ended;
        if ((G.ended || lost) && endAt == 0) {
            endAt = System.currentTimeMillis();
            if (G.ended) Sfx.play(G.winner == myTeam ? "win" : "lose");
        }
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

        // pass A: world + towers + procedural actors
        ArrayList<Actor3D> pbrList = new ArrayList<Actor3D>();
        ArrayList<ModelInstance> pbrProps = new ArrayList<ModelInstance>();
        mbA.begin(cam);
        world.render(mbA, env, cam);
        for (int i = 0; i < 10; i++) {
            boolean al = G.alive[i];
            int an = al ? G.anim[i] : 6;
            hero[i].update(vx[i], vz[i], i == me ? prot : vr[i], an, t + i * 0.37f, dt);
            if (hero[i].pbr) pbrList.add(hero[i]); else mbA.render(hero[i].mi, env);
        }
        for (G.Ent e : G.ents.values()) {
            if (e.kind >= 4) { drawStatic(e, pbrProps); continue; }
            if (e.actor == null) {
                e.actor = e.kind == 0 ? Actor3D.minion(e.team, false) : e.kind == 1 ? Actor3D.minion(e.team, true) : Actor3D.monster(e.kind == 3);
            }
            e.actor.update(e.vx, e.vz, e.vr, e.anim == 0 ? 0 : (e.anim == 1 ? 1 : 2), t + e.id * 0.3f, dt);
            if (e.actor.pbr) pbrList.add(e.actor); else mbA.render(e.actor.mi, env);
        }
        mbA.end();
        // pass B: glTF characters (PBR shader)
        if (mbP != null && (!pbrList.isEmpty() || !pbrProps.isEmpty())) {
            mbP.begin(cam);
            for (Actor3D a : pbrList) mbP.render(a.mi, envP);
            for (ModelInstance pm : pbrProps) mbP.render(pm, envP);
            mbP.end();
        }
        // pass C: transparent rings + effects
        mbA.begin(cam);
        for (int i = 0; i < 10; i++) {
            if (!G.alive[i]) continue;
            ring[i].transform.setToTranslation(vx[i], 0.07f, vz[i]).scale(1.15f, 1, 1.15f);
            mbA.render(ring[i], env);
        }
        if (hasTgt && G.alive[me]) {
            float pulse = 1.3f + 0.1f * (float) Math.sin(t * 8);
            targetRing.transform.setToTranslation(tgtX, 0.09f, tgtZ).scale(pulse, 1, pulse);
            mbA.render(targetRing, env);
        }
        for (int i = fxs.size() - 1; i >= 0; i--) {
            Fx f = fxs.get(i);
            updFx(f, dt);
            if (f.t >= f.dur) { fxs.remove(i); continue; }
            mbA.render(f.mi, env);
        }
        mbA.end();
        hud(lost);
    }

    void drawStatic(G.Ent e, ArrayList<ModelInstance> pbrProps) {
        boolean tower = e.kind == 4;
        Color tc = e.team == 0 ? World.BLUE : World.RED;
        if (e.parts == null) {
            ModelInstance prop = Actor3D.prop(tower ? "tower" : "castle", e.team);
            if (tower) {
                if (prop != null) prop.transform.setToTranslation(e.x, 0, e.z).scale(3.1f, 3.1f, 3.1f);
                e.parts = new ModelInstance[]{prop, new ModelInstance(e.team == 0 ? world.tTop0 : world.tTop1), ringInst(tc, 0.6f),
                        new ModelInstance(world.tBase), new ModelInstance(world.tBody)};
                e.parts[3].transform.setToTranslation(e.x, 0.3f, e.z);
                e.parts[4].transform.setToTranslation(e.x, 2.8f, e.z);
            } else {
                if (prop != null) prop.transform.setToTranslation(e.x, 0, e.z).scale(3.4f, 3.4f, 3.4f);
                e.parts = new ModelInstance[]{prop, new ModelInstance(e.team == 0 ? world.cry0 : world.cry1), ringInst(tc, 0.5f)};
            }
        }
        boolean hasProp = e.parts[0] != null;
        float top = tower ? (hasProp ? 2.49f * 3.1f : 5.9f) : (hasProp ? 3.98f * 3.4f : 3.5f);
        float pulse = 1f + 0.12f * (float) Math.sin(t * 3 + e.id);
        float bob = (float) Math.sin(t * 2 + e.id) * (tower ? 0.25f : 0.5f);
        ModelInstance orb = e.parts[1];
        if (tower) orb.transform.setToTranslation(e.x, top + 1.2f + bob, e.z).scale(pulse, pulse, pulse);
        else orb.transform.setToTranslation(e.x, top + 3.4f + bob, e.z).rotate(Vector3.Y, t * 40).scale(pulse, pulse, pulse);
        float r = (tower ? 3.6f : 8.5f) * (1f + 0.04f * (float) Math.sin(t * 2 + e.id));
        e.parts[2].transform.setToTranslation(e.x, 0.1f, e.z).scale(r, 1, r);
        if (hasProp) pbrProps.add(e.parts[0]);
        else if (tower) { mbA.render(e.parts[3], env); mbA.render(e.parts[4], env); }
        mbA.render(orb, env);
        mbA.render(e.parts[2], env);
    }

    // ------------------------------------------------------------ HUD (Mobile-Legends style layout)
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

    float[] bar(float wx, float wy, float wz) {
        tmp.set(wx, wy, wz);
        cam.project(tmp);
        return new float[]{tmp.x, tmp.y, tmp.z};
    }

    void hud(boolean lost) {
        long now = System.currentTimeMillis();
        int h = G.hero[me];
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        proj.clear();
        sr.begin(ShapeRenderer.ShapeType.Filled);
        // hero bars
        for (int i = 0; i < 10; i++) {
            if (!G.alive[i]) { proj.add(null); continue; }
            float[] p = bar(vx[i], 3.1f, vz[i]);
            if (p[2] > 1) { proj.add(null); continue; }
            proj.add(p);
            float bw = 46 * s, frac = Math.max(0, Math.min(1f, G.hp[i] / (float) Heroes.HP[G.hero[i]]));
            sr.setColor(0, 0, 0, 0.7f);
            sr.rect(p[0] - bw / 2 - s, p[1] - s, bw + 2 * s, 7 * s);
            if (i == me) sr.setColor(0.3f, 1f, 0.4f, 1); else if (G.team(i) == 0) sr.setColor(0.3f, 0.6f, 1f, 1); else sr.setColor(1f, 0.3f, 0.3f, 1);
            sr.rect(p[0] - bw / 2, p[1], bw * frac, 5 * s);
        }
        // minion / monster / tower bars
        for (G.Ent e : G.ents.values()) {
            float wy = e.kind <= 1 ? 2.2f : (e.kind <= 3 ? (e.kind == 3 ? 5f : 3f) : (e.kind == 4 ? 9.8f : 17.5f));
            float[] p = bar(e.vx, wy, e.vz);
            if (p[2] > 1) continue;
            float bw = (e.kind <= 1 ? 22 : (e.kind <= 3 ? 34 : 56)) * s, frac = Math.max(0, Math.min(1f, e.hp / (float) Math.max(1, e.maxhp)));
            sr.setColor(0, 0, 0, 0.65f);
            sr.rect(p[0] - bw / 2 - s * 0.7f, p[1] - s * 0.7f, bw + 1.4f * s, 4.4f * s);
            if (e.team == 2) sr.setColor(1f, 0.8f, 0.2f, 1); else if (e.team == myTeam) sr.setColor(0.3f, 0.6f, 1f, 1); else sr.setColor(1f, 0.3f, 0.3f, 1);
            sr.rect(p[0] - bw / 2, p[1], bw * frac, 3 * s);
        }
        // minimap
        float mw = 150 * s, mh = 90 * s, m0x = 8 * s, m0y = H - 8 * s - mh;
        sr.setColor(0, 0, 0, 0.55f);
        sr.rect(m0x, m0y, mw, mh);
        sr.setColor(0.2f, 0.45f, 0.2f, 0.55f);
        sr.rect(m0x + 2 * s, m0y + 2 * s, mw - 4 * s, mh - 4 * s);
        for (G.Ent e : G.ents.values()) {
            float mx = m0x + (e.vx + 50) / 100f * mw, my = m0y + (1 - (e.vz + 30) / 60f) * mh;
            if (e.kind <= 1) { sr.setColor(e.team == 0 ? 0.4f : 1f, 0.6f, e.team == 0 ? 1f : 0.4f, 0.9f); sr.circle(mx, my, 1.4f * s); }
            else if (e.kind <= 3) { sr.setColor(1f, 0.85f, 0.2f, 0.9f); sr.circle(mx, my, 2f * s); }
            else { sr.setColor(e.team == 0 ? 0.3f : 1f, 0.5f, e.team == 0 ? 1f : 0.3f, 1f); sr.rect(mx - 2.5f * s, my - 2.5f * s, 5 * s, 5 * s); }
        }
        for (int i = 0; i < 10; i++) {
            if (!G.alive[i]) continue;
            float mx = m0x + (vx[i] + 50) / 100f * mw, my = m0y + (1 - (vz[i] + 30) / 60f) * mh;
            if (i == me) sr.setColor(1, 1, 1, 1); else if (G.team(i) == 0) sr.setColor(0.3f, 0.7f, 1f, 1); else sr.setColor(1f, 0.3f, 0.3f, 1);
            sr.circle(mx, my, (i == me ? 4 : 3) * s);
        }
        // score panel
        float pw = 250 * s, ph = 28 * s, p0x = W / 2 - pw / 2, p0y = H - ph - 6 * s;
        sr.setColor(0.1f, 0.35f, 0.9f, 0.8f);
        sr.rect(p0x, p0y, pw * 0.4f, ph);
        sr.setColor(0.05f, 0.05f, 0.1f, 0.85f);
        sr.rect(p0x + pw * 0.4f, p0y, pw * 0.2f, ph);
        sr.setColor(0.9f, 0.15f, 0.15f, 0.8f);
        sr.rect(p0x + pw * 0.6f, p0y, pw * 0.4f, ph);
        // my hp bar (bottom center)
        float hw = 210 * s, hx0 = W / 2 - hw / 2, hy0 = 10 * s;
        float hf = Math.max(0, Math.min(1, G.hp[me] / (float) Heroes.HP[h]));
        sr.setColor(0, 0, 0, 0.7f);
        sr.rect(hx0 - 2 * s, hy0 - 2 * s, hw + 4 * s, 14 * s);
        sr.setColor(0.25f, 0.9f, 0.35f, 1);
        sr.rect(hx0, hy0, hw * hf, 10 * s);
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
            sr.setColor(1, 1, 1, 0.18f);
            sr.circle(100 * s, 90 * s, 22 * s);
        }
        // buttons
        for (int k = 0; k < 4; k++) {
            Color c = k == 0 ? new Color(0.85f, 0.25f, 0.2f, 1) : Heroes.MAIN[h];
            sr.setColor(1f, 0.85f, 0.4f, 0.9f);
            sr.circle(bx[k], by[k], br[k]);
            sr.setColor(c.r * 0.55f, c.g * 0.55f, c.b * 0.55f, 0.95f);
            sr.circle(bx[k], by[k], br[k] * 0.92f);
            sr.setColor(c.r, c.g, c.b, 0.95f);
            sr.circle(bx[k], by[k], br[k] * 0.78f);
            if (G.cd[k] > 0.05f) {
                sr.setColor(0, 0, 0, 0.7f);
                sr.arc(bx[k], by[k], br[k] * 0.92f, 90, 360f * Math.min(1, G.cd[k] / Heroes.S[h][k][4]));
            }
        }
        if (!G.alive[me] && !G.ended) { sr.setColor(0.2f, 0.2f, 0.25f, 0.45f); sr.rect(0, 0, W, H); }
        if (G.ended || lost) { sr.setColor(0, 0, 0, 0.65f); sr.rect(0, 0, W, H); }
        sr.end();

        sb.begin();
        for (int i = 0; i < 10; i++) {
            float[] p = i < proj.size() ? proj.get(i) : null;
            if (p == null) continue;
            Color c = i == me ? new Color(0.5f, 1f, 0.6f, 1) : (G.team(i) == 0 ? new Color(0.6f, 0.8f, 1f, 1) : new Color(1f, 0.6f, 0.6f, 1));
            Ui.text(sb, G.names[i], p[0], p[1] + 12 * s, 0.6f, c, 1);
        }
        for (float[] n : nums) {
            float[] p = bar(n[0], 3.0f + n[3] * 2.5f, n[1]);
            if (p[2] > 1) continue;
            Color c = n[4] > 0 ? new Color(1f, 0.95f, 0.4f, 1f - n[3]) : new Color(1f, 0.3f, 0.3f, 1f - n[3]);
            Ui.text(sb, String.valueOf((int) n[2]), p[0], p[1], 0.9f + (n[2] > 150 ? 0.3f : 0), c, 1);
        }
        Ui.text(sb, String.valueOf(G.teamKills[0]), p0x + pw * 0.2f, p0y + ph / 2, 1.5f, Color.WHITE, 1);
        Ui.text(sb, String.valueOf(G.teamKills[1]), p0x + pw * 0.8f, p0y + ph / 2, 1.5f, Color.WHITE, 1);
        long sec = Math.max(0, (now - G.startAt) / 1000);
        Ui.text(sb, String.format("%02d:%02d", sec / 60, sec % 60), W / 2, p0y + ph / 2, 0.9f, Color.WHITE, 1);
        Ui.text(sb, "K " + G.kills[me] + "  D " + G.deaths[me], m0x, m0y - 8 * s, 0.7f, Color.WHITE, 0);
        Ui.text(sb, Gdx.graphics.getFramesPerSecond() + " FPS", W - 8 * s, H - 8 * s, 0.6f, Color.LIGHT_GRAY, 2);
        Ui.text(sb, G.hp[me] + " / " + Heroes.HP[h], W / 2, hy0 + 5 * s, 0.6f, Color.WHITE, 1);
        for (int k = 0; k < 4; k++) {
            Ui.text(sb, k == 0 ? "ATK" : Heroes.ATK[h][k], bx[k], by[k], k == 0 ? 0.9f : 0.5f, Color.WHITE, 1);
            if (G.cd[k] > 0.05f) Ui.text(sb, String.valueOf((int) Math.ceil(G.cd[k])), bx[k], by[k] + br[k] * 0.1f, 1.4f, Color.YELLOW, 1);
        }
        int fi = 0;
        for (int i = G.feed.size() - 1; i >= 0; i--) {
            G.Msg m = G.feed.get(i);
            if (now - m.at > 6000) continue;
            Ui.text(sb, m.text, W - 8 * s, H - 26 * s - fi * 16 * s, 0.8f, m.team == 0 ? new Color(0.6f, 0.8f, 1f, 1) : new Color(1f, 0.6f, 0.6f, 1), 2);
            fi++;
        }
        if (!G.announce.isEmpty()) {
            G.Msg m = G.announce.get(G.announce.size() - 1);
            float age = (now - m.at) / 1000f;
            if (age < 2.6f) {
                float mul = m.kill ? 1.5f + 0.35f * m.tier : 1.4f;
                if (age < 0.2f) mul *= 0.5f + 2.5f * age;
                Color c = (m.kill ? tierColor(m.tier) : new Color(1f, 0.85f, 0.3f, 1)).cpy();
                if (age > 2.0f) c.a = Math.max(0, 1 - (age - 2.0f) / 0.6f);
                Ui.text(sb, m.text, W / 2, H * 0.72f, mul, c, 1);
                if (m.kill) {
                    Color sc2 = (m.team == 0 ? new Color(0.6f, 0.8f, 1f, 1) : new Color(1f, 0.6f, 0.6f, 1));
                    sc2.a = c.a;
                    Ui.text(sb, m.sub, W / 2, H * 0.72f - (18 + 6 * m.tier) * s, 0.9f, sc2, 1);
                }
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
        G.net.send("MV 0 0");
        G.net.send("ATK 0");
        world.dispose(); mbA.dispose(); if (mbP != null) mbP.dispose(); sr.dispose(); sb.dispose();
        ringM.dispose(); sphereM.dispose(); boxM.dispose();
    }
}
