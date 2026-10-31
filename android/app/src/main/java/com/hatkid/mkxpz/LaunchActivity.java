package com.hatkid.mkxpz;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import org.json.JSONObject;

/** Chooses a runtime without initializing SDL in web games. Older APK data defaults to RGSS. */
public final class LaunchActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        boolean web=false;
        try(java.io.InputStream in=getAssets().open("game/portemon_runtime.json")) {
            byte[] bytes=new byte[4096]; int size=in.read(bytes);
            web="mv".equals(new JSONObject(new String(bytes,0,size,java.nio.charset.StandardCharsets.UTF_8)).optString("engine"));
        } catch(Exception ignored) { }
        startActivity(new Intent(this,web?WebActivity.class:MainActivity.class));
        finish();
    }
}
