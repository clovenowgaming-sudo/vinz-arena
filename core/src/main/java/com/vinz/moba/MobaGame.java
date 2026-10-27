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
        setScreen(new LoginScreen());
    }
}
