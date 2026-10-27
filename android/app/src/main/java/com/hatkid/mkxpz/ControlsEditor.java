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
import android.widget.TextView;

import com.hatkid.mkxpz.gamepad.Gamepad;
import com.hatkid.mkxpz.gamepad.GamepadButton;

/**
 * RMXP-Android-Ports: modo para editar los controles en pantalla.
 * Arrastrar un grupo (cruceta, botones) lo mueve; tocar un botón cambia su tecla.
 * Posiciones y teclas se guardan en las preferencias y se aplican al abrir el juego.
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
    private final Runnable mOnDone;
    private View mOverlay;

    public ControlsEditor(Activity activity, Gamepad gamepad, SharedPreferences prefs, Runnable onDone)
    {
        mActivity = activity;
        mGamepad = gamepad;
        mPrefs = prefs;
        mOnDone = onDone;
    }

    private String name(View v)
    {
        return mActivity.getResources().getResourceEntryName(v.getId());
    }

    /** Aplica las posiciones y teclas guardadas (al abrir la app). */
    public void applySaved()
    {
        for (View group : mGamepad.movableGroups()) {
            group.setTranslationX(mPrefs.getFloat("x_" + name(group), 0));
            group.setTranslationY(mPrefs.getFloat("y_" + name(group), 0));
        }
        for (GamepadButton b : mGamepad.buttons()) {
            int key = mPrefs.getInt("tecla_" + name(b), 0);
            if (key != 0)
                mGamepad.setButtonKey(b, key);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    public void start(ViewGroup layout)
    {
        float dp = mActivity.getResources().getDisplayMetrics().density;
        FrameLayout overlay = new FrameLayout(mActivity);
        overlay.setBackgroundColor(0x33000000);

        // Barra de instrucciones arriba
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
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        // Arrastrar y tocar
        float slop = 8 * dp;
        overlay.setOnTouchListener(new View.OnTouchListener() {
            View dragging;
            float startX, startY, baseX, baseY;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dragging = groupAt(e.getRawX(), e.getRawY());
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
                        if (moved) {
                            mPrefs.edit()
                                .putFloat("x_" + name(dragging), dragging.getTranslationX())
                                .putFloat("y_" + name(dragging), dragging.getTranslationY()).apply();
                        } else {
                            GamepadButton b = buttonAt(e.getRawX(), e.getRawY());
                            if (b != null)
                                chooseKey(b);
                        }
                        dragging = null;
                        return true;
                }
                return false;
            }
        });

        mOverlay = overlay;
        layout.addView(overlay, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void stop(ViewGroup layout)
    {
        layout.removeView(mOverlay);
        mOverlay = null;
        mOnDone.run();
    }

    public boolean isActive() { return mOverlay != null; }

    private boolean contains(View v, float rawX, float rawY)
    {
        Rect r = new Rect();
        return v.getVisibility() == View.VISIBLE && v.getGlobalVisibleRect(r) && r.contains((int) rawX, (int) rawY);
    }

    private View groupAt(float x, float y)
    {
        for (View g : mGamepad.movableGroups())
            if (contains(g, x, y))
                return g;
        return null;
    }

    private GamepadButton buttonAt(float x, float y)
    {
        for (GamepadButton b : mGamepad.buttons())
            if (contains(b, x, y))
                return b;
        return null;
    }

    private void chooseKey(GamepadButton button)
    {
        String[] labels = new String[KEYS.length];
        for (int i = 0; i < KEYS.length; i++)
            labels[i] = Gamepad.keyLabel(KEYS[i]);
        new AlertDialog.Builder(mActivity)
            .setTitle(R.string.rmxp_edit_choose)
            .setItems(labels, (d, which) -> {
                mGamepad.setButtonKey(button, KEYS[which]);
                mPrefs.edit().putInt("tecla_" + name(button), KEYS[which]).apply();
            })
            .show();
    }

    private void reset()
    {
        SharedPreferences.Editor ed = mPrefs.edit();
        for (View g : mGamepad.movableGroups()) {
            g.setTranslationX(0);
            g.setTranslationY(0);
            ed.remove("x_" + name(g)).remove("y_" + name(g));
        }
        for (GamepadButton b : mGamepad.buttons())
            ed.remove("tecla_" + name(b));
        ed.apply();
        mGamepad.resetKeys();
    }
}
