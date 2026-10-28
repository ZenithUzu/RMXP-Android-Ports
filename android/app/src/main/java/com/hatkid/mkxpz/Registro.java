package com.hatkid.mkxpz;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * RMXP-Android-Ports: guarda el registro (log) de la app en un .txt, sin cable ni adb.
 * Una app solo puede leer sus propias líneas de logcat, que son justo las que hacen falta
 * (también las de la vez anterior si se cerró). Se agrega el errorlog.txt del juego si existe.
 * Idea tomada de Klisee/pokemon-reloaded-android (Ajustes → Logs).
 */
public class Registro
{
    public static final int GUARDAR = 4203;
    private final Activity mActivity;
    private final File mGameDir;

    public Registro(Activity activity, File gameDir)
    {
        mActivity = activity;
        mGameDir = gameDir;
    }

    public void save()
    {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/plain");
        String fecha = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
        i.putExtra(Intent.EXTRA_TITLE, "log-" + fecha + ".txt");
        mActivity.startActivityForResult(i, GUARDAR);
    }

    /** Llamar desde onActivityResult. Devuelve true si era nuestro. */
    public boolean onResult(int request, int result, Intent data)
    {
        if (request != GUARDAR)
            return false;
        if (result != Activity.RESULT_OK || data == null)
            return true;
        Uri uri = data.getData();
        try (OutputStream out = mActivity.getContentResolver().openOutputStream(uri)) {
            Process p = Runtime.getRuntime().exec(new String[] { "logcat", "-d", "-v", "threadtime" });
            try (InputStream in = p.getInputStream()) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0)
                    out.write(buf, 0, n);
            }
            // errorlog.txt de Essentials: en la carpeta de datos (v19+) o en la del juego
            for (File f : new File[] { new File(mActivity.getFilesDir(), "errorlog.txt"), new File(mGameDir, "errorlog.txt") }) {
                if (f.isFile()) {
                    out.write(("\n===== " + f.getName() + " =====\n").getBytes());
                    out.write(Files.readAllBytes(f.toPath()));
                }
            }
            Toast.makeText(mActivity, R.string.rmxp_log_saved, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(mActivity, mActivity.getString(R.string.rmxp_saves_error) + " " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        return true;
    }
}
