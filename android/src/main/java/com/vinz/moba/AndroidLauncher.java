package com.vinz.moba;

import android.content.Intent;
import android.os.Bundle;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;

public class AndroidLauncher extends AndroidApplication implements Platform {
    private Platform.Callback cb;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        AndroidApplicationConfiguration c = new AndroidApplicationConfiguration();
        c.useImmersiveMode = true;
        c.numSamples = 0;
        initialize(new MobaGame(this), c);
    }

    public boolean hasGoogle() { return true; }

    public void googleSignIn(Platform.Callback callback) {
        cb = callback;
        GoogleSignInOptions o = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().build();
        GoogleSignInClient cl = GoogleSignIn.getClient(this, o);
        startActivityForResult(cl.getSignInIntent(), 9001);
    }

    @Override
    protected void onActivityResult(int rq, int rs, Intent d) {
        super.onActivityResult(rq, rs, d);
        if (rq != 9001 || cb == null) return;
        String res = null;
        try {
            GoogleSignInAccount a = GoogleSignIn.getSignedInAccountFromIntent(d).getResult(ApiException.class);
            String mail = a.getEmail() == null ? "x" : a.getEmail();
            String n = a.getDisplayName() != null ? a.getDisplayName() : mail;
            res = n.replace(' ', '_') + "#" + (Math.abs(mail.hashCode()) % 10000);
        } catch (Exception e) { res = null; }
        final String fin = res;
        final Platform.Callback c2 = cb;
        Gdx.app.postRunnable(new Runnable() { public void run() { c2.done(fin); } });
    }
}
