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
    public interface VisibilitySink { void accept(boolean shown); }
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
        { { "Esc", KeyEvent.KEYCODE_ESCAPE, null, 1f }, { "Tab", KeyEvent.KEYCODE_TAB, null, 1f },
          { "Ctrl", KeyEvent.KEYCODE_CTRL_LEFT, null, 1f }, { "Alt", KeyEvent.KEYCODE_ALT_LEFT, null, 1f },
          { "Espacio", KeyEvent.KEYCODE_SPACE, " ", 3f },
          { "←", KeyEvent.KEYCODE_DPAD_LEFT, null, 1f }, { "↑", KeyEvent.KEYCODE_DPAD_UP, null, 1f },
          { "↓", KeyEvent.KEYCODE_DPAD_DOWN, null, 1f }, { "→", KeyEvent.KEYCODE_DPAD_RIGHT, null, 1f },
          { "⇕", -2, null, 1f }, { "▼", -3, null, 1f } },
    };
    private static Object[] nav(String label, int code) { return new Object[] {label, code, null, 1f}; }
    private static final Object[][][] FUNCTIONS = {
        {nav("F1", KeyEvent.KEYCODE_F1), nav("F2", KeyEvent.KEYCODE_F2), nav("F3", KeyEvent.KEYCODE_F3), nav("F4", KeyEvent.KEYCODE_F4), nav("F5", KeyEvent.KEYCODE_F5), nav("F6", KeyEvent.KEYCODE_F6)},
        {nav("F7", KeyEvent.KEYCODE_F7), nav("F8", KeyEvent.KEYCODE_F8), nav("F9", KeyEvent.KEYCODE_F9), nav("F10", KeyEvent.KEYCODE_F10), nav("F11", KeyEvent.KEYCODE_F11), nav("F12", KeyEvent.KEYCODE_F12)},
        {nav("Home", KeyEvent.KEYCODE_MOVE_HOME), nav("End", KeyEvent.KEYCODE_MOVE_END), nav("PgUp", KeyEvent.KEYCODE_PAGE_UP), nav("PgDn", KeyEvent.KEYCODE_PAGE_DOWN), nav("Ins", KeyEvent.KEYCODE_INSERT), nav("Supr", KeyEvent.KEYCODE_FORWARD_DEL)},
        FILAS[4]
    };
    private static final Object[][][] SYMBOLS = {
        {k("1"), k("2"), k("3"), k("4"), k("5"), k("6"), k("7"), k("8"), k("9"), k("0")},
        {{"@",KeyEvent.KEYCODE_AT,"@",1f},{"#",KeyEvent.KEYCODE_POUND,"#",1f},{"/",KeyEvent.KEYCODE_SLASH,"/",1f},{"=",KeyEvent.KEYCODE_EQUALS,"=",1f},{"+",KeyEvent.KEYCODE_PLUS,"+",1f},{";",KeyEvent.KEYCODE_SEMICOLON,";",1f}},
        {{"[",KeyEvent.KEYCODE_LEFT_BRACKET,"[",1f},{"]",KeyEvent.KEYCODE_RIGHT_BRACKET,"]",1f},{"á",0,"á",1f},{"é",0,"é",1f},{"í",0,"í",1f},{"ó",0,"ó",1f},{"ú",0,"ú",1f},{"ü",0,"ü",1f},nav("⌫",KeyEvent.KEYCODE_DEL)},
        FILAS[4]
    };
    private static final float[] ALTOS = { 0.46f, 0.58f, 0.70f }; // fracción del alto de pantalla
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
    private final int mAccent;
    private LinearLayout mPanel;
    private boolean mShift, mDismissTouch;
    private int mPage;
    private final java.util.Set<Integer> mDown = new java.util.HashSet<>();
    private final java.util.Set<Integer> mModifiers = new java.util.HashSet<>();
    private final VisibilitySink mVisibility;


    public Teclado(Activity activity, SharedPreferences prefs, VisibilitySink visibility)
    {
        mActivity = activity;
        mVisibility = visibility;
        mPrefs = prefs;
        mAccent = activity.getColor(R.color.rmxp_ambar);
    }

    private float dp(float v) { return v * mActivity.getResources().getDisplayMetrics().density; }

    public void attachTo(ViewGroup parent)
    {
        mPanel = new LinearLayout(mActivity);
        mPanel.setOrientation(LinearLayout.VERTICAL);
        int p = Math.round(dp(4));
        mPanel.setPadding(p, p, p, p);
        mPanel.setClickable(true); // los toques no pasan al juego
        mPanel.setVisibility(View.GONE);
        // La capa de SDL es un RelativeLayout: hay que pegarlo abajo así (Gravity no aplica)
        android.widget.RelativeLayout.LayoutParams lp = new android.widget.RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        lp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM);
        parent.addView(mPanel, lp);
        build();
        SDLActivity.sKeyboardHook = this;
    }

    private void build()
    {
        releaseKeys(false);
        mPanel.setBackgroundColor(mActivity.getColor(R.color.rmxp_tinta));
        mPanel.removeAllViews();
        LinearLayout tabs = new LinearLayout(mActivity);
        String[] pages = { "ABC", "#+=", "F1–F12" };
        for (int i = 0; i < pages.length; i++) {
            final int page = i;
            TextView tab = new TextView(mActivity);
            tab.setText(pages[i]); tab.setGravity(Gravity.CENTER);
            tab.setTextColor(mActivity.getColor(i == mPage ? R.color.rmxp_ambar : R.color.rmxp_niebla));
            tab.setOnClickListener(v -> { mPage = page; build(); });
            tabs.addView(tab, new LinearLayout.LayoutParams(0, Math.round(dp(36)), 1));
        }
        mPanel.addView(tabs);
        Object[][][] rows = mPage == 0 ? FILAS : mPage == 1 ? SYMBOLS : FUNCTIONS;
        for (Object[][] fila : rows) {
            LinearLayout row = new LinearLayout(mActivity);
            for (Object[] key : fila) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, (float) key[3]);
                int gap = Math.round(dp(2.5f));
                lp.setMargins(gap, gap, gap, gap);
                row.addView(keyView(key), lp);
            }
            mPanel.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        int size = Math.max(0, Math.min(ALTOS.length - 1, mPrefs.getInt("teclado_tam", 1)));
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
        boolean on = code == SHIFT && mShift;
        v.setTextColor(mActivity.getColor(on || mModifiers.contains(code) ? R.color.rmxp_tinta : R.color.rmxp_niebla));
        v.setTextSize(14);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8));
        int normal = mActivity.getColor(on || mModifiers.contains(code) ? R.color.rmxp_ambar : R.color.rmxp_pizarra);
        int pressed = mAccent;
        bg.setColor(normal);
        v.setBackground(bg);

        Runnable[] repeat = new Runnable[1];
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    Haptics.pulse(view);
                    bg.setColor(pressed);
                    if (code < 0) {
                        special(code);
                        return true;
                    }
                    if (code == KeyEvent.KEYCODE_CTRL_LEFT || code == KeyEvent.KEYCODE_ALT_LEFT) {
                        if (mModifiers.remove(code)) SDLActivity.onNativeKeyUp(code);
                        else { mModifiers.add(code); SDLActivity.onNativeKeyDown(code); }
                        mHandler.post(this::build);
                        return true;
                    }
                    if (code != 0) {
                        mDown.add(code);
                        SDLActivity.onNativeKeyDown(code);
                    }
                    if (text != null && mModifiers.isEmpty())
                        SDLActivity.commitText(letter && mShift ? text.toUpperCase() : text);
                    if (letter && mShift) {
                        mShift = false;
                        SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_SHIFT_LEFT);
                        mHandler.post(this::build);
                    }
                    // Borrar y flechas se repiten al dejarlas presionadas
                    if (code == KeyEvent.KEYCODE_DEL || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
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
                    bg.setColor(normal);
                    if (repeat[0] != null)
                        mHandler.removeCallbacks(repeat[0]);
                    if (code > 0 && mDown.remove(code))
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
            if (mShift) SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_SHIFT_LEFT);
            else SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_SHIFT_LEFT);
        } else if (code == TAMANO) {
            mPrefs.edit().putInt("teclado_tam", (mPrefs.getInt("teclado_tam", 1) + 1) % ALTOS.length).apply();
        } else if (code == CERRAR) {
            hide();
            return;
        }
        mHandler.post(this::build);
    }

    private void releaseKeys(boolean modifiers)
    {
        mHandler.removeCallbacksAndMessages(null);
        for (int key : mDown) SDLActivity.onNativeKeyUp(key);
        mDown.clear();
        if (modifiers) {
            for (int key : mModifiers) SDLActivity.onNativeKeyUp(key);
            mModifiers.clear();
            if (mShift) SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_SHIFT_LEFT);
            mShift = false;
        }
    }

    /** Consume the dismissal gesture so the same tap cannot also press a game control. */
    public boolean onOutsideTouch(MotionEvent event)
    {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && shown()) {
            int[] xy = new int[2];
            mPanel.getLocationOnScreen(xy);
            if (event.getRawY() < xy[1]) { hide(); mDismissTouch = true; }
        }
        if (!mDismissTouch) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL)
            mDismissTouch = false;
        return true;
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
        if (mPanel != null) {
            build(); // por si cambió el estilo
            mPanel.setVisibility(View.VISIBLE);
            mVisibility.accept(true);
        }
    }

    @Override
    public void hide()
    {
        boolean wasShown = shown();
        releaseKeys(true);
        if (mPanel != null) mPanel.setVisibility(View.GONE);
        if (wasShown) mVisibility.accept(false);
    }

    @Override
    public boolean shown()
    {
        return mPanel != null && mPanel.getVisibility() == View.VISIBLE;
    }
}
