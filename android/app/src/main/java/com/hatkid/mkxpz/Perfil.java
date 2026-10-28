package com.hatkid.mkxpz;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;

/**
 * RMXP-Android-Ports: perfil de controles en un archivo (distribución, estilo, colores, tamaños,
 * mando). Para pasarlo a otro juego o compartirlo. Idea de Pokémon Reloaded en Android.
 */
public class Perfil
{
    public static final int EXPORTAR = 4205, IMPORTAR = 4206;
    // Lo que no es de los controles (velocidad, pantalla...) no viaja en el perfil
    private static final String[] FUERA = { "velocidad", "estirar", "hz", "fps", "pantalla" };

    private final Activity mActivity;
    private final SharedPreferences mPrefs;
    private final Runnable mListo;

    public Perfil(Activity a, SharedPreferences prefs, Runnable listo) { mActivity = a; mPrefs = prefs; mListo = listo; }

    public void exportar()
    {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json").putExtra(Intent.EXTRA_TITLE, "controles-rmxp.json");
        mActivity.startActivityForResult(i, EXPORTAR);
    }

    public void importar()
    {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        mActivity.startActivityForResult(i, IMPORTAR);
    }

    public boolean onResult(int req, int res, Intent data)
    {
        if (req != EXPORTAR && req != IMPORTAR)
            return false;
        if (res != Activity.RESULT_OK || data == null || data.getData() == null)
            return true;
        try {
            if (req == EXPORTAR) {
                JSONObject o = new JSONObject();
                for (Map.Entry<String, ?> e : mPrefs.getAll().entrySet())
                    if (!fuera(e.getKey()))
                        o.put(e.getKey(), new JSONObject().put("t", e.getValue().getClass().getSimpleName()).put("v", e.getValue()));
                try (OutputStream out = mActivity.getContentResolver().openOutputStream(data.getData())) {
                    out.write(o.toString(1).getBytes("UTF-8"));
                }
            } else {
                byte[] b;
                try (InputStream in = mActivity.getContentResolver().openInputStream(data.getData())) {
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                    b = bo.toByteArray();
                }
                JSONObject o = new JSONObject(new String(b, "UTF-8"));
                SharedPreferences.Editor ed = mPrefs.edit();
                for (Iterator<String> k = o.keys(); k.hasNext(); ) {
                    String key = k.next();
                    if (fuera(key)) continue;
                    JSONObject e = o.getJSONObject(key);
                    switch (e.getString("t")) {
                        case "Integer": ed.putInt(key, e.getInt("v")); break;
                        case "Boolean": ed.putBoolean(key, e.getBoolean("v")); break;
                        case "Float": ed.putFloat(key, (float) e.getDouble("v")); break;
                        case "Long": ed.putLong(key, e.getLong("v")); break;
                        default: ed.putString(key, e.getString("v"));
                    }
                }
                ed.apply();
                mListo.run();
            }
            Toast.makeText(mActivity, R.string.rmxp_ctrl_ok, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(mActivity, mActivity.getString(R.string.rmxp_saves_error) + " " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        return true;
    }

    private static boolean fuera(String k)
    {
        for (String f : FUERA)
            if (f.equals(k)) return true;
        return false;
    }
}
