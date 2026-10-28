package com.vinz.moba;

import com.badlogic.gdx.Game;

public class MobaGame extends Game {
    public static MobaGame I;
    public final Platform platform;

    public MobaGame(Platform p) { platform = p; }

    @Override
    public void create() {
        I = this;
        Ui.init();
        Sfx.init();
        Actor3D.loadModels();
        if (!Actor3D.modelsOk) G.say("3D models failed: " + Actor3D.loadError);
        setScreen(new LoginScreen());
    }
}
