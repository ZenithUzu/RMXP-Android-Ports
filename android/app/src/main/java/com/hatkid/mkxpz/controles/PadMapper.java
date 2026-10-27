package com.hatkid.mkxpz.controles;

import android.content.SharedPreferences;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mando físico → teclas del juego. SDL no entiende los botones de mando como teclas, así que
 * los traducimos aquí, con valores cómodos por defecto (A confirma, B cancela) que se pueden
 * cambiar en "Mando y teclado". El teclado físico pasa directo al juego.
 */
public class PadMapper
{
    /** Botones del mando, con el nombre que se muestra y su tecla por defecto. */
    public static final Map<Integer, String> BOTONES = new LinkedHashMap<>();
    private static final Map<Integer, Integer> POR_DEFECTO = new LinkedHashMap<>();

    static {
        def(KeyEvent.KEYCODE_BUTTON_A, "A", KeyEvent.KEYCODE_C);          // confirmar
        def(KeyEvent.KEYCODE_BUTTON_B, "B", KeyEvent.KEYCODE_X);          // cancelar
        def(KeyEvent.KEYCODE_BUTTON_X, "X", KeyEvent.KEYCODE_Z);          // acción
        def(KeyEvent.KEYCODE_BUTTON_Y, "Y", KeyEvent.KEYCODE_D);          // especial
        def(KeyEvent.KEYCODE_BUTTON_L1, "L1", KeyEvent.KEYCODE_Q);
        def(KeyEvent.KEYCODE_BUTTON_R1, "R1", KeyEvent.KEYCODE_W);
        def(KeyEvent.KEYCODE_BUTTON_L2, "L2", KeyEvent.KEYCODE_SHIFT_LEFT);
        def(KeyEvent.KEYCODE_BUTTON_R2, "R2", Control.TURBO);
        def(KeyEvent.KEYCODE_BUTTON_START, "Start", KeyEvent.KEYCODE_ENTER);
        def(KeyEvent.KEYCODE_BUTTON_SELECT, "Select", Control.MENU);
        def(KeyEvent.KEYCODE_BUTTON_THUMBL, "L3", KeyEvent.KEYCODE_ESCAPE);
        def(KeyEvent.KEYCODE_BUTTON_THUMBR, "R3", KeyEvent.KEYCODE_F5);
    }

    private static void def(int button, String name, int key)
    {
        BOTONES.put(button, name);
        POR_DEFECTO.put(button, key);
    }

    private final SharedPreferences mPrefs;
    private final TouchControls.Actions mActions;
    private int mAxisDir; // flechas del stick o cruceta analógica: bits 1 arriba, 2 abajo, 4 izq., 8 der.

    public PadMapper(SharedPreferences prefs, TouchControls.Actions actions)
    {
        mPrefs = prefs;
        mActions = actions;
    }

    public int keyFor(int button)
    {
        Integer def = POR_DEFECTO.get(button);
        return mPrefs.getInt("mando_" + button, def == null ? 0 : def);
    }

    public void setKey(int button, int key) { mPrefs.edit().putInt("mando_" + button, key).apply(); }

    public void reset()
    {
        SharedPreferences.Editor e = mPrefs.edit();
        for (int b : BOTONES.keySet())
            e.remove("mando_" + b);
        e.apply();
    }

    public static boolean isGamepad(InputDevice d)
    {
        if (d == null)
            return false;
        int s = d.getSources();
        return (s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
            || (s & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    /** Traduce un botón del mando. Devuelve true si lo manejó. */
    public boolean onKey(KeyEvent e)
    {
        if (!isGamepad(e.getDevice()))
            return false;
        int code = e.getKeyCode();
        int key;
        if (code == KeyEvent.KEYCODE_BACK)                  // algunos mandos mandan Atrás con B
            key = keyFor(KeyEvent.KEYCODE_BUTTON_B);
        else if (BOTONES.containsKey(code))
            key = keyFor(code);
        else if (code >= KeyEvent.KEYCODE_DPAD_UP && code <= KeyEvent.KEYCODE_DPAD_RIGHT)
            key = code;                                     // la cruceta ya son flechas
        else
            return false;
        if (e.getRepeatCount() > 0)
            return true;
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (key > 0) {
            if (down) mActions.keyDown(key); else mActions.keyUp(key);
        } else if (key < 0 && down) {
            mActions.action(key);
        }
        return true;
    }

    /** Cruceta analógica (HAT) y stick izquierdo → flechas. */
    public boolean onMotion(MotionEvent e)
    {
        if (!isGamepad(e.getDevice()) || e.getActionMasked() != MotionEvent.ACTION_MOVE)
            return false;
        float x = e.getAxisValue(MotionEvent.AXIS_HAT_X), y = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if (Math.abs(x) < 0.5f && Math.abs(y) < 0.5f) {
            x = e.getAxisValue(MotionEvent.AXIS_X);
            y = e.getAxisValue(MotionEvent.AXIS_Y);
        }
        int dir = (y < -0.5f ? 1 : 0) | (y > 0.5f ? 2 : 0) | (x < -0.5f ? 4 : 0) | (x > 0.5f ? 8 : 0);
        int[] keys = { KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT };
        for (int i = 0; i < 4; i++) {
            boolean was = (mAxisDir & (1 << i)) != 0, is = (dir & (1 << i)) != 0;
            if (is && !was) mActions.keyDown(keys[i]);
            if (was && !is) mActions.keyUp(keys[i]);
        }
        mAxisDir = dir;
        return true;
    }
}
