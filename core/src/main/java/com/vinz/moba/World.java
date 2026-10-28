package com.vinz.moba;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Matrix4;

import java.util.ArrayList;
import java.util.Random;

public class World {
    final ArrayList<ModelInstance> statics = new ArrayList<ModelInstance>();
    final ArrayList<ModelInstance> trees = new ArrayList<ModelInstance>();
    final ArrayList<float[]> treePos = new ArrayList<float[]>();
    final ArrayList<Model> models = new ArrayList<Model>();
    Texture grass;

    public static final Color BLUE = new Color(0.2f, 0.5f, 1f, 1), RED = new Color(1f, 0.25f, 0.25f, 1);
    public Model tBase, tBody, tTop0, tTop1, cry0, cry1;

    Model shape(int kind, Color c, boolean glow, float w, float h, float d) {
        ModelBuilder mb = new ModelBuilder();
        Material m = Heroes.mat(c, glow);
        Model md;
        if (kind == 1) md = mb.createSphere(w, h, d, 14, 10, m, Usage.Position | Usage.Normal);
        else if (kind == 2) md = mb.createCylinder(w, h, d, 16, m, Usage.Position | Usage.Normal);
        else if (kind == 3) md = mb.createCone(w, h, d, 16, m, Usage.Position | Usage.Normal);
        else md = mb.createBox(w, h, d, m, Usage.Position | Usage.Normal);
        models.add(md);
        return md;
    }

    void add(Model m, float x, float y, float z) {
        ModelInstance mi = new ModelInstance(m);
        mi.transform.setToTranslation(x, y, z);
        statics.add(mi);
    }

    public World() {
        Pixmap pm = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        Random r = new Random(7);
        for (int x = 0; x < 64; x++)
            for (int y = 0; y < 64; y++) {
                float v = 0.85f + r.nextFloat() * 0.3f;
                pm.setColor(0.22f * v, 0.5f * v, 0.2f * v, 1);
                pm.drawPixel(x, y);
            }
        grass = new Texture(pm);
        grass.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        grass.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pm.dispose();

        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder gp = mb.part("g", GL20.GL_TRIANGLES, Usage.Position | Usage.Normal | Usage.TextureCoordinates,
                new Material(TextureAttribute.createDiffuse(grass)));
        gp.setUVRange(0, 0, 40, 24);
        gp.box(110, 0.4f, 70);
        Model ground = mb.end();
        models.add(ground);
        add(ground, 0, -0.2f, 0);

        Model lane = shape(0, new Color(0.62f, 0.5f, 0.34f, 1), false, 100, 0.05f, 4.5f);
        float[] lz = {-18, 0, 18};
        for (float z : lz) add(lane, 0, 0.03f, z);
        Model bridge = shape(0, new Color(0.55f, 0.44f, 0.3f, 1), false, 4.5f, 0.05f, 36f);
        add(bridge, -30, 0.035f, 0);
        add(bridge, 30, 0.035f, 0);

        Model plat0 = shape(2, new Color(0.12f, 0.25f, 0.5f, 1), false, 14, 0.5f, 14);
        Model plat1 = shape(2, new Color(0.5f, 0.12f, 0.12f, 1), false, 14, 0.5f, 14);
        Model pil = shape(2, new Color(0.7f, 0.7f, 0.75f, 1), false, 1f, 4f, 1f);
        Model orb0 = shape(1, BLUE, true, 0.9f, 0.9f, 0.9f);
        Model orb1 = shape(1, RED, true, 0.9f, 0.9f, 0.9f);
        for (int t = 0; t < 2; t++) {
            float bx = t == 0 ? -43 : 43;
            add(t == 0 ? plat0 : plat1, bx, 0.25f, 0);
            float[] ox = {-5, 5, -5, 5}, oz = {-5, -5, 5, 5};
            for (int i = 0; i < 4; i++) {
                add(pil, bx + ox[i], 2f, oz[i]);
                add(t == 0 ? orb0 : orb1, bx + ox[i], 4.4f, oz[i]);
            }
        }
        tBase = shape(2, new Color(0.45f, 0.45f, 0.5f, 1), false, 3.2f, 0.6f, 3.2f);
        tBody = shape(2, new Color(0.62f, 0.62f, 0.68f, 1), false, 2f, 5f, 2f);
        tTop0 = shape(1, BLUE, true, 1.8f, 1.8f, 1.8f);
        tTop1 = shape(1, RED, true, 1.8f, 1.8f, 1.8f);
        cry0 = shape(3, BLUE, true, 3.2f, 6, 3.2f);
        cry1 = shape(3, RED, true, 3.2f, 6, 3.2f);

        ModelBuilder tb = new ModelBuilder();
        tb.begin();
        Node n = tb.node();
        n.id = "tree";
        MeshPartBuilder p1 = tb.part("trunk", GL20.GL_TRIANGLES, Usage.Position | Usage.Normal, Heroes.mat(new Color(0.4f, 0.26f, 0.14f, 1), false));
        p1.setVertexTransform(new Matrix4().setToTranslation(0, 1.25f, 0));
        p1.cylinder(0.8f, 2.5f, 0.8f, 8);
        MeshPartBuilder p2 = tb.part("l1", GL20.GL_TRIANGLES, Usage.Position | Usage.Normal, Heroes.mat(new Color(0.1f, 0.4f, 0.15f, 1), false));
        p2.setVertexTransform(new Matrix4().setToTranslation(0, 3.6f, 0));
        p2.cone(3.4f, 3.6f, 3.4f, 10);
        MeshPartBuilder p3 = tb.part("l2", GL20.GL_TRIANGLES, Usage.Position | Usage.Normal, Heroes.mat(new Color(0.14f, 0.5f, 0.2f, 1), false));
        p3.setVertexTransform(new Matrix4().setToTranslation(0, 5.3f, 0));
        p3.cone(2.5f, 3f, 2.5f, 10);
        Model tree = tb.end();
        models.add(tree);
        Random rnd = new Random(42);
        for (int i = 0; i < 50; i++) {
            float x = -38 + rnd.nextFloat() * 76;
            float sg = rnd.nextBoolean() ? 1 : -1;
            float z = rnd.nextBoolean() ? sg * (13.5f + rnd.nextFloat() * 2.5f) : sg * (27f + rnd.nextFloat() * 1.5f);
            ModelInstance mi = new ModelInstance(tree);
            float s = 0.7f + rnd.nextFloat() * 0.7f;
            mi.transform.setToTranslation(x, 0, z).scale(s, s, s);
            trees.add(mi);
            treePos.add(new float[]{x, z});
        }
    }

    public void render(ModelBatch mb, Environment env, Camera cam) {
        for (ModelInstance mi : statics) mb.render(mi, env);
        for (int i = 0; i < trees.size(); i++) {
            float[] p = treePos.get(i);
            if (cam.frustum.sphereInFrustum(p[0], 3, p[1], 5)) mb.render(trees.get(i), env);
        }
    }

    public void dispose() {
        for (Model m : models) m.dispose();
        grass.dispose();
    }
}
