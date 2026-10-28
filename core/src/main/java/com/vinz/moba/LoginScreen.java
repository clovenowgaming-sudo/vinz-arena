package com.vinz.moba;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class LoginScreen extends ScreenAdapter {
    final Stage st = new Stage(new ScreenViewport());
    final ShapeRenderer sr = new ShapeRenderer();
    final Preferences pf = Gdx.app.getPreferences("vinzmoba");
    Label status;
    TextField hostF, guestF;
    float t;
    boolean busy;

    public LoginScreen() {
        float s = Ui.sc;
        Table root = new Table();
        root.setFillParent(true);
        st.addActor(root);
        root.add(Ui.label("VINZ ARENA", 3.2f, new Color(1f, 0.85f, 0.3f, 1))).padBottom(2 * s).row();
        root.add(Ui.label("5 v 5  MOBA   -   Mod By Vinz :3", 1.3f, Color.WHITE)).padBottom(14 * s).row();
        hostF = Ui.field(pf.getString("server", "127.0.0.1:7777"), "server host:port");
        root.add(hostF).width(280 * s).height(30 * s).padBottom(8 * s).row();
        if (MobaGame.I.platform.hasGoogle()) {
            root.add(Ui.btn("Sign in with Google", new Color(0.86f, 0.27f, 0.22f, 1), new Runnable() {
                public void run() { google(); }
            })).width(280 * s).height(36 * s).padBottom(8 * s).row();
        }
        guestF = Ui.field(pf.getString("guest", "Player" + (int) (Math.random() * 9000 + 1000)), "guest name");
        root.add(guestF).width(280 * s).height(30 * s).padBottom(6 * s).row();
        root.add(Ui.btn("Play as Guest", new Color(0.12f, 0.55f, 0.8f, 1), new Runnable() {
            public void run() { pf.putString("guest", guestF.getText()); pf.flush(); go(guestF.getText()); }
        })).width(280 * s).height(36 * s).padBottom(8 * s).row();
        status = Ui.label(G.toast, 0.9f, Color.LIGHT_GRAY);
        root.add(status);
    }

    void go(String n) {
        if (busy) return;
        busy = true;
        n = Ui.clean(n);
        G.name = n;
        String hs = hostF.getText().trim();
        int i = hs.lastIndexOf(':');
        G.host = i > 0 ? hs.substring(0, i) : hs;
        try { G.port = i > 0 ? Integer.parseInt(hs.substring(i + 1)) : 7777; } catch (Exception e) { G.port = 7777; }
        pf.putString("server", hs);
        pf.flush();
        G.loggedIn = false;
        G.net.connect(G.host, G.port, "HELLO " + n);
        status.setText("Connecting to " + G.host + ":" + G.port + " ...");
    }

    void google() {
        MobaGame.I.platform.googleSignIn(new Platform.Callback() {
            public void done(String n) {
                if (n == null) status.setText("Google sign-in failed (OAuth not set up yet) - use Guest");
                else go(n);
            }
        });
    }

    @Override
    public void show() { Gdx.input.setInputProcessor(st); }

    @Override
    public void render(float dt) {
        t += dt;
        G.pump();
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Ui.bg(sr, t);
        if (busy && G.net.state == 3) { busy = false; status.setText("Failed: " + G.net.err); G.net.state = 0; }
        if (busy && G.net.state == 4) { busy = false; status.setText("Disconnected"); G.net.state = 0; }
        if (G.loggedIn) { MobaGame.I.setScreen(new LobbyScreen()); return; }
        st.act(dt);
        st.draw();
    }

    @Override
    public void resize(int w, int h) { st.getViewport().update(w, h, true); }

    @Override
    public void hide() { Gdx.input.setInputProcessor(null); st.dispose(); sr.dispose(); }
}
