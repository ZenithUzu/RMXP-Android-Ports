package com.hatkid.mkxpz.controles;

import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Un control en pantalla: botón de tecla, acción (teclado, turbo...) o cruceta. */
public class Control
{
    // Acciones (números negativos para no chocar con las teclas de Android)
    public static final int TECLADO = -1, TURBO = -2, MENU = -3, OCULTAR = -4;

    public String id;
    public boolean cruceta;
    public int tecla;           // KeyEvent.KEYCODE_* o una acción
    public String etiqueta;
    public float x, y;          // centro, como fracción del ancho/alto de la pantalla
    public int tam;             // dp
    public int color;           // ARGB
    public float opacidad;      // 0.1 - 1
    public boolean pastilla;    // forma alargada en vez de círculo

    static Control boton(String id, String etiqueta, int tecla, float x, float y, int tam, boolean pastilla)
    {
        Control c = new Control();
        c.id = id; c.etiqueta = etiqueta; c.tecla = tecla; c.x = x; c.y = y; c.tam = tam;
        c.pastilla = pastilla; c.color = 0xFFFFFFFF; c.opacidad = 1f;
        return c;
    }

    /** Distribución inicial: cómoda en horizontal, parecida a un mando. */
    public static List<Control> predeterminados()
    {
        List<Control> l = new ArrayList<>();
        Control dpad = boton("cruceta", "", 0, 0.13f, 0.72f, 150, false);
        dpad.cruceta = true;
        l.add(dpad);
        l.add(boton("a", "A", KeyEvent.KEYCODE_C, 0.92f, 0.70f, 72, false));       // confirmar
        l.add(boton("b", "B", KeyEvent.KEYCODE_X, 0.84f, 0.86f, 72, false));       // cancelar
        l.add(boton("x", "X", KeyEvent.KEYCODE_Z, 0.84f, 0.54f, 60, false));       // acción
        l.add(boton("y", "Y", KeyEvent.KEYCODE_D, 0.76f, 0.70f, 60, false));       // especial
        l.add(boton("l", "L", KeyEvent.KEYCODE_Q, 0.07f, 0.12f, 64, true));
        l.add(boton("r", "R", KeyEvent.KEYCODE_W, 0.93f, 0.12f, 64, true));
        l.add(boton("esc", "Esc", KeyEvent.KEYCODE_ESCAPE, 0.40f, 0.92f, 60, true));
        l.add(boton("enter", "Enter", KeyEvent.KEYCODE_ENTER, 0.60f, 0.92f, 60, true));
        l.add(boton("shift", "Shift", KeyEvent.KEYCODE_SHIFT_LEFT, 0.50f, 0.92f, 60, true));
        l.add(boton("teclado", "", TECLADO, 0.44f, 0.08f, 44, false));
        l.add(boton("turbo", "", TURBO, 0.56f, 0.08f, 44, false));
        return l;
    }

    JSONObject toJson() throws JSONException
    {
        return new JSONObject().put("id", id).put("cruceta", cruceta).put("tecla", tecla)
            .put("etiqueta", etiqueta).put("x", x).put("y", y).put("tam", tam)
            .put("color", color).put("opacidad", opacidad).put("pastilla", pastilla);
    }

    static Control fromJson(JSONObject o) throws JSONException
    {
        Control c = boton(o.getString("id"), o.optString("etiqueta"), o.getInt("tecla"),
            (float) o.getDouble("x"), (float) o.getDouble("y"), o.getInt("tam"), o.optBoolean("pastilla"));
        c.cruceta = o.optBoolean("cruceta");
        c.color = o.optInt("color", 0xFFFFFFFF);
        c.opacidad = (float) o.optDouble("opacidad", 1);
        return c;
    }

    public static String toJson(List<Control> list)
    {
        JSONArray a = new JSONArray();
        try {
            for (Control c : list)
                a.put(c.toJson());
        } catch (JSONException ignored) {
        }
        return a.toString();
    }

    public static List<Control> fromJson(String json)
    {
        List<Control> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++)
                l.add(fromJson(a.getJSONObject(i)));
        } catch (JSONException e) {
            return predeterminados();
        }
        return l;
    }
}
