package com.vinz.moba;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;

import net.mgsx.gltf.loaders.glb.GLBLoader;
import net.mgsx.gltf.loaders.gltf.GLTFLoader;
import net.mgsx.gltf.scene3d.attributes.PBRColorAttribute;
import net.mgsx.gltf.scene3d.scene.SceneAsset;
import net.mgsx.gltf.scene3d.shaders.PBRShaderProvider;

import java.util.HashMap;

/** A character / building in the world: free glTF models (PBR) with skeletal animation, or a procedural fallback. */
public class Actor3D {
    static final HashMap<String, SceneAsset> assets = new HashMap<String, SceneAsset>();
    static int maxBones = 12;
    public static boolean modelsOk;
    public static String loadError = "";
    static final float CHAR_H = 2.25f;   // standing height of the KayKit characters (model units)
    static final String HEX = "models/hex/addons/kaykit_medieval_hexagon_pack/Assets/gltf/buildings/";

    public static void loadModels() {
        try {
            String[] names = {"knight", "mage", "rogue", "barbarian", "skel_minion", "skel_warrior"};
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
        // buildings are optional: if they fail we keep the procedural towers
        try {
            String[][] b = {{"tower0", "blue/building_tower_B_blue"}, {"tower1", "red/building_tower_B_red"},
                    {"castle0", "blue/building_castle_blue"}, {"castle1", "red/building_castle_red"}};
            for (String[] x : b)
                assets.put(x[0], new GLTFLoader().load(Gdx.files.internal(HEX + x[1] + ".gltf")));
        } catch (Throwable t) {
            if (loadError.isEmpty()) loadError = "buildings: " + t;
        }
    }

    public static ModelBatch pbrBatch() { return new ModelBatch(PBRShaderProvider.createDefault(maxBones)); }

    /** Tower / castle building model (PBR) or null. kind = "tower" or "castle". */
    public static ModelInstance prop(String kind, int team) {
        SceneAsset a = assets.get(kind + team);
        return a == null ? null : new ModelInstance(a.scene.model);
    }

    // ---------------------------------------------------------------- instance
    public ModelInstance mi;
    public boolean pbr;
    public float scale = 1;
    Heroes.Rig rig;                // procedural fallback for heroes
    AnimationController ac;
    String idle, walk, death, cur = "";
    String[] atk = new String[4];  // clip for basic, skill1, skill2, skill3
    float atkSpeed = 1.6f;
    boolean fallbackBlob;
    float pbrOff;

    Actor3D() { }

    static Animation find(ModelInstance mi, String... names) {
        for (String n : names)
            for (Animation a : mi.animations) if (a.id.equalsIgnoreCase(n)) return a;
        return null;
    }

    static String id(Animation a) { return a == null ? null : a.id; }

    static void hide(ModelInstance mi, String... ids) {
        for (String s : ids) {
            Node n = mi.getNode(s, true);
            if (n != null) n.parts.clear();
        }
    }

    static Actor3D gltf(String file, float targetH, Color tint, String idle, String walk, String death, String[] atk, float atkSpeed, String... hidden) {
        SceneAsset a = assets.get(file);
        if (a == null) return null;
        Actor3D r = new Actor3D();
        r.mi = new ModelInstance(a.scene.model);
        r.pbr = true;
        r.scale = targetH / CHAR_H;
        hide(r.mi, hidden);
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
        r.death = id(find(r.mi, death));
        for (int i = 0; i < 4; i++) r.atk[i] = id(find(r.mi, atk[i]));
        r.atkSpeed = atkSpeed;
        return r;
    }

    public static Actor3D hero(int h) {
        Actor3D a = null;
        if (modelsOk) {
            switch (h) {
                case 0: // Kaizen: barbarian with the two-handed axe
                    a = gltf("barbarian", 2.7f, null, "Idle", "Running_A", "Death_A",
                            new String[]{"2H_Melee_Attack_Slice", "2H_Melee_Attack_Spin", "2H_Melee_Attack_Stab", "2H_Melee_Attack_Spinning"}, 1.8f,
                            "1H_Axe_Offhand", "1H_Axe", "Mug", "Barbarian_Round_Shield");
                    break;
                case 1: // Lumina: mage with staff
                    a = gltf("mage", 2.5f, null, "Idle", "Running_A", "Death_A",
                            new String[]{"Spellcast_Shoot", "Spellcast_Raise", "Spellcast_Long", "Spellcasting"}, 1.5f,
                            "Spellbook", "Spellbook_open", "1H_Wand");
                    break;
                case 2: // Zephyr: rogue with crossbow
                    a = gltf("rogue", 2.5f, null, "Idle", "Running_A", "Death_A",
                            new String[]{"2H_Ranged_Shoot", "2H_Ranged_Shoot", "Dodge_Backward", "2H_Ranged_Shooting"}, 1.6f,
                            "Knife_Offhand", "1H_Crossbow", "Knife", "Throwable");
                    break;
                default: // Bastion: knight with sword and round shield
                    a = gltf("knight", 2.9f, null, "Idle", "Running_A", "Death_A",
                            new String[]{"1H_Melee_Attack_Chop", "Block_Attack", "Jump_Full_Short", "1H_Melee_Attack_Slice_Diagonal"}, 1.6f,
                            "1H_Sword_Offhand", "Badge_Shield", "Rectangle_Shield", "Spike_Shield", "2H_Sword");
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
        Actor3D a = modelsOk ? gltf("skel_minion", ranged ? 1.7f : 1.9f, tint, "Idle", "Running_A", "Death_A",
                new String[]{ranged ? "1H_Ranged_Shoot" : "Unarmed_Melee_Attack_Punch_A", "", "", ""}, 1.6f) : null;
        return a != null ? a : blob(tint, 0.9f);
    }

    public static Actor3D monster(boolean big) {
        Actor3D a = modelsOk ? gltf("skel_warrior", big ? 4.4f : 2.7f, big ? new Color(1f, 0.6f, 0.3f, 1) : new Color(0.8f, 1f, 0.6f, 1),
                "Idle_Combat", "Running_A", "Death_A", new String[]{"1H_Melee_Attack_Chop", "", "", ""}, 1.4f) : null;
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

    // anim: 0 idle, 1 walk, 2 basic, 3..5 skills, 6 dead
    public void update(float x, float z, float rot, int anim, float t, float dt) {
        if (rig != null) { rig.update(x, z, rot, anim, t); return; }
        if (ac != null) {
            String want = idle;
            float spd = 1f;
            if (anim == 1) want = walk;
            else if (anim >= 2 && anim <= 5) {
                want = atk[anim - 2];
                if (want == null || want.isEmpty()) want = atk[0];
                spd = anim == 2 ? atkSpeed * 1.4f : atkSpeed;
            } else if (anim == 6) want = death;
            if (want == null) want = idle;
            if (want != null && !want.equals(cur)) {
                cur = want;
                ac.animate(want, anim == 6 ? 1 : -1, spd, null, 0.12f);
            }
            ac.update(dt);
        }
        float y = fallbackBlob ? pbrOff + (anim == 1 ? Math.abs((float) Math.sin(t * 10)) * 0.15f : 0) : 0;
        mi.transform.setToTranslation(x, y, z).rotate(Vector3.Y, rot).scale(scale, scale, scale);
        if (anim == 6 && (death == null || fallbackBlob)) {
            mi.transform.setToTranslation(x, 0.3f, z).rotate(Vector3.Y, rot).rotate(Vector3.X, -88f).scale(scale, scale, scale);
        }
        mi.calculateTransforms();
    }
}
