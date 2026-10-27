package com.vinz.moba;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class HeroSelectScreen extends ScreenAdapter {
    final Stage st = new Stage(new ScreenViewport());
    final ShapeRenderer sr = new ShapeRenderer();
    final ModelBatch mb = new ModelBatch();
    final Environment env = new Environment();
    PerspectiveCamera cam;
    Heroes.Rig rig;
    int shown = -1;
    float t;
    final Label[] slot = new Label[10];
    Label timerL, heroL, skillL;
    static final int[] ANIMS = {0, 2, 3, 4, 5};

    public HeroSelectScreen() {
        float s = Ui.sc;
        env.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.5f, 0.5f, 0.6f, 1f));
        env.add(new DirectionalLight().set(1f, 0.95f, 0.85f, -0.4f, -0.8f, -0.6f));
        cam = new PerspectiveCamera(40, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(0, 2.1f, 7.5f);
        cam.lookAt(0, 1.3f, 0);
        cam.near = 0.5f;
        cam.far = 50;
        cam.update();

        Table root = new Table();
        root.setFillParent(true);
        root.pad(8 * s);
        st.addActor(root);
        Table l = new Table(), r = new Table();
        for (int i = 0; i < 10; i++) {
            slot[i] = Ui.label("", 0.9f, i < 5 ? new Color(0.5f, 0.75f, 1f, 1) : new Color(1f, 0.55f, 0.5f, 1));
            Table c = i < 5 ? l : r;
            c.add(slot[i]).width(150 * s).height(26 * s).left().padBottom(3 * s).row();
        }
        timerL = Ui.label("", 2.2f, new Color(1f, 0.85f, 0.3f, 1));
        Table mid = new Table();
        mid.add(Ui.label("CHOOSE YOUR HERO", 1.2f, Color.WHITE)).row();
        mid.add(timerL);
        root.add(l).top().left();
        root.add(mid).top().expandX();
        root.add(r).top().right().row();
        root.add().expand().colspan(3).row();
        Table bot = new Table();
        heroL = Ui.label("", 1.4f, Color.WHITE);
        skillL = Ui.label("", 0.8f, Color.LIGHT_GRAY);
        Table info = new Table();
        info.add(heroL).row();
        info.add(skillL);
        bot.add(info).padBottom(6 * s).row();
        Table btns = new Table();
        for (int i = 0; i < Heroes.COUNT; i++) {
            final int h = i;
            Color c = new Color(Heroes.MAIN[i].r * 0.9f, Heroes.MAIN[i].g * 0.9f, Heroes.MAIN[i].b * 0.9f, 1);
            btns.add(Ui.btn(Heroes.NAME[i], c, new Runnable() {
                public void run() { choose(h); }
            })).width(110 * s).height(44 * s).pad(4 * s);
        }
        bot.add(btns);
        root.add(bot).colspan(3).bottom();
        choose(G.pickHero);
    }

    void choose(int h) {
        G.pickHero = h;
        G.hero[G.myIdx] = h;
        G.picked[G.myIdx] = true;
        G.net.send("PICK " + h);
    }

    @Override
    public void show() { Gdx.input.setInputProcessor(st); }

    @Override
    public void render(float dt) {
        t += dt;
        G.pump();
        if (G.net.state >= 3) {
            G.resetMatch();
            G.loggedIn = false;
            MobaGame.I.setScreen(new LoginScreen());
            return;
        }
        if (G.started) { MobaGame.I.setScreen(new MatchScreen()); return; }
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Ui.bg(sr, t);
        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
        int h = G.pickHero;
        if (shown != h) { shown = h; rig = new Heroes.Rig(h); }
        int a = ANIMS[((int) (t / 2.2f)) % ANIMS.length];
        rig.update(0, 0, 15 + (float) Math.sin(t * 0.6f) * 25, a, t);
        mb.begin(cam);
        mb.render(rig.mi, env);
        mb.end();
        long left = Math.max(0, (G.pickEnd - System.currentTimeMillis()) / 1000);
        timerL.setText(left > 0 ? String.valueOf(left) : "Starting...");
        int my = G.team(G.myIdx);
        for (int i = 0; i < 10; i++) {
            String hn = G.team(i) == my ? (G.picked[i] ? Heroes.NAME[G.hero[i]] : "picking...") : "???";
            slot[i].setText(G.names[i] + "  " + hn);
        }
        heroL.setText(Heroes.NAME[h] + "  (" + Heroes.ROLE[h] + ")");
        skillL.setText(Heroes.ATK[h][0] + " | " + Heroes.ATK[h][1] + " | " + Heroes.ATK[h][2] + " | " + Heroes.ATK[h][3]);
        st.act(dt);
        st.draw();
    }

    @Override
    public void resize(int w, int h) {
        st.getViewport().update(w, h, true);
        cam.viewportWidth = w;
        cam.viewportHeight = h;
        cam.update();
    }

    @Override
    public void hide() { Gdx.input.setInputProcessor(null); st.dispose(); sr.dispose(); mb.dispose(); }
}
