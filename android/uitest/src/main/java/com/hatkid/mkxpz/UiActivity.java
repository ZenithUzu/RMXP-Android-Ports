package com.hatkid.mkxpz;
public class UiActivity extends android.app.Activity {
    public OptionsMenu menu;
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        setTitle("UI test · simulated engine");
        android.widget.RelativeLayout layer = new android.widget.RelativeLayout(this);
        layer.setBackgroundColor(0xff254555);
        setContentView(layer);
        menu = new OptionsMenu(this, getFilesDir().getPath());
        menu.attachTo(layer);
    }
}
