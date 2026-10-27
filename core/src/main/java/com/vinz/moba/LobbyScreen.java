package com.vinz.moba;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
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
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class LobbyScreen extends ScreenAdapter {
    final Stage st = new Stage(new ScreenViewport());
    final ShapeRenderer sr = new ShapeRenderer();
    final ModelBatch mb = new ModelBatch();
    final Environment env = new Environment();
    PerspectiveCamera cam;
    Heroes.Rig rig;
    Model platM, ringM;
    ModelInstance plat, ring;
    float t;
    Label nameL, rankL, heroL, skillL, partyL, queueL, toastL, invL;
    Table fp, qp, invp;
    TextField addF;
    int lastVer = -1;
    boolean showFriends;
    static final int[] ANIMS = {0, 2, 3, 4, 5};

    public LobbyScreen() {
        float s = Ui.sc;
        env.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.5f, 0.5f, 0.6f, 1f));
        env.add(new DirectionalLight().set(1f, 0.95f, 0.85f, -0.4f, -0.8f, -0.6f));
        env.add(new DirectionalLight().set(0.3f, 0.4f, 0.9f, 0.6f, -0.2f, 0.5f));
        cam = new PerspectiveCamera(40, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(0, 2.1f, 7.5f);
        cam.lookAt(0, 1.3f, 0);
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
        rig = new Heroes.Rig(G.pickHero);

        Table root = new Table();
        root.setFillParent(true);
        root.pad(8 * s);
        st.addActor(root);

        Table left = new Table();
        nameL = Ui.label(G.name, 1.6f, Color.WHITE);
        rankL = Ui.label("", 1.0f, new Color(1f, 0.85f, 0.3f, 1));
        partyL = Ui.label("", 0.8f, Color.LIGHT_GRAY);
        left.add(nameL).left().row();
        left.add(rankL).left().row();
        left.add(partyL).left().row();
        left.add(Ui.btn("FRIENDS", new Color(0.25f, 0.3f, 0.5f, 1), new Runnable() {
            public void run() { showFriends = !showFriends; fp.setVisible(showFriends); G.net.send("FL"); }
        })).width(120 * s).height(30 * s).padTop(6 * s).left().row();
        left.add(Ui.btn("LEAVE PARTY", new Color(0.4f, 0.2f, 0.2f, 1), new Runnable() {
            public void run() { G.net.send("LEAVEP"); }
        })).width(120 * s).height(24 * s).padTop(4 * s).left().row();

        Table right = new Table();
        right.add(Ui.btn("RANKED", new Color(0.85f, 0.6f, 0.1f, 1), new Runnable() {
            public void run() { queue("rank"); }
        })).width(170 * s).height(50 * s).pad(4 * s).row();
        right.add(Ui.btn("CLASSIC", new Color(0.12f, 0.5f, 0.85f, 1), new Runnable() {
            public void run() { queue("classic"); }
        })).width(170 * s).height(50 * s).pad(4 * s).row();

        root.add(left).top().left().expandX();
        root.add(right).top().right().row();
        root.add().expand().colspan(2).row();

        Table bottom = new Table();
        bottom.add(Ui.btn("<", new Color(0.2f, 0.25f, 0.4f, 1), new Runnable() {
            public void run() { pick(-1); }
        })).width(44 * s).height(44 * s);
        Table info = new Table();
        heroL = Ui.label("", 1.5f, Color.WHITE);
        skillL = Ui.label("", 0.8f, Color.LIGHT_GRAY);
        info.add(heroL).row();
        info.add(skillL);
        bottom.add(info).width(320 * s).pad(0, 8 * s, 0, 8 * s);
        bottom.add(Ui.btn(">", new Color(0.2f, 0.25f, 0.4f, 1), new Runnable() {
            public void run() { pick(1); }
        })).width(44 * s).height(44 * s);
        root.add(bottom).colspan(2).bottom();

        fp = new Table();
        fp.setBackground(Ui.round(new Color(0, 0, 0, 0.78f)));
        fp.setVisible(false);
        st.addActor(fp);
        addF = Ui.field("", "add friend (Name#1234)");

        qp = new Table();
        qp.setBackground(Ui.round(new Color(0, 0, 0, 0.85f)));
        queueL = Ui.label("", 1.3f, Color.WHITE);
        qp.pad(14 * s);
        qp.add(queueL).row();
        qp.add(Ui.btn("CANCEL", new Color(0.7f, 0.2f, 0.2f, 1), new Runnable() {
            public void run() { G.net.send("CANCEL"); }
        })).width(120 * s).height(32 * s).padTop(8 * s);
        qp.setVisible(false);
        st.addActor(qp);

        invp = new Table();
        invp.setBackground(Ui.round(new Color(0.1f, 0.3f, 0.2f, 0.92f)));
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
        rig = new Heroes.Rig(G.pickHero);
    }

    void rebuildFriends() {
        float s = Ui.sc;
        fp.clear();
        fp.pad(8 * s);
        fp.add(Ui.label("FRIENDS", 1.1f, Color.WHITE)).colspan(3).left().row();
        fp.add(addF).width(190 * s).height(26 * s).colspan(2);
        fp.add(Ui.btn("ADD", new Color(0.2f, 0.55f, 0.3f, 1), new Runnable() {
            public void run() { String n = Ui.clean(addF.getText()); if (!addF.getText().trim().isEmpty()) G.net.send("FRIEND " + n); addF.setText(""); }
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
        fp.setPosition(8 * s, h - 150 * s - fp.getHeight());
        qp.pack();
        qp.setPosition(w / 2 - qp.getWidth() / 2, h / 2 - qp.getHeight() / 2);
        invp.pack();
        invp.setPosition(w / 2 - invp.getWidth() / 2, h - invp.getHeight() - 8 * s);
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
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Ui.bg(sr, t);
        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);

        int a = ANIMS[((int) (t / 2.6f)) % ANIMS.length];
        rig.update(0, 0, 20 + (float) Math.sin(t * 0.5f) * 25, a, t);
        ring.materials.get(0).set(ColorAttribute.createEmissive(new Color(0.3f, 0.6f + 0.3f * (float) Math.sin(t * 2), 1f, 1)));
        mb.begin(cam);
        mb.render(plat, env);
        mb.render(ring, env);
        mb.render(rig.mi, env);
        mb.end();

        nameL.setText(G.name);
        rankL.setText(G.tier(G.rank) + "  -  " + G.rank + " pts");
        partyL.setText(G.party.isEmpty() ? "Solo" : "Party: " + join(G.party));
        int h = G.pickHero;
        heroL.setText(Heroes.NAME[h] + "  (" + Heroes.ROLE[h] + ")");
        skillL.setText("HP " + Heroes.HP[h] + "   " + Heroes.ATK[h][1] + " / " + Heroes.ATK[h][2] + " / " + Heroes.ATK[h][3]);
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
            toastL.setPosition(Gdx.graphics.getWidth() / 2f - toastL.getWidth() / 2, 70 * Ui.sc);
        }
        st.act(dt);
        st.draw();
    }

    static String join(java.util.List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) { if (i > 0) sb.append(", "); sb.append(l.get(i)); }
        return sb.toString();
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
        st.dispose(); sr.dispose(); mb.dispose(); platM.dispose(); ringM.dispose();
    }
}
