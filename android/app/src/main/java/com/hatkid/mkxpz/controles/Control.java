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
    public int tam;             // talla: 0 pequeño, 1 mediano, 2 grande
    /** Alto en dp de cada talla (la cruceta es CRUCETA veces más grande; las alargadas, PASTILLA de ancho). */
    public static final int[] TALLAS = { 52, 64, 80 };
    public static final float CRUCETA = 2.3f, PASTILLA = 1.6f;
    /** Opacidades que se pueden elegir, en %. */
    public static final int[] OPACIDADES = { 25, 50, 75, 100 };
    public int color;           // ARGB
    public float opacidad;      // 0.1 - 1
    public boolean pastilla;    // forma alargada en vez de círculo
    public int mando;           // botón de mando que comparte la tecla (KEYCODE_BUTTON_*), 0 = ninguno

    static Control boton(String id, String etiqueta, int tecla, float x, float y, int tam, boolean pastilla)
    {
        Control c = new Control();
        c.id = id; c.etiqueta = etiqueta; c.tecla = tecla; c.x = x; c.y = y; c.tam = tam;
        c.pastilla = pastilla; c.color = 0xFFFFFFFF; c.opacidad = 1f;
        return c;
    }

    /**
     * Distribución inicial, medida en dp desde los bordes (como un mando): cruceta abajo a la
     * izquierda, A/B/X/Y en rombo abajo a la derecha, L/R arriba, Esc/Shift/Enter abajo al centro.
     * w y h son el tamaño de la pantalla en dp (horizontal).
     */
    public static List<Control> predeterminados(float w, float h)
    {
        List<Control> l = new ArrayList<>();
        float m = 20, s = TALLAS[1], d = s * 1.1f;          // margen, botón mediano, separación del rombo
        float cruz = s * CRUCETA;
        Control dpad = boton("cruceta", "", 0, (m + cruz / 2) / w, (h - m - cruz / 2) / h, 1, false);
        dpad.cruceta = true;
        l.add(dpad);
        float cx = w - m - s / 2 - d, cy = h - m - s / 2 - d; // centro del rombo
        l.add(boton("a", "A", KeyEvent.KEYCODE_C, (cx + d) / w, cy / h, 1, false));   // confirmar
        l.add(boton("b", "B", KeyEvent.KEYCODE_X, cx / w, (cy + d) / h, 1, false));   // cancelar
        l.add(boton("x", "X", KeyEvent.KEYCODE_Z, cx / w, (cy - d) / h, 1, false));   // acción
        l.add(boton("y", "Y", KeyEvent.KEYCODE_D, (cx - d) / w, cy / h, 1, false));   // especial
        float pw = s * PASTILLA;
        l.add(boton("l", "L", KeyEvent.KEYCODE_Q, (m + pw / 2) / w, (m + s / 2) / h, 1, true));
        l.add(boton("r", "R", KeyEvent.KEYCODE_W, (w - m - pw / 2) / w, (m + s / 2) / h, 1, true));
        for (Control c : l)
            c.mando = mandoPorDefecto(c.id);
        float by = (h - m - s / 2) / h, gap = pw + 12;
        l.add(boton("esc", "Esc", KeyEvent.KEYCODE_ESCAPE, (w / 2 - gap) / w, by, 1, true));
        l.add(boton("shift", "Shift", KeyEvent.KEYCODE_SHIFT_LEFT, 0.5f, by, 1, true));
        l.add(boton("enter", "Enter", KeyEvent.KEYCODE_ENTER, (w / 2 + gap) / w, by, 1, true));
        // Junto a L y R, hacia dentro: así no chocan con la cruceta ni con X en pantallas bajas
        float ay = (m + s / 2) / h, ax = m + pw + 16 + pw / 2;
        l.add(boton("teclado", "", TECLADO, ax / w, ay, 1, true)); // ancho, con forma de teclado
        l.add(boton("turbo", "", TURBO, (w - m - pw - 16 - s / 2) / w, ay, 1, false));
        return l;
    }

    /** Los botones A/B/X/Y/L/R comparten tecla con los del mando ("Mando y teclado"). */
    static int mandoPorDefecto(String id)
    {
        switch (id) {
            case "a": return KeyEvent.KEYCODE_BUTTON_A;
            case "b": return KeyEvent.KEYCODE_BUTTON_B;
            case "x": return KeyEvent.KEYCODE_BUTTON_X;
            case "y": return KeyEvent.KEYCODE_BUTTON_Y;
            case "l": return KeyEvent.KEYCODE_BUTTON_L1;
            case "r": return KeyEvent.KEYCODE_BUTTON_R1;
        }
        return 0;
    }

    JSONObject toJson() throws JSONException
    {
        return new JSONObject().put("id", id).put("cruceta", cruceta).put("tecla", tecla)
            .put("etiqueta", etiqueta).put("x", x).put("y", y).put("tam", tam)
            .put("color", color).put("opacidad", opacidad).put("pastilla", pastilla).put("mando", mando);
    }

    static Control fromJson(JSONObject o) throws JSONException
    {
        Control c = boton(o.getString("id"), o.optString("etiqueta"), o.getInt("tecla"),
            (float) o.getDouble("x"), (float) o.getDouble("y"), o.getInt("tam"), o.optBoolean("pastilla"));
        c.cruceta = o.optBoolean("cruceta");
        c.color = o.optInt("color", 0xFFFFFFFF);
        c.opacidad = (float) o.optDouble("opacidad", 1);
        c.mando = o.optInt("mando", mandoPorDefecto(c.id));
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
            return null;
        }
        return l;
    }
}
