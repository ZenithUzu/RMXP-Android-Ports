package com.hatkid.mkxpz;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.KeyEvent;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.libsdl.app.SDLActivity;

/**
 * RMXP-Android-Ports: exportar e importar partidas (.rxdata/.rvdata/.rvdata2), para pasarlas
 * de y hacia JoiPlay, Kirin o la PC. Son el mismo archivo en todos lados; solo cambia dónde vive.
 *  - Essentials v19+ guarda en la carpeta de datos de la app (files/).
 *  - Juegos más viejos guardan dentro de la carpeta del juego (files/game/).
 */
public class Partidas
{
    public static final int EXPORTAR = 4201, IMPORTAR = 4202;

    private final Activity mActivity;
    private final File mDataDir, mGameDir;

    public Partidas(Activity activity, File gameDir)
    {
        mActivity = activity;
        mDataDir = activity.getFilesDir();
        mGameDir = gameDir;
    }

    private static boolean isSave(File f)
    {
        String n = f.getName().toLowerCase();
        return f.isFile() && (n.endsWith(".rxdata") || n.endsWith(".rvdata") || n.endsWith(".rvdata2"));
    }

    private List<File> saves()
    {
        List<File> list = new ArrayList<>();
        for (File dir : new File[] { mDataDir, mGameDir }) {
            File[] files = dir.listFiles();
            if (files != null)
                for (File f : files)
                    if (isSave(f))
                        list.add(f);
        }
        return list;
    }

    public void export()
    {
        if (saves().isEmpty()) {
            toast(R.string.rmxp_saves_none);
            return;
        }
        mActivity.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), EXPORTAR);
    }

    public void pickImport()
    {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        mActivity.startActivityForResult(i, IMPORTAR);
    }

    /** Llamar desde onActivityResult. Devuelve true si era nuestro. */
    public boolean onResult(int request, int result, Intent data)
    {
        if (request != EXPORTAR && request != IMPORTAR)
            return false;
        if (result != Activity.RESULT_OK || data == null)
            return true;
        try {
            if (request == EXPORTAR)
                exportTo(data.getData());
            else
                importFrom(data);
        } catch (Exception e) {
            Toast.makeText(mActivity, mActivity.getString(R.string.rmxp_saves_error) + " " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        return true;
    }

    private void exportTo(Uri tree) throws Exception
    {
        ContentResolver cr = mActivity.getContentResolver();
        Uri dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        int n = 0;
        for (File f : saves()) {
            Uri doc = DocumentsContract.createDocument(cr, dir, "application/octet-stream", f.getName());
            if (doc == null)
                continue;
            try (InputStream in = new FileInputStream(f); OutputStream out = cr.openOutputStream(doc)) {
                copy(in, out);
            }
            n++;
        }
        Toast.makeText(mActivity, mActivity.getString(R.string.rmxp_saves_exported, n), Toast.LENGTH_LONG).show();
    }

    private void importFrom(Intent data) throws Exception
    {
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null)
            for (int i = 0; i < data.getClipData().getItemCount(); i++)
                uris.add(data.getClipData().getItemAt(i).getUri());
        else if (data.getData() != null)
            uris.add(data.getData());

        // Si el juego ya guarda en su propia carpeta (juegos viejos), las nuevas van ahí también
        boolean gameDirSaves = false;
        for (File f : saves())
            gameDirSaves |= f.getParentFile().equals(mGameDir);

        int n = 0;
        for (Uri uri : uris) {
            String name = displayName(uri);
            if (name == null || !name.toLowerCase().matches(".*\\.(rxdata|rvdata2?)$"))
                continue;
            File target = new File(gameDirSaves || new File(mGameDir, name).exists() ? mGameDir : mDataDir, name);
            if (target.exists()) // copia de seguridad de la partida que había antes
                target.renameTo(new File(target.getPath() + ".respaldo"));
            try (InputStream in = mActivity.getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(target)) {
                copy(in, out);
            }
            n++;
        }
        if (n == 0) {
            toast(R.string.rmxp_saves_none_picked);
            return;
        }
        new AlertDialog.Builder(mActivity)
            .setMessage(mActivity.getString(R.string.rmxp_saves_imported, n))
            .setPositiveButton(R.string.rmxp_reset, (d, w) -> {
                // F12 = reiniciar el juego para que lea la partida nueva
                SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_F12);
                SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_F12);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private String displayName(Uri uri)
    {
        try (Cursor c = mActivity.getContentResolver().query(uri, new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (c != null && c.moveToFirst())
                return c.getString(0);
        }
        return uri.getLastPathSegment();
    }

    private static void copy(InputStream in, OutputStream out) throws Exception
    {
        byte[] buf = new byte[64 * 1024];
        int r;
        while ((r = in.read(buf)) > 0)
            out.write(buf, 0, r);
    }

    private void toast(int text)
    {
        Toast.makeText(mActivity, text, Toast.LENGTH_LONG).show();
    }
}
