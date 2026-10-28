package com.vinz.moba;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import net.mgsx.gltf.scene3d.lights.DirectionalLightEx;

public class LobbyScreen extends ScreenAdapter {
    final Stage st = new Stage(new ScreenViewport());
    final ShapeRenderer sr = new ShapeRenderer();
    final SpriteBatch sb = new SpriteBatch();
    ModelBatch mbA, mbP;
    final Environment env = new Environment(), envP = new Environment();
    PerspectiveCamera cam;
    Actor3D actor;
    Model platM, ringM;
    ModelInstance plat, ring;
    float t;
    Label nameL, rankL, heroL, skillL, queueL, toastL, invL;
    TextButton classicB, rankedB;
    Table fp, qp, invp;
    TextField addF;
    int lastVer = -1;
    boolean showFriends;
    String sel = "classic";
    static final int[] ANIMS = {0, 2, 3, 4, 5};

    public LobbyScreen() {
        float s = Ui.sc;
        mbA = new ModelBatch();
        mbP = Actor3D.modelsOk ? Actor3D.pbrBatch() : null;
        env.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.5f, 0.5f, 0.6f, 1f));
        env.add(new DirectionalLight().set(1f, 0.95f, 0.85f, -0.4f, -0.8f, -0.6f));
        envP.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.75f, 0.75f, 0.8f, 1f));
        DirectionalLightEx l1 = new DirectionalLightEx();
        l1.direction.set(-0.4f, -0.8f, -0.6f).nor();
        l1.color.set(1f, 0.95f, 0.85f, 1f);
        l1.intensity = 3f;
        envP.add(l1);
        DirectionalLightEx l2 = new DirectionalLightEx();
        l2.direction.set(0.6f, -0.2f, 0.5f).nor();
        l2.color.set(0.5f, 0.6f, 1f, 1f);
        l2.intensity = 1.5f;
        envP.add(l2);
        cam = new PerspectiveCamera(40, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(0, 2.3f, 8f);
        cam.lookAt(0, 1.4f, 0);
        cam.near = 0.5f;
        cam.far = 50;
        cam.update();
        ModelBuilder b = new ModelBuilder();
        platM = b.createCylinder(3.6f, 0.25f, 3.6f, 32, Heroes.mat(new Color(0.15f, 0.2f, 0.35f, 1), false), Usage.Position | Usage.Normal);
        ringM = b.createCylinder(4.2f, 0.12f, 4.2f, 32, Heroes.mat(new Color(0.4f, 0.8f, 1f, 1), true), Usage.Position | Usage.Normal);
        plat = new ModelInstance(platM);
        plat.transform.setToTranslation(0, -0.12f, 0);
        ring = new ModelInstance(ringM);
        ring.transform.setToTranslation(0, -0.2f, 0);
        actor = Actor3D.hero(G.pickHero);

        Table root = new Table();
        root.setFillParent(true);
        st.addActor(root);

        // ---- top bar: profile + social
        Table top = new Table();
        nameL = Ui.label(G.name, 1.4f, Color.WHITE);
        rankL = Ui.label("", 1.0f, Ui.GOLD);
        top.add(nameL).left().padLeft(12 * s);
        top.add(rankL).left().padLeft(14 * s).expandX().left();
        top.add(Ui.btn("FRIENDS", new Color(0.25f, 0.3f, 0.55f, 1), new Runnable() {
            public void run() { showFriends = !showFriends; fp.setVisible(showFriends); G.net.send("FL"); }
        })).width(100 * s).height(28 * s).padRight(6 * s);
        top.add(Ui.btn("LEAVE PARTY", new Color(0.45f, 0.2f, 0.2f, 1), new Runnable() {
            public void run() { G.net.send("LEAVEP"); }
        })).width(110 * s).height(28 * s).padRight(8 * s);
        root.add(top).expandX().fillX().height(44 * s).row();

        // ---- middle: hero picker around the 3D model
        Table mid = new Table();
        mid.add(Ui.btn("<", new Color(0.2f, 0.25f, 0.45f, 0.9f), new Runnable() {
            public void run() { pick(-1); }
        })).width(46 * s).height(60 * s).padLeft(30 * s).left();
        mid.add().expand();
        mid.add(Ui.btn(">", new Color(0.2f, 0.25f, 0.45f, 0.9f), new Runnable() {
            public void run() { pick(1); }
        })).width(46 * s).height(60 * s).padRight(30 * s).right().row();
        Table info = new Table();
        heroL = Ui.label("", 1.7f, Color.WHITE);
        skillL = Ui.label("", 0.8f, Color.LIGHT_GRAY);
        info.add(heroL).row();
        info.add(skillL);
        mid.add(info).colspan(3).padBottom(4 * s);
        root.add(mid).expand().fill().row();

        // ---- bottom bar: party slots (drawn), mode tabs, START
        Table bot = new Table();
        bot.add().width(200 * s);
        classicB = Ui.btn("CLASSIC", new Color(0.12f, 0.45f, 0.85f, 1), new Runnable() {
            public void run() { sel = "classic"; }
        });
        rankedB = Ui.btn("RANKED", new Color(0.7f, 0.5f, 0.1f, 1), new Runnable() {
            public void run() { sel = "rank"; }
        });
        bot.add(classicB).width(110 * s).height(34 * s).padRight(6 * s);
        bot.add(rankedB).width(110 * s).height(34 * s).expandX().left();
        bot.add(Ui.btn("START", new Color(0.95f, 0.65f, 0.1f, 1), new Runnable() {
            public void run() { queue(sel); }
        })).width(170 * s).height(46 * s).padRight(14 * s);
        root.add(bot).expandX().fillX().height(62 * s);

        fp = new Table();
        fp.setBackground(Ui.round(new Color(0, 0, 0, 0.8f)));
        fp.setVisible(false);
        st.addActor(fp);
        addF = Ui.field("", "add friend (Name#1234)");

        qp = new Table();
        qp.setBackground(Ui.round(new Color(0, 0, 0, 0.88f)));
        queueL = Ui.label("", 1.3f, Color.WHITE);
        qp.pad(14 * s);
        qp.add(queueL).row();
        qp.add(Ui.btn("CANCEL", new Color(0.7f, 0.2f, 0.2f, 1), new Runnable() {
            public void run() { G.net.send("CANCEL"); }
        })).width(120 * s).height(32 * s).padTop(8 * s);
        qp.setVisible(false);
        st.addActor(qp);

        invp = new Table();
        invp.setBackground(Ui.round(new Color(0.1f, 0.3f, 0.2f, 0.94f)));
        invp.pad(8 * s);
        invL = Ui.label("", 1.0f, Color.WHITE);
        invp.add(invL).padRight(8 * s);
        invp.add(Ui.btn("ACCEPT", new Color(0.2f, 0.65f, 0.3f, 1), new Runnable() {
            public void run() { G.net.send("JOIN " + G.invFrom); G.invFrom = ""; }
        })).height(28 * s).padRight(4 * s);
        invp.add(Ui.btn("X", new Color(0.5f, 0.2f, 0.2f, 1), new Runnable() {
            public void run() { G.invFrom = ""; }
        })).height(28 * s);
        invp.setVisible(false);
        st.addActor(invp);

        toastL = Ui.label("", 1.0f, Color.YELLOW);
        st.addActor(toastL);
        G.net.send("FL");
    }

    void queue(String m) {
        if (G.queued) return;
        G.mode = m;
        G.net.send("QUEUE " + m);
    }

    void pick(int d) {
        G.pickHero = (G.pickHero + d + Heroes.COUNT) % Heroes.COUNT;
        actor = Actor3D.hero(G.pickHero);
    }

    void rebuildFriends() {
        float s = Ui.sc;
        fp.clear();
        fp.pad(8 * s);
        fp.add(Ui.label("FRIENDS", 1.1f, Color.WHITE)).colspan(3).left().row();
        fp.add(addF).width(190 * s).height(26 * s).colspan(2);
        fp.add(Ui.btn("ADD", new Color(0.2f, 0.55f, 0.3f, 1), new Runnable() {
            public void run() { if (!addF.getText().trim().isEmpty()) G.net.send("FRIEND " + Ui.clean(addF.getText())); addF.setText(""); }
        })).height(26 * s).padLeft(4 * s).row();
        int n = Math.min(6, G.friendName.size());
        for (int i = 0; i < n; i++) {
            final String fn = G.friendName.get(i);
            boolean on = G.friendOn.get(i);
            fp.add(Ui.label(on ? "o" : "-", 1f, on ? Color.GREEN : Color.GRAY)).padRight(4 * s);
            fp.add(Ui.label(fn, 0.9f, Color.WHITE)).left().expandX();
            fp.add(Ui.btn("INVITE", new Color(0.2f, 0.4f, 0.75f, 1), new Runnable() {
                public void run() { G.net.send("INVITE " + fn); }
            })).height(24 * s).padLeft(4 * s).row();
        }
        fp.pack();
        layoutPanels();
    }

    void layoutPanels() {
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight(), s = Ui.sc;
        fp.setPosition(8 * s, h - 50 * s - fp.getHeight());
        qp.pack();
        qp.setPosition(w / 2 - qp.getWidth() / 2, h / 2 - qp.getHeight() / 2);
        invp.pack();
        invp.setPosition(w / 2 - invp.getWidth() / 2, h - 44 * s - invp.getHeight() - 6 * s);
    }

    @Override
    public void show() { Gdx.input.setInputProcessor(st); rebuildFriends(); }

    @Override
    public void render(float dt) {
        t += dt;
        G.pump();
        if (G.net.state >= 3) {
            G.loggedIn = false;
            G.say("Disconnected from server");
            MobaGame.I.setScreen(new LoginScreen());
            return;
        }
        if (G.matchFound) { MobaGame.I.setScreen(new HeroSelectScreen()); return; }
        float s = Ui.sc, W = Gdx.graphics.getWidth(), H = Gdx.graphics.getHeight();
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Ui.bg(sr, t);
        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);

        int a = ANIMS[((int) (t / 2.6f)) % ANIMS.length];
        actor.update(0, 0, 20 + (float) Math.sin(t * 0.5f) * 25, a, t, dt);
        ring.materials.get(0).set(ColorAttribute.createEmissive(new Color(0.3f, 0.6f + 0.3f * (float) Math.sin(t * 2), 1f, 1)));
        mbA.begin(cam);
        mbA.render(plat, env);
        mbA.render(ring, env);
        if (!actor.pbr) mbA.render(actor.mi, env);
        mbA.end();
        if (actor.pbr && mbP != null) {
            mbP.begin(cam);
            mbP.render(actor.mi, envP);
            mbP.end();
        }

        // party slots (bottom-left)
        Gdx.gl.glEnable(GL20.GL_BLEND);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < 5; i++) {
            float cx = 26 * s + i * 34 * s, cy = 31 * s;
            boolean filled = i == 0 || i < G.party.size();
            sr.setColor(Ui.GOLD.r, Ui.GOLD.g, Ui.GOLD.b, filled ? 1f : 0.35f);
            sr.circle(cx, cy, 14 * s);
            sr.setColor(0.08f, 0.1f, 0.2f, 1f);
            sr.circle(cx, cy, 12 * s);
        }
        sr.end();
        sb.begin();
        for (int i = 0; i < 5; i++) {
            float cx = 26 * s + i * 34 * s, cy = 31 * s;
            String ch = i == 0 ? G.name.substring(0, 1).toUpperCase() : (i < G.party.size() ? G.party.get(i).substring(0, 1).toUpperCase() : "+");
            Ui.text(sb, ch, cx, cy, 1.2f, Color.WHITE, 1);
        }
        sb.end();

        nameL.setText(G.name);
        rankL.setText(G.tier(G.rank) + "  " + G.rank + " pts");
        int h = G.pickHero;
        heroL.setText(Heroes.NAME[h] + "  (" + Heroes.ROLE[h] + ")");
        skillL.setText("HP " + Heroes.HP[h] + "   " + Heroes.ATK[h][1] + " / " + Heroes.ATK[h][2] + " / " + Heroes.ATK[h][3]);
        classicB.setText(sel.equals("classic") ? "> CLASSIC <" : "CLASSIC");
        rankedB.setText(sel.equals("rank") ? "> RANKED <" : "RANKED");
        if (lastVer != G.friendsVer) { lastVer = G.friendsVer; rebuildFriends(); }
        qp.setVisible(G.queued);
        if (G.queued) {
            long sec = (System.currentTimeMillis() - G.queueStart) / 1000;
            queueL.setText("Searching " + G.mode.toUpperCase() + " match...  " + sec + "s");
            layoutPanels();
        }
        boolean inv = !G.invFrom.isEmpty();
        invp.setVisible(inv);
        if (inv) { invL.setText(G.invFrom + " invites you to a party"); layoutPanels(); }
        boolean tv = System.currentTimeMillis() - G.toastAt < 3500 && !G.toast.isEmpty();
        toastL.setVisible(tv);
        if (tv) {
            toastL.setText(G.toast);
            toastL.pack();
            toastL.setPosition(W / 2 - toastL.getWidth() / 2, 70 * s);
        }
        st.act(dt);
        st.draw();
    }

    @Override
    public void resize(int w, int h) {
        st.getViewport().update(w, h, true);
        cam.viewportWidth = w;
        cam.viewportHeight = h;
        cam.update();
        layoutPanels();
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
        st.dispose(); sr.dispose(); sb.dispose(); mbA.dispose(); if (mbP != null) mbP.dispose();
        platM.dispose(); ringM.dispose();
    }
}
