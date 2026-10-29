package com.hatkid.mkxpz.controles;

import android.view.KeyEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/** Teclas que se pueden asignar, en el orden de un teclado, con su nombre corto. */
public final class Teclas
{
    public static final Map<Integer, String> TODAS = new LinkedHashMap<>();

    static {
        put(KeyEvent.KEYCODE_ESCAPE, "Esc");
        for (int i = 1; i <= 12; i++)
            put(KeyEvent.KEYCODE_F1 + i - 1, "F" + i);
        put(KeyEvent.KEYCODE_GRAVE, "`");
        for (int i = 1; i <= 9; i++)
            put(KeyEvent.KEYCODE_0 + i, String.valueOf(i));
        put(KeyEvent.KEYCODE_0, "0");
        put(KeyEvent.KEYCODE_MINUS, "-");
        put(KeyEvent.KEYCODE_EQUALS, "=");
        put(KeyEvent.KEYCODE_DEL, "⌫");
        put(KeyEvent.KEYCODE_TAB, "Tab");
        for (char ch : "QWERTYUIOP".toCharArray())
            put(KeyEvent.KEYCODE_A + ch - 'A', String.valueOf(ch));
        put(KeyEvent.KEYCODE_LEFT_BRACKET, "[");
        put(KeyEvent.KEYCODE_RIGHT_BRACKET, "]");
        put(KeyEvent.KEYCODE_BACKSLASH, "\\");
        put(KeyEvent.KEYCODE_CAPS_LOCK, "Bloq");
        for (char ch : "ASDFGHJKL".toCharArray())
            put(KeyEvent.KEYCODE_A + ch - 'A', String.valueOf(ch));
        put(KeyEvent.KEYCODE_SEMICOLON, ";");
        put(KeyEvent.KEYCODE_APOSTROPHE, "'");
        put(KeyEvent.KEYCODE_ENTER, "Enter");
        put(KeyEvent.KEYCODE_SHIFT_LEFT, "Shift");
        put(KeyEvent.KEYCODE_SHIFT_RIGHT, "Shift D");
        for (char ch : "ZXCVBNM".toCharArray())
            put(KeyEvent.KEYCODE_A + ch - 'A', String.valueOf(ch));
        put(KeyEvent.KEYCODE_COMMA, ",");
        put(KeyEvent.KEYCODE_PERIOD, ".");
        put(KeyEvent.KEYCODE_SLASH, "/");
        put(KeyEvent.KEYCODE_CTRL_LEFT, "Ctrl");
        put(KeyEvent.KEYCODE_CTRL_RIGHT, "Ctrl D");
        put(KeyEvent.KEYCODE_ALT_LEFT, "Alt");
        put(KeyEvent.KEYCODE_ALT_RIGHT, "Alt D");
        put(KeyEvent.KEYCODE_SPACE, "Espacio");
        put(KeyEvent.KEYCODE_DPAD_UP, "↑");
        put(KeyEvent.KEYCODE_DPAD_LEFT, "←");
        put(KeyEvent.KEYCODE_DPAD_DOWN, "↓");
        put(KeyEvent.KEYCODE_DPAD_RIGHT, "→");
        put(KeyEvent.KEYCODE_FORWARD_DEL, "Supr");
        put(KeyEvent.KEYCODE_PAGE_UP, "RePág");
        put(KeyEvent.KEYCODE_PAGE_DOWN, "AvPág");
        put(KeyEvent.KEYCODE_MOVE_HOME, "Inicio");
        put(KeyEvent.KEYCODE_MOVE_END, "Fin");
        put(KeyEvent.KEYCODE_INSERT, "Insert");
        for (int i = 0; i <= 9; i++)
            put(KeyEvent.KEYCODE_NUMPAD_0 + i, "Num " + i);
        put(KeyEvent.KEYCODE_NUMPAD_DIVIDE, "Num /");
        put(KeyEvent.KEYCODE_NUMPAD_MULTIPLY, "Num *");
        put(KeyEvent.KEYCODE_NUMPAD_SUBTRACT, "Num -");
        put(KeyEvent.KEYCODE_NUMPAD_ADD, "Num +");
        put(KeyEvent.KEYCODE_NUMPAD_DOT, "Num .");
        put(KeyEvent.KEYCODE_NUMPAD_ENTER, "Num Enter");
    }

    public static final int[] COMUNES = { KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_C,
        KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_SHIFT_LEFT,
        KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_Q,
        KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_F5,
        KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN };

    public static String uso(android.content.Context context, int key) {
        int text = 0;
        switch (key) {
            case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_C: case KeyEvent.KEYCODE_SPACE: text = com.hatkid.mkxpz.R.string.rmxp_key_use; break;
            case KeyEvent.KEYCODE_X: case KeyEvent.KEYCODE_ESCAPE: text = com.hatkid.mkxpz.R.string.rmxp_key_back; break;
            case KeyEvent.KEYCODE_Z: case KeyEvent.KEYCODE_SHIFT_LEFT: text = com.hatkid.mkxpz.R.string.rmxp_key_action; break;
            case KeyEvent.KEYCODE_A: text = com.hatkid.mkxpz.R.string.rmxp_key_page_up; break;
            case KeyEvent.KEYCODE_S: text = com.hatkid.mkxpz.R.string.rmxp_key_page_down; break;
            case KeyEvent.KEYCODE_D: text = com.hatkid.mkxpz.R.string.rmxp_key_special; break;
            case KeyEvent.KEYCODE_Q: return "Aux 1";
            case KeyEvent.KEYCODE_W: return "Aux 2";
            case KeyEvent.KEYCODE_F1: text = com.hatkid.mkxpz.R.string.rmxp_key_settings; break;
            case KeyEvent.KEYCODE_F5: text = com.hatkid.mkxpz.R.string.rmxp_key_item; break;
        }
        return text == 0 ? "" : context.getString(text);
    }

    private static void put(int code, String name) { TODAS.put(code, name); }

    private Teclas() {}

    /** Nombre de una tecla o acción ("" si no tiene). */
    public static String nombre(int tecla)
    {
        switch (tecla) {
            case Control.TECLADO: return "Teclado";
            case Control.TURBO: return "Turbo";
            case Control.MENU: return "Menú";
            case Control.OCULTAR: return "Ocultar";
            case 0: return "";
        }
        String n = TODAS.get(tecla);
        return n != null ? n : KeyEvent.keyCodeToString(tecla).replace("KEYCODE_", "");
    }
}
