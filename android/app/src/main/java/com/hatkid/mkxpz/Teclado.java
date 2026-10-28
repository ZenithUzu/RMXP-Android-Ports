package com.hatkid.mkxpz;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.libsdl.app.SDLActivity;

/**
 * RMXP-Android-Ports: teclado de PC en pantalla. Reemplaza al teclado de Android (que no
 * borraba): manda teclas reales (Borrar, Enter, flechas...) y el texto de cada letra.
 * Aparece con el botón de teclado o cuando el juego pide escribir (por ejemplo, un nombre).
 */
public class Teclado implements SDLActivity.KeyboardHook
{
    // Etiqueta, tecla de Android (0 = solo texto), texto que escribe (null = ninguno), ancho
    private static final Object[][][] FILAS = {
        { k("1"), k("2"), k("3"), k("4"), k("5"), k("6"), k("7"), k("8"), k("9"), k("0"),
          { "⌫", KeyEvent.KEYCODE_DEL, null, 1.5f } },
        { k("q"), k("w"), k("e"), k("r"), k("t"), k("y"), k("u"), k("i"), k("o"), k("p"),
          { "ñ", 0, "ñ", 1f } },
        { k("a"), k("s"), k("d"), k("f"), k("g"), k("h"), k("j"), k("k"), k("l"),
          { "Enter", KeyEvent.KEYCODE_ENTER, null, 2f } },
        { { "⇧", -1, null, 1.5f }, k("z"), k("x"), k("c"), k("v"), k("b"), k("n"), k("m"),
          { ",", KeyEvent.KEYCODE_COMMA, ",", 1f }, { ".", KeyEvent.KEYCODE_PERIOD, ".", 1f },
          { "-", KeyEvent.KEYCODE_MINUS, "-", 1f } },
        { { "Esc", KeyEvent.KEYCODE_ESCAPE, null, 1.5f }, { "⇕", -2, null, 1f },
          { "", KeyEvent.KEYCODE_SPACE, " ", 5f },
          { "←", KeyEvent.KEYCODE_DPAD_LEFT, null, 1f }, { "→", KeyEvent.KEYCODE_DPAD_RIGHT, null, 1f },
          { "▼", -3, null, 1.5f } },
    };
    private static final float[] ALTOS = { 0.36f, 0.46f, 0.56f }; // fracción del alto de pantalla
    private static final int SHIFT = -1, TAMANO = -2, CERRAR = -3;

    private static Object[] k(String letra)
    {
        char c = letra.charAt(0);
        int code = Character.isDigit(c) ? KeyEvent.KEYCODE_0 + (c - '0') : KeyEvent.KEYCODE_A + (c - 'a');
        return new Object[] { letra, code, letra, 1f };
    }

    private final Activity mActivity;
    private final SharedPreferences mPrefs;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final int mAccent, mKey, mText;
    private LinearLayout mPanel;
    private boolean mShift;

    public Teclado(Activity activity, SharedPreferences prefs)
    {
        mActivity = activity;
        mPrefs = prefs;
        mAccent = activity.getColor(R.color.rmxp_ambar);
        mKey = activity.getColor(R.color.rmxp_pizarra);
        mText = activity.getColor(R.color.rmxp_niebla);
    }

    private float dp(float v) { return v * mActivity.getResources().getDisplayMetrics().density; }

    public void attachTo(ViewGroup parent)
    {
        mPanel = new LinearLayout(mActivity);
        mPanel.setOrientation(LinearLayout.VERTICAL);
        mPanel.setBackgroundColor(0xE6000000 | (mActivity.getColor(R.color.rmxp_tinta) & 0xFFFFFF));
        int p = Math.round(dp(4));
        mPanel.setPadding(p, p, p, p);
        mPanel.setClickable(true); // los toques no pasan al juego
        mPanel.setVisibility(View.GONE);
        parent.addView(mPanel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM));
        build();
        SDLActivity.sKeyboardHook = this;
    }

    private void build()
    {
        mPanel.removeAllViews();
        for (Object[][] fila : FILAS) {
            LinearLayout row = new LinearLayout(mActivity);
            for (Object[] key : fila) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, (float) key[3]);
                int gap = Math.round(dp(2.5f));
                lp.setMargins(gap, gap, gap, gap);
                row.addView(keyView(key), lp);
            }
            mPanel.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        int size = mPrefs.getInt("teclado_tam", 1);
        int h = mActivity.getResources().getDisplayMetrics().heightPixels;
        int w = mActivity.getResources().getDisplayMetrics().widthPixels;
        mPanel.getLayoutParams().height = Math.round(Math.min(h, w) * ALTOS[size]);
        mPanel.requestLayout();
    }

    @SuppressLint("ClickableViewAccessibility")
    private View keyView(Object[] key)
    {
        String label = (String) key[0];
        int code = (int) key[1];
        String text = (String) key[2];
        boolean letter = text != null && Character.isLetter(text.charAt(0));

        TextView v = new TextView(mActivity);
        v.setText(letter && mShift ? label.toUpperCase() : label);
        v.setGravity(Gravity.CENTER);
        v.setTextColor(code == SHIFT && mShift ? mActivity.getColor(R.color.rmxp_tinta) : mText);
        v.setTextSize(letter || text != null ? 18 : 15);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8));
        bg.setColor(code == SHIFT && mShift ? mAccent : mKey);
        v.setBackground(bg);

        Runnable[] repeat = new Runnable[1];
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    bg.setAlpha(150);
                    if (code < 0) {
                        special(code);
                        return true;
                    }
                    if (code != 0)
                        SDLActivity.onNativeKeyDown(code);
                    if (text != null)
                        SDLActivity.commitText(letter && mShift ? text.toUpperCase() : text);
                    if (letter && mShift) {
                        mShift = false; // mayúscula de una sola letra, como en el teléfono
                        mHandler.post(this::build);
                    }
                    // Borrar y flechas se repiten al dejarlas presionadas
                    if (code == KeyEvent.KEYCODE_DEL || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        repeat[0] = new Runnable() {
                            public void run() {
                                SDLActivity.onNativeKeyDown(code);
                                mHandler.postDelayed(this, 60);
                            }
                        };
                        mHandler.postDelayed(repeat[0], 400);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    bg.setAlpha(255);
                    if (repeat[0] != null)
                        mHandler.removeCallbacks(repeat[0]);
                    if (code > 0)
                        SDLActivity.onNativeKeyUp(code);
                    return true;
            }
            return true;
        });
        return v;
    }

    private void special(int code)
    {
        if (code == SHIFT) {
            mShift = !mShift;
        } else if (code == TAMANO) {
            mPrefs.edit().putInt("teclado_tam", (mPrefs.getInt("teclado_tam", 1) + 1) % ALTOS.length).apply();
        } else if (code == CERRAR) {
            hide();
            return;
        }
        mHandler.post(this::build);
    }

    // ---------------------------------------------------------------- mostrar / ocultar

    public void toggle()
    {
        if (shown())
            hide();
        else
            show();
    }

    @Override
    public void show()
    {
        if (mPanel != null)
            mPanel.setVisibility(View.VISIBLE);
    }

    @Override
    public void hide()
    {
        if (mPanel != null)
            mPanel.setVisibility(View.GONE);
    }

    @Override
    public boolean shown()
    {
        return mPanel != null && mPanel.getVisibility() == View.VISIBLE;
    }
}
