package com.hatkid.mkxpz;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import com.hatkid.mkxpz.gamepad.Gamepad;
import com.hatkid.mkxpz.gamepad.GamepadButton;

/**
 * RMXP-Android-Ports: editor de controles en pantalla. Cada control (cruceta, cada botón,
 * teclado, turbo y menú) se puede mover, ocultar, cambiar de tamaño y de transparencia;
 * los botones del mando también de tecla. Todo se guarda en las preferencias.
 */
public class ControlsEditor
{
    // Teclas que se pueden asignar. Enter/C/Espacio = confirmar y Esc/X = cancelar en RPG Maker.
    private static final int[] KEYS = {
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_SPACE,
        KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_Z,
        KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D,
        KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_SHIFT_LEFT,
        KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_ALT_LEFT,
        KeyEvent.KEYCODE_F5, KeyEvent.KEYCODE_F6, KeyEvent.KEYCODE_F8, KeyEvent.KEYCODE_F9 };

    private final Activity mActivity;
    private final Gamepad mGamepad;
    private final SharedPreferences mPrefs;
    private final List<View> mExtra; // teclado, turbo, menú
    private final Runnable mOnDone;
    private View mOverlay;
    private float mGlobalSize = 1f;

    public ControlsEditor(Activity activity, Gamepad gamepad, SharedPreferences prefs, List<View> extra, Runnable onDone)
    {
        mActivity = activity;
        mGamepad = gamepad;
        mPrefs = prefs;
        mExtra = extra;
        mOnDone = onDone;
    }

    private String name(View v)
    {
        return mActivity.getResources().getResourceEntryName(v.getId());
    }

    private List<View> controls()
    {
        List<View> all = new ArrayList<>(mGamepad.controls());
        all.addAll(mExtra);
        return all;
    }

    private boolean hidden(View v)
    {
        // El botón de menú viene oculto: Atrás (o Select en un mando) ya abre el menú
        return mPrefs.getBoolean("oculto_" + name(v), v.getId() == R.id.rmxp_btn_menu);
    }

    /** Aplica posición, tamaño, transparencia, visibilidad y tecla de cada control. */
    public void apply(int globalSizePercent)
    {
        mGlobalSize = globalSizePercent / 100f;
        for (View v : controls()) {
            String n = name(v);
            v.setTranslationX(mPrefs.getFloat("x_" + n, 0));
            v.setTranslationY(mPrefs.getFloat("y_" + n, 0));
            float scale = mGlobalSize * mPrefs.getInt("tam_" + n, 100) / 100f;
            v.setScaleX(scale);
            v.setScaleY(scale);
            v.setAlpha(mPrefs.getInt("opa_" + n, 100) / 100f);
            // INVISIBLE (no GONE) para que los demás no cambien de lugar; tampoco recibe toques
            v.setVisibility(hidden(v) && !isActive() ? View.INVISIBLE : View.VISIBLE);
            if (isActive() && hidden(v))
                v.setAlpha(0.25f); // en el editor se ven tenues para poder volver a mostrarlos
        }
        for (GamepadButton b : mGamepad.buttons()) {
            int key = mPrefs.getInt("tecla_" + name(b), 0);
            if (key != 0)
                mGamepad.setButtonKey(b, key);
        }
    }

    public boolean isActive() { return mOverlay != null; }

    @SuppressLint("ClickableViewAccessibility")
    public void start(ViewGroup layout)
    {
        float dp = mActivity.getResources().getDisplayMetrics().density;
        FrameLayout overlay = new FrameLayout(mActivity);
        overlay.setBackgroundColor(0x33000000);

        LinearLayout bar = new LinearLayout(mActivity);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundResource(R.drawable.rmxp_panel_bar);
        int pad = Math.round(12 * dp);
        bar.setPadding(pad * 2, pad, pad, pad);
        TextView hint = new TextView(mActivity);
        hint.setText(R.string.rmxp_edit_hint);
        hint.setTextColor(mActivity.getColor(R.color.rmxp_niebla));
        hint.setTextSize(14);
        bar.addView(hint, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button reset = new Button(mActivity, null, 0, R.style.RmxpButton);
        reset.setText(R.string.rmxp_edit_reset);
        reset.setOnClickListener(v -> reset());
        bar.addView(reset, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(44 * dp)));
        Button done = new Button(mActivity, null, 0, R.style.RmxpButton);
        done.setText(R.string.rmxp_edit_done);
        done.setBackgroundTintList(mActivity.getColorStateList(R.color.rmxp_ambar));
        done.setTextColor(mActivity.getColor(R.color.rmxp_tinta));
        done.setOnClickListener(v -> stop(layout));
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(44 * dp));
        doneParams.leftMargin = Math.round(8 * dp);
        bar.addView(done, doneParams);
        overlay.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        float slop = 8 * dp;
        overlay.setOnTouchListener(new View.OnTouchListener() {
            View dragging;
            float startX, startY, baseX, baseY;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dragging = controlAt(e.getRawX(), e.getRawY());
                        if (dragging == null)
                            return false;
                        startX = e.getRawX();
                        startY = e.getRawY();
                        baseX = dragging.getTranslationX();
                        baseY = dragging.getTranslationY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (dragging == null)
                            return false;
                        float dx = e.getRawX() - startX, dy = e.getRawY() - startY;
                        if (Math.abs(dx) > slop || Math.abs(dy) > slop)
                            moved = true;
                        if (moved) {
                            dragging.setTranslationX(baseX + dx);
                            dragging.setTranslationY(baseY + dy);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (dragging == null)
                            return false;
                        if (moved)
                            mPrefs.edit()
                                .putFloat("x_" + name(dragging), dragging.getTranslationX())
                                .putFloat("y_" + name(dragging), dragging.getTranslationY()).apply();
                        else
                            editControl(dragging);
                        dragging = null;
                        return true;
                }
                return false;
            }
        });

        mOverlay = overlay;
        layout.addView(overlay, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        apply(Math.round(mGlobalSize * 100));
    }

    private void stop(ViewGroup layout)
    {
        layout.removeView(mOverlay);
        mOverlay = null;
        apply(Math.round(mGlobalSize * 100));
        mOnDone.run();
    }

    private View controlAt(float x, float y)
    {
        Rect r = new Rect();
        List<View> all = controls();
        for (int i = all.size() - 1; i >= 0; i--) { // los de arriba primero
            View v = all.get(i);
            if (v.getVisibility() == View.VISIBLE && v.getGlobalVisibleRect(r) && r.contains((int) x, (int) y))
                return v;
        }
        return null;
    }

    /** Ventana con las opciones de un control: tecla, tamaño, transparencia y visible. */
    private void editControl(View control)
    {
        String n = name(control);
        float dp = mActivity.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * dp);
        box.setPadding(pad, Math.round(8 * dp), pad, 0);

        if (control instanceof GamepadButton) {
            GamepadButton b = (GamepadButton) control;
            Button key = new Button(mActivity);
            key.setText(mActivity.getString(R.string.rmxp_edit_key) + ": " + Gamepad.keyLabel(b.getKey()));
            key.setAllCaps(false);
            key.setOnClickListener(v -> chooseKey(b, key));
            box.addView(key);
        }

        box.addView(label(R.string.rmxp_pad_size));
        SeekBar size = new SeekBar(mActivity);
        size.setMax(150); // 50 % a 200 %
        size.setProgress(mPrefs.getInt("tam_" + n, 100) - 50);
        box.addView(size);

        box.addView(label(R.string.rmxp_edit_opacity));
        SeekBar opacity = new SeekBar(mActivity);
        opacity.setMax(90); // 10 % a 100 %
        opacity.setProgress(mPrefs.getInt("opa_" + n, 100) - 10);
        box.addView(opacity);

        Switch visible = new Switch(mActivity);
        visible.setText(R.string.rmxp_edit_visible);
        visible.setChecked(!hidden(control));
        box.addView(visible);

        SeekBar.OnSeekBarChangeListener live = new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                float scale = mGlobalSize * (size.getProgress() + 50) / 100f;
                control.setScaleX(scale);
                control.setScaleY(scale);
                control.setAlpha((opacity.getProgress() + 10) / 100f);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        };
        size.setOnSeekBarChangeListener(live);
        opacity.setOnSeekBarChangeListener(live);

        new AlertDialog.Builder(mActivity)
            .setView(box)
            .setPositiveButton(R.string.rmxp_edit_done, (d, w) -> {
                mPrefs.edit()
                    .putInt("tam_" + n, size.getProgress() + 50)
                    .putInt("opa_" + n, opacity.getProgress() + 10)
                    .putBoolean("oculto_" + n, !visible.isChecked()).apply();
                apply(Math.round(mGlobalSize * 100));
            })
            .setOnCancelListener(d -> apply(Math.round(mGlobalSize * 100)))
            .show();
    }

    private TextView label(int text)
    {
        TextView t = new TextView(mActivity);
        t.setText(text);
        t.setPadding(0, Math.round(12 * mActivity.getResources().getDisplayMetrics().density), 0, 0);
        return t;
    }

    private void chooseKey(GamepadButton button, Button shown)
    {
        String[] labels = new String[KEYS.length];
        for (int i = 0; i < KEYS.length; i++)
            labels[i] = Gamepad.keyLabel(KEYS[i]);
        new AlertDialog.Builder(mActivity)
            .setTitle(R.string.rmxp_edit_choose)
            .setItems(labels, (d, which) -> {
                mGamepad.setButtonKey(button, KEYS[which]);
                mPrefs.edit().putInt("tecla_" + name(button), KEYS[which]).apply();
                shown.setText(mActivity.getString(R.string.rmxp_edit_key) + ": " + labels[which]);
            })
            .show();
    }

    private void reset()
    {
        SharedPreferences.Editor ed = mPrefs.edit();
        for (View v : controls()) {
            String n = name(v);
            ed.remove("x_" + n).remove("y_" + n).remove("tam_" + n).remove("opa_" + n)
              .remove("oculto_" + n).remove("tecla_" + n);
        }
        ed.apply();
        mGamepad.resetKeys();
        apply(Math.round(mGlobalSize * 100));
    }
}
