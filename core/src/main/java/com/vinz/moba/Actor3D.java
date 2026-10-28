package com.vinz.moba;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;

import net.mgsx.gltf.loaders.glb.GLBLoader;
import net.mgsx.gltf.scene3d.attributes.PBRColorAttribute;
import net.mgsx.gltf.scene3d.scene.SceneAsset;
import net.mgsx.gltf.scene3d.shaders.PBRShaderProvider;

import java.util.HashMap;

/** A character in the world: a free glTF model (PBR) with skeletal animation, or a procedural fallback. */
public class Actor3D {
    // ---------------------------------------------------------------- model library
    static final HashMap<String, SceneAsset> assets = new HashMap<String, SceneAsset>();
    static int maxBones = 12;
    public static boolean modelsOk;
    public static String loadError = "";
    // known standing height (in model units) of each file
    static final HashMap<String, Float> HEIGHT = new HashMap<String, Float>();
    static {
        HEIGHT.put("soldier", 1.83f);
        HEIGHT.put("xbot", 1.81f);
        HEIGHT.put("robot", 3.8f);
        HEIGHT.put("fox", 79f);
    }

    public static void loadModels() {
        try {
            String[] names = {"soldier", "xbot", "robot", "fox"};
            for (String n : names) {
                SceneAsset a = new GLBLoader().load(Gdx.files.internal("models/" + n + ".glb"));
                assets.put(n, a);
                maxBones = Math.max(maxBones, a.maxBones);
            }
            modelsOk = true;
        } catch (Throwable t) {
            modelsOk = false;
            loadError = String.valueOf(t);
        }
    }

    public static ModelBatch pbrBatch() { return new ModelBatch(PBRShaderProvider.createDefault(maxBones)); }

    // ---------------------------------------------------------------- instance
    public ModelInstance mi;
    public boolean pbr;
    public float scale = 1;
    Heroes.Rig rig;                // procedural fallback for heroes
    AnimationController ac;
    String idle, walk, atk, death, cur = "";
    float atkSpeed = 1.6f;
    boolean fallbackBlob;

    Actor3D() { }

    static Animation find(ModelInstance mi, String... names) {
        for (String n : names)
            for (Animation a : mi.animations) if (a.id.equalsIgnoreCase(n)) return a;
        for (String n : names)
            for (Animation a : mi.animations) if (a.id.toLowerCase().contains(n.toLowerCase())) return a;
        return null;
    }

    static String id(Animation a) { return a == null ? null : a.id; }

    static Actor3D gltf(String file, float targetH, Color tint, String[] idle, String[] walk, String[] atk, String[] death, float atkSpeed) {
        SceneAsset a = assets.get(file);
        if (a == null) return null;
        Actor3D r = new Actor3D();
        r.mi = new ModelInstance(a.scene.model);
        r.pbr = true;
        r.scale = targetH / HEIGHT.get(file);
        if (tint != null) {
            for (Material m : r.mi.materials) {
                PBRColorAttribute at = (PBRColorAttribute) m.get(PBRColorAttribute.BaseColorFactor);
                if (at != null) at.color.mul(tint);
                else m.set(PBRColorAttribute.createBaseColorFactor(tint));
            }
        }
        r.ac = new AnimationController(r.mi);
        r.idle = id(find(r.mi, idle));
        r.walk = id(find(r.mi, walk));
        r.atk = id(find(r.mi, atk));
        r.death = id(find(r.mi, death));
        r.atkSpeed = atkSpeed;
        return r;
    }

    public static Actor3D hero(int h) {
        Actor3D a = null;
        if (modelsOk) {
            switch (h) {
                case 0: a = gltf("soldier", 2.5f, new Color(1f, 0.72f, 0.72f, 1), new String[]{"idle"}, new String[]{"run"}, new String[]{"run"}, new String[]{"none"}, 2.2f); break;
                case 1: a = gltf("xbot", 2.4f, new Color(0.75f, 0.55f, 1f, 1), new String[]{"idle"}, new String[]{"run"}, new String[]{"run"}, new String[]{"none"}, 2.2f); break;
                case 2: a = gltf("xbot", 2.4f, new Color(0.55f, 1f, 0.65f, 1), new String[]{"idle"}, new String[]{"run"}, new String[]{"run"}, new String[]{"none"}, 2.2f); break;
                default: a = gltf("robot", 3.3f, new Color(0.7f, 0.82f, 1f, 1), new String[]{"idle"}, new String[]{"running"}, new String[]{"punch"}, new String[]{"death"}, 1.5f);
            }
        }
        if (a == null) {
            a = new Actor3D();
            a.rig = new Heroes.Rig(h);
            a.mi = a.rig.mi;
            a.scale = a.rig.scale;
        }
        return a;
    }

    public static Actor3D minion(int team, boolean ranged) {
        Color tint = team == 0 ? new Color(0.45f, 0.7f, 1f, 1) : new Color(1f, 0.45f, 0.45f, 1);
        Actor3D a = modelsOk ? gltf("robot", ranged ? 1.5f : 1.7f, tint, new String[]{"idle"}, new String[]{"walking"}, new String[]{"punch"}, new String[]{"none"}, 1.6f) : null;
        return a != null ? a : blob(tint, 0.9f);
    }

    public static Actor3D monster(boolean big) {
        Actor3D a = modelsOk ? gltf("fox", big ? 4.2f : 2.4f, big ? new Color(1f, 0.6f, 0.3f, 1) : new Color(0.85f, 0.9f, 0.6f, 1),
                new String[]{"survey"}, new String[]{"run"}, new String[]{"run"}, new String[]{"none"}, 1.8f) : null;
        return a != null ? a : blob(big ? new Color(0.9f, 0.5f, 0.2f, 1) : new Color(0.6f, 0.8f, 0.4f, 1), big ? 2f : 1.2f);
    }

    static Actor3D blob(Color c, float size) {
        ModelBuilder mb = new ModelBuilder();
        Model m = mb.createSphere(size, size * 1.2f, size, 12, 10, Heroes.mat(c, false), Usage.Position | Usage.Normal);
        Actor3D a = new Actor3D();
        a.mi = new ModelInstance(m);
        a.fallbackBlob = true;
        a.scale = 1;
        a.pbrOff = size * 0.6f;
        return a;
    }
    float pbrOff;

    // anim: 0 idle, 1 walk, 2 basic, 3..5 skills, 6 dead
    public void update(float x, float z, float rot, int anim, float t, float dt) {
        if (rig != null) { rig.update(x, z, rot, anim, t); return; }
        if (ac != null) {
            String want = idle;
            float spd = 1f;
            if (anim == 1) want = walk;
            else if (anim >= 2 && anim <= 5) { want = atk; spd = atkSpeed; }
            else if (anim == 6) want = death;
            if (want == null) want = idle;
            if (want != null && !want.equals(cur)) {
                cur = want;
                ac.animate(want, anim == 6 ? 1 : -1, spd, null, 0.15f);
            }
            ac.update(dt);
        }
        float y = fallbackBlob ? pbrOff + (anim == 1 ? Math.abs((float) Math.sin(t * 10)) * 0.15f : 0) : 0;
        float yaw = rot, pitch = 0;
        if (anim == 3) yaw += t * 720f;
        if (anim == 4) pitch = 20;
        if (anim == 5) y += Math.abs((float) Math.sin(t * 5f)) * 1.0f;
        mi.transform.setToTranslation(x, y, z).rotate(Vector3.Y, yaw).rotate(Vector3.X, pitch).scale(scale, scale, scale);
        if (anim == 6 && (death == null || fallbackBlob)) {
            mi.transform.setToTranslation(x, 0.3f, z).rotate(Vector3.Y, rot).rotate(Vector3.X, -88f).scale(scale, scale, scale);
        }
        mi.calculateTransforms();
    }
}
