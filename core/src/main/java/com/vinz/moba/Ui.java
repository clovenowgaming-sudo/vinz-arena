package com.vinz.moba;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;

import java.util.HashMap;

public class Ui {
    public static BitmapFont font;
    public static float sc, fs;
    static final GlyphLayout GL = new GlyphLayout();
    static final HashMap<Integer, NinePatchDrawable> cache = new HashMap<Integer, NinePatchDrawable>();
    static Texture white;

    public static void init() {
        sc = Gdx.graphics.getHeight() / 360f;
        fs = sc * 0.95f;
        font = new BitmapFont();
        font.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        font.getData().setScale(fs);
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(1, 1, 1, 1);
        pm.fill();
        white = new Texture(pm);
        pm.dispose();
    }

    public static String clean(String s) {
        s = s.trim().replaceAll("[^A-Za-z0-9_#.\\-]", "_");
        if (s.length() > 16) s = s.substring(0, 16);
        return s.isEmpty() ? "Player" : s;
    }

    public static NinePatchDrawable round(Color c) {
        int key = c.toIntBits();
        NinePatchDrawable d = cache.get(key);
        if (d != null) return d;
        Pixmap p = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        p.setColor(c);
        int r = 20;
        p.fillCircle(r, r, r);
        p.fillCircle(63 - r, r, r);
        p.fillCircle(r, 63 - r, r);
        p.fillCircle(63 - r, 63 - r, r);
        p.fillRectangle(r, 0, 64 - 2 * r, 64);
        p.fillRectangle(0, r, 64, 64 - 2 * r);
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        p.dispose();
        d = new NinePatchDrawable(new NinePatch(new TextureRegion(t), r, r, r, r));
        cache.put(key, d);
        return d;
    }

    public static Label label(String s, float mul, Color c) {
        Label l = new Label(s, new Label.LabelStyle(font, c));
        l.setFontScale(fs * mul);
        return l;
    }

    public static TextButton btn(String s, Color c, final Runnable r) {
        TextButton.TextButtonStyle st = new TextButton.TextButtonStyle();
        st.font = font;
        st.fontColor = Color.WHITE;
        st.up = round(c);
        st.down = round(new Color(c.r * 0.65f, c.g * 0.65f, c.b * 0.65f, 1));
        TextButton b = new TextButton(s, st);
        b.getLabel().setFontScale(fs);
        b.addListener(new ChangeListener() {
            public void changed(ChangeEvent e, Actor a) { r.run(); }
        });
        return b;
    }

    public static TextField field(String text, String hint) {
        TextField.TextFieldStyle st = new TextField.TextFieldStyle();
        st.font = font;
        st.fontColor = Color.WHITE;
        st.messageFontColor = Color.GRAY;
        st.background = round(new Color(0.05f, 0.08f, 0.16f, 0.95f));
        st.cursor = new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(new TextureRegion(white));
        st.selection = new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(new TextureRegion(white));
        TextField f = new TextField(text, st);
        f.setMessageText(hint);
        return f;
    }

    // align: 0 left, 1 center, 2 right. y is the vertical center.
    public static void text(SpriteBatch sb, String s, float x, float y, float mul, Color c, int align) {
        font.getData().setScale(fs * mul);
        font.setColor(c);
        GL.setText(font, s);
        float xx = align == 1 ? x - GL.width / 2 : (align == 2 ? x - GL.width : x);
        font.draw(sb, s, xx, y + GL.height / 2);
        font.getData().setScale(fs);
    }

    public static void bg(ShapeRenderer sr, float t) {
        int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        float k = (float) Math.sin(t * 0.4f) * 0.5f + 0.5f;
        Color c1 = new Color(0.03f, 0.05f + 0.04f * k, 0.15f, 1);
        Color c2 = new Color(0.1f + 0.08f * k, 0.05f, 0.2f, 1);
        Color c3 = new Color(0.02f, 0.02f, 0.08f, 1);
        Color c4 = new Color(0.04f, 0.08f, 0.2f + 0.1f * k, 1);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.rect(0, 0, w, h, c1, c2, c3, c4);
        for (int i = 0; i < 40; i++) {
            float px = (i * 97.3f + t * (6 + (i % 7) * 4)) % w;
            float py = (i * 53.1f + (float) Math.sin(t * 0.7f + i) * 20) % h;
            float r = (2 + (i % 5)) * sc;
            sr.setColor(0.4f, 0.7f, 1f, 0.08f + 0.06f * (float) Math.sin(t + i));
            sr.circle(px, py, r);
        }
        sr.end();
    }
}
