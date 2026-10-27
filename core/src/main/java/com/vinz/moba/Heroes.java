package com.vinz.moba;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;

public class Heroes {
    public static final int COUNT = 4;
    public static final String[] NAME = {"Kaizen", "Lumina", "Zephyr", "Bastion"};
    public static final String[] ROLE = {"Warrior", "Mage", "Marksman", "Tank"};
    public static final int[] HP = {1300, 850, 950, 1900};
    public static final float[] SPEED = {6.4f, 5.8f, 6.2f, 5.4f};
    public static final Color[] MAIN = {new Color(0.78f, 0.12f, 0.14f, 1), new Color(0.45f, 0.2f, 0.85f, 1),
            new Color(0.15f, 0.6f, 0.3f, 1), new Color(0.25f, 0.35f, 0.55f, 1)};
    public static final Color[] ACC = {new Color(1f, 0.8f, 0.2f, 1), new Color(0.4f, 0.9f, 1f, 1),
            new Color(0.85f, 0.7f, 0.3f, 1), new Color(0.8f, 0.8f, 0.85f, 1)};
    // index 0 = basic attack, 1..3 = skills
    public static final String[][] ATK = {
            {"Slash", "Whirlwind", "Dash Strike", "Blade Storm"},
            {"Arcane Bolt", "Fireball", "Frost Nova", "Meteor"},
            {"Arrow", "Pierce Shot", "Roll", "Arrow Rain"},
            {"Mace Hit", "Shield Bash", "Ground Slam", "Fortress"}};
    // kind: 0 melee arc(a=range) 1 aoe self(a=radius) 2 dash(a=dist,b=width) 3 bolt nearest(a=range)
    //       4 line(a=len,b=width) 5 aoe at target(a=cast range,b=radius) 6 back dash(a=dist)
    // {kind, a, b, damage, cooldown}
    public static final float[][][] S = {
            {{0, 3.4f, 0, 100, 0.7f}, {1, 4.6f, 0, 160, 4}, {2, 8, 2.2f, 200, 6}, {1, 6.5f, 0, 320, 14}},
            {{3, 11, 0, 90, 0.8f}, {3, 13, 0, 190, 3}, {1, 5.5f, 0, 140, 6}, {5, 14, 5.5f, 330, 14}},
            {{3, 13, 0, 85, 0.55f}, {4, 15, 1.8f, 170, 3.5f}, {6, 7, 0, 0, 5}, {5, 14, 5, 260, 13}},
            {{0, 3.2f, 0, 70, 0.8f}, {0, 4.2f, 0, 130, 4}, {1, 5.5f, 0, 150, 7}, {1, 6.5f, 0, 200, 14}}};

    static final Color SKIN = new Color(0.96f, 0.78f, 0.62f, 1);
    static final Color DARK = new Color(0.07f, 0.07f, 0.1f, 1);
    static final Color BROWN = new Color(0.4f, 0.26f, 0.14f, 1);
    static final Color STEEL = new Color(0.82f, 0.85f, 0.92f, 1);

    static final Model[] cache = new Model[COUNT];

    public static Model model(int h) {
        if (cache[h] == null) cache[h] = build(h);
        return cache[h];
    }

    static Material mat(Color c, boolean glow) {
        Material m = new Material(ColorAttribute.createDiffuse(c), ColorAttribute.createSpecular(0.6f, 0.6f, 0.6f, 1f),
                FloatAttribute.createShininess(24f));
        if (glow) m.set(ColorAttribute.createEmissive(c));
        return m;
    }

    static class B {
        final ModelBuilder mb = new ModelBuilder();
        final ArrayList<Node> nodes = new ArrayList<Node>();
        final ArrayList<String> par = new ArrayList<String>();
        final Matrix4 tm = new Matrix4();
        int cnt = 0;

        B() { mb.begin(); }

        Node n(String id, String parent, float x, float y, float z) {
            Node nd = mb.node();
            nd.id = id;
            nd.translation.set(x, y, z);
            nodes.add(nd);
            par.add(parent);
            return nd;
        }

        // kind: 0 box 1 sphere 2 cylinder 3 cone
        void p(int kind, Color c, boolean glow, float w, float h, float d, float ox, float oy, float oz) {
            MeshPartBuilder b = mb.part("p" + (cnt++), GL20.GL_TRIANGLES, Usage.Position | Usage.Normal, mat(c, glow));
            b.setVertexTransform(tm.setToTranslation(ox, oy, oz));
            switch (kind) {
                case 0: b.box(w, h, d); break;
                case 1: b.sphere(w, h, d, 10, 8); break;
                case 2: b.cylinder(w, h, d, 12); break;
                default: b.cone(w, h, d, 12);
            }
        }

        Model end() {
            Model m = mb.end();
            for (int i = 0; i < nodes.size(); i++) {
                String pr = par.get(i);
                if (pr != null) {
                    Node ch = nodes.get(i);
                    m.nodes.removeValue(ch, true);
                    m.getNode(pr).addChild(ch);
                }
            }
            return m;
        }
    }

    static Model build(int h) {
        B b = new B();
        Color M = MAIN[h], A = ACC[h];
        b.n("hips", null, 0, 1.08f, 0);
        b.p(0, A, false, 0.66f, 0.26f, 0.42f, 0, 0, 0);
        for (int s = -1; s <= 1; s += 2) {
            b.n(s < 0 ? "legL" : "legR", "hips", s * 0.18f, -0.08f, 0);
            b.p(1, M, false, 0.3f, 0.3f, 0.3f, 0, 0, 0);
            b.p(0, M, false, 0.27f, 0.5f, 0.29f, 0, -0.27f, 0);
            b.p(0, M, false, 0.23f, 0.44f, 0.25f, 0, -0.68f, 0.01f);
            b.p(1, A, false, 0.27f, 0.2f, 0.27f, 0, -0.48f, 0.08f);
            b.p(0, A, false, 0.29f, 0.2f, 0.44f, 0, -0.94f, 0.07f);
        }
        b.n("torso", "hips", 0, 0.12f, 0);
        b.p(0, M, false, 0.84f, 0.8f, 0.5f, 0, 0.42f, 0);
        b.p(0, A, false, 0.88f, 0.12f, 0.54f, 0, 0.05f, 0);
        b.p(1, A, true, 0.3f, 0.3f, 0.14f, 0, 0.55f, 0.27f);
        b.p(0, A, false, 0.92f, 0.1f, 0.52f, 0, 0.8f, 0);
        b.n("head", "torso", 0, 0.86f, 0);
        b.p(2, SKIN, false, 0.2f, 0.16f, 0.2f, 0, 0.04f, 0);
        b.p(1, SKIN, false, 0.54f, 0.58f, 0.54f, 0, 0.34f, 0);
        b.p(1, DARK, false, 0.09f, 0.11f, 0.06f, -0.1f, 0.38f, 0.25f);
        b.p(1, DARK, false, 0.09f, 0.11f, 0.06f, 0.1f, 0.38f, 0.25f);
        for (int s = -1; s <= 1; s += 2) {
            b.n(s < 0 ? "armL" : "armR", "torso", s * 0.56f, 0.68f, 0);
            b.p(1, A, false, 0.36f, 0.36f, 0.36f, 0, 0, 0);
            b.p(0, M, false, 0.2f, 0.44f, 0.22f, 0, -0.27f, 0);
            b.p(0, A, false, 0.23f, 0.1f, 0.25f, 0, -0.52f, 0);
            b.p(0, M, false, 0.18f, 0.38f, 0.2f, 0, -0.72f, 0);
            b.p(1, SKIN, false, 0.2f, 0.2f, 0.2f, 0, -0.96f, 0);
        }
        if (h == 0) { // Kaizen: knight with sword, crest and cape
            b.p(2, M, false, 0.58f, 0.2f, 0.58f, 0, 0.5f, 0);
            b.p(3, A, false, 0.2f, 0.5f, 0.45f, 0, 0.86f, -0.05f);
            b.n("cape", "torso", 0, 0.78f, -0.27f);
            b.p(0, new Color(0.5f, 0.05f, 0.08f, 1), false, 0.72f, 1.15f, 0.06f, 0, -0.55f, 0);
            Node w = b.n("weapon", "armR", 0, -0.96f, 0.1f);
            w.rotation.setEulerAngles(0, 70, 0);
            b.p(2, DARK, false, 0.07f, 0.3f, 0.07f, 0, 0.02f, 0);
            b.p(0, A, false, 0.5f, 0.08f, 0.1f, 0, 0.2f, 0);
            b.p(0, STEEL, false, 0.12f, 1.5f, 0.04f, 0, 1.0f, 0);
            b.p(0, A, true, 0.04f, 1.3f, 0.05f, 0, 1.0f, 0);
            b.p(3, STEEL, false, 0.12f, 0.3f, 0.04f, 0, 1.9f, 0);
        } else if (h == 1) { // Lumina: mage with hat, robe, staff
            b.p(3, M, false, 1.1f, 1.0f, 1.0f, 0, -0.5f, 0);
            b.p(2, M, false, 0.92f, 0.07f, 0.92f, 0, 0.64f, 0);
            b.p(3, M, false, 0.56f, 0.9f, 0.56f, 0, 1.12f, 0);
            b.p(2, A, true, 0.58f, 0.1f, 0.58f, 0, 0.72f, 0);
            b.p(1, new Color(0.85f, 0.7f, 1f, 1), false, 0.6f, 0.6f, 0.5f, 0, 0.34f, -0.1f);
            b.p(0, new Color(0.85f, 0.7f, 1f, 1), false, 0.5f, 0.9f, 0.12f, 0, -0.1f, -0.24f);
            Node w = b.n("weapon", "armR", 0, -0.96f, 0.05f);
            w.rotation.setEulerAngles(0, 8, 0);
            b.p(2, BROWN, false, 0.07f, 2.3f, 0.07f, 0, 0.3f, 0);
            b.p(1, A, true, 0.4f, 0.4f, 0.4f, 0, 1.55f, 0);
            b.p(2, A, true, 0.32f, 0.05f, 0.32f, 0, 1.3f, 0);
        } else if (h == 2) { // Zephyr: hooded archer with bow and quiver
            b.p(1, M, false, 0.64f, 0.62f, 0.6f, 0, 0.36f, -0.03f);
            b.p(3, M, false, 0.5f, 0.6f, 0.5f, 0, 0.82f, -0.12f);
            b.p(0, A, false, 0.9f, 0.12f, 0.5f, 0, 0.0f, 0);
            b.p(0, BROWN, false, 0.2f, 0.7f, 0.2f, 0.15f, 0.4f, 0 - 0.34f);
            b.p(3, A, false, 0.06f, 0.15f, 0.06f, 0.1f, 0.82f, -0.34f);
            b.p(3, A, false, 0.06f, 0.15f, 0.06f, 0.2f, 0.82f, -0.34f);
            Node w = b.n("weapon", "armL", 0, -0.96f, 0.1f);
            w.rotation.setEulerAngles(0, 0, 0);
            b.p(0, BROWN, false, 0.06f, 1.5f, 0.1f, 0, 0.1f, 0.2f);
            b.p(0, BROWN, false, 0.06f, 0.5f, 0.1f, 0, 0.95f, 0.1f);
            b.p(0, BROWN, false, 0.06f, 0.5f, 0.1f, 0, -0.75f, 0.1f);
            b.p(0, STEEL, true, 0.015f, 1.7f, 0.015f, 0, 0.1f, 0.0f);
        } else { // Bastion: heavy tank with shield and mace
            b.p(0, A, false, 1.0f, 0.6f, 0.58f, 0, 0.45f, 0.02f);
            b.p(1, M, false, 0.66f, 0.55f, 0.66f, 0, 0.45f, 0);
            b.p(0, DARK, false, 0.4f, 0.1f, 0.1f, 0, 0.4f, 0.3f);
            b.p(3, A, false, 0.14f, 0.5f, 0.14f, -0.34f, 0.82f, 0);
            b.p(3, A, false, 0.14f, 0.5f, 0.14f, 0.34f, 0.82f, 0);
            b.p(1, A, false, 0.56f, 0.4f, 0.56f, 0, 0.0f, 0);
            Node w = b.n("weapon", "armR", 0, -0.96f, 0.1f);
            w.rotation.setEulerAngles(0, 70, 0);
            b.p(2, BROWN, false, 0.09f, 1.2f, 0.09f, 0, 0.5f, 0);
            b.p(1, STEEL, false, 0.5f, 0.5f, 0.5f, 0, 1.2f, 0);
            b.p(3, A, false, 0.14f, 0.3f, 0.14f, 0, 1.5f, 0);
            b.p(3, A, false, 0.14f, 0.3f, 0.14f, 0.28f, 1.2f, 0);
            Node sh = b.n("shield", "armL", 0, -0.7f, 0.3f);
            sh.rotation.setEulerAngles(0, 90, 0);
            b.p(2, M, false, 1.0f, 0.12f, 1.0f, 0, 0, 0);
            b.p(2, A, false, 1.1f, 0.08f, 1.1f, 0, 0.05f, 0);
            b.p(1, A, true, 0.3f, 0.3f, 0.3f, 0, 0.1f, 0);
        }
        return b.end();
    }

    public static class Rig {
        public final ModelInstance mi;
        final Node hips, torso, head, armL, armR, legL, legR;
        public final float scale;

        public Rig(int h) {
            mi = new ModelInstance(model(h));
            hips = mi.getNode("hips");
            torso = mi.getNode("torso");
            head = mi.getNode("head");
            armL = mi.getNode("armL");
            armR = mi.getNode("armR");
            legL = mi.getNode("legL");
            legR = mi.getNode("legR");
            scale = h == 3 ? 0.98f : (h == 1 ? 0.8f : 0.85f);
        }

        // anim: 0 idle, 1 walk, 2 basic, 3..5 skills, 6 dead
        public void update(float x, float z, float rot, int anim, float t) {
            pose(anim, t);
            if (anim == 6) {
                mi.transform.setToTranslation(x, 0.35f, z).rotate(Vector3.Y, rot).rotate(Vector3.X, -88f).scale(scale, scale, scale);
            } else {
                mi.transform.setToTranslation(x, 0, z).rotate(Vector3.Y, rot).scale(scale, scale, scale);
            }
            mi.calculateTransforms();
        }

        void pose(int anim, float t) {
            hips.translation.set(0, 1.08f, 0);
            hips.rotation.idt(); torso.rotation.idt(); head.rotation.idt();
            armL.rotation.idt(); armR.rotation.idt(); legL.rotation.idt(); legR.rotation.idt();
            switch (anim) {
                case 0: {
                    float s = (float) Math.sin(t * 2.6f);
                    hips.translation.y += s * 0.025f;
                    armL.rotation.setEulerAngles(0, s * 4, -4);
                    armR.rotation.setEulerAngles(0, -s * 4, 4);
                    head.rotation.setEulerAngles((float) Math.sin(t * 0.8f) * 6, 0, 0);
                    break;
                }
                case 1: {
                    float s = (float) Math.sin(t * 11f);
                    hips.translation.y += Math.abs(s) * 0.07f;
                    legL.rotation.setEulerAngles(0, s * 42, 0);
                    legR.rotation.setEulerAngles(0, -s * 42, 0);
                    armL.rotation.setEulerAngles(0, -s * 38, -4);
                    armR.rotation.setEulerAngles(0, s * 38, 4);
                    torso.rotation.setEulerAngles(0, 8, 0);
                    break;
                }
                case 2: {
                    float ph = (t * 3.2f) % 1f;
                    armR.rotation.setEulerAngles(0, -165 + 150 * ph * ph, 0);
                    armL.rotation.setEulerAngles(0, -30, -6);
                    torso.rotation.setEulerAngles(30 - 60 * ph, 6, 0);
                    legL.rotation.setEulerAngles(0, -20, 0);
                    legR.rotation.setEulerAngles(0, 20, 0);
                    break;
                }
                case 3: {
                    torso.rotation.setEulerAngles(t * 720f, 0, 0);
                    armL.rotation.setEulerAngles(0, 0, -85);
                    armR.rotation.setEulerAngles(0, 0, 85);
                    legL.rotation.setEulerAngles(0, 0, -10);
                    legR.rotation.setEulerAngles(0, 0, 10);
                    break;
                }
                case 4: {
                    torso.rotation.setEulerAngles(0, 32, 0);
                    head.rotation.setEulerAngles(0, -20, 0);
                    armR.rotation.setEulerAngles(0, -100, 0);
                    armL.rotation.setEulerAngles(0, 60, 0);
                    legL.rotation.setEulerAngles(0, -45, 0);
                    legR.rotation.setEulerAngles(0, 40, 0);
                    break;
                }
                case 5: {
                    hips.translation.y += Math.abs((float) Math.sin(t * 5f)) * 1.1f;
                    armL.rotation.setEulerAngles(0, -170, 0);
                    armR.rotation.setEulerAngles(0, -170, 0);
                    legL.rotation.setEulerAngles(0, -40, 0);
                    legR.rotation.setEulerAngles(0, -20, 0);
                    head.rotation.setEulerAngles(0, -20, 0);
                    break;
                }
                default: {
                    armL.rotation.setEulerAngles(0, 0, -30);
                    armR.rotation.setEulerAngles(0, 0, 30);
                }
            }
        }
    }
}
