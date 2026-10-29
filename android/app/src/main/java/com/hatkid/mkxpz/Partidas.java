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
    public static final int EXPORTAR = 4201, IMPORTAR = 4202, EXPORTAR_UNA = 4207;
    private File mUna; // la partida que se exporta sola

    private final Activity mActivity;
    private final File mDataDir, mGameDir;

    public Partidas(Activity activity, File gameDir)
    {
        mActivity = activity;
        mDataDir = activity.getFilesDir();
        mGameDir = gameDir;
    }

    static boolean isSave(File f)
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
        if (request == EXPORTAR_UNA) {
            if (result == Activity.RESULT_OK && data != null && mUna != null)
                try (InputStream in = new FileInputStream(mUna); OutputStream out = mActivity.getContentResolver().openOutputStream(data.getData())) {
                    copy(in, out);
                    toast(R.string.rmxp_ctrl_ok);
                } catch (Exception e) {
                    toast(R.string.rmxp_saves_error);
                }
            return true;
        }
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
                Partidas.reiniciar(mActivity);
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

    // ---------------------------------------------------------------- gestor de partidas

    /** Lista de partidas con fecha y tamaño; al tocar una: exportar, respaldar o borrar. */
    public void lista()
    {
        List<File> l = saves();
        if (l.isEmpty()) {
            toast(R.string.rmxp_saves_none);
            return;
        }
        java.text.DateFormat f = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT);
        String[] items = new String[l.size()];
        for (int i = 0; i < items.length; i++)
            items[i] = l.get(i).getName() + "\n" + f.format(new java.util.Date(l.get(i).lastModified()))
                + " · " + Math.max(1, l.get(i).length() / 1024) + " KB";
        new AlertDialog.Builder(mActivity).setTitle(R.string.rmxp_saves_list)
            .setItems(items, (d, i) -> unaPartida(l.get(i))).show();
    }

    private void unaPartida(File s)
    {
        String[] op = { mActivity.getString(R.string.rmxp_save_export), mActivity.getString(R.string.rmxp_save_backup),
            mActivity.getString(R.string.rmxp_save_delete) };
        new AlertDialog.Builder(mActivity).setTitle(s.getName()).setItems(op, (d, i) -> {
            if (i == 0) {
                mUna = s;
                mActivity.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, s.getName()), EXPORTAR_UNA);
            } else if (i == 1) {
                File r = new File(s.getPath() + ".respaldo-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(new java.util.Date()));
                try (InputStream in = new FileInputStream(s); OutputStream out = new FileOutputStream(r)) {
                    copy(in, out);
                    Toast.makeText(mActivity, mActivity.getString(R.string.rmxp_save_backed, r.getName()), Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    toast(R.string.rmxp_saves_error);
                }
            } else {
                new AlertDialog.Builder(mActivity).setMessage(mActivity.getString(R.string.rmxp_save_delete_q, s.getName()))
                    .setPositiveButton(R.string.rmxp_save_delete, (d2, w) -> {
                        //noinspection ResultOfMethodCallIgnored
                        s.delete();
                        //noinspection ResultOfMethodCallIgnored
                        new File(carpeta(), s.getName()).delete(); // si no, la sincronización la traería de vuelta
                    })
                    .setNegativeButton(android.R.string.cancel, null).show();
            }
        }).show();
    }

    // ---------------------------------------------------------------- carpeta "RPG Maker"

    /**
     * Copia visible de las partidas en el almacenamiento del teléfono: "RPG Maker/<juego>/",
     * junto a Download, Android, etc. Sobrevive a desinstalar la app y se puede copiar a la PC.
     * El juego sigue guardando donde siempre; esto solo sincroniza (gana la más nueva).
     */
    public File carpeta()
    {
        // El nombre que muestra Android (en los APK hechos en el teléfono no es R.string.app_name)
        String juego = mActivity.getApplicationInfo().loadLabel(mActivity.getPackageManager()).toString().replaceAll("[\\\\/:*?\"<>|]", "").trim();
        return new File(android.os.Environment.getExternalStorageDirectory(), "RPG Maker/" + juego);
    }

    public boolean carpetaPermitida()
    {
        if (android.os.Build.VERSION.SDK_INT >= 30)
            return android.os.Environment.isExternalStorageManager();
        return mActivity.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /** Pide el permiso (una sola vez sola; desde el menú, cuando se quiera). */
    public void pedirCarpeta(boolean soloPrimeraVez)
    {
        android.content.SharedPreferences prefs = mActivity.getSharedPreferences("rmxp", Activity.MODE_PRIVATE);
        if (carpetaPermitida() || (soloPrimeraVez && prefs.getBoolean("carpeta_preguntada", false)))
            return;
        prefs.edit().putBoolean("carpeta_preguntada", true).apply();
        new AlertDialog.Builder(mActivity)
            .setTitle(R.string.rmxp_folder_title)
            .setMessage(mActivity.getString(R.string.rmxp_folder_msg, "RPG Maker/" + carpeta().getName()))
            .setPositiveButton(R.string.rmxp_folder_allow, (d, w) -> {
                if (android.os.Build.VERSION.SDK_INT >= 30)
                    mActivity.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + mActivity.getPackageName())));
                else
                    mActivity.requestPermissions(new String[] { android.Manifest.permission.WRITE_EXTERNAL_STORAGE }, 4204);
            })
            .setNegativeButton(R.string.rmxp_folder_later, null)
            .show();
    }

    /** Sincroniza en los dos sentidos: la copia más nueva de cada partida gana. */
    /** Devuelve cuántas partidas entraron desde la carpeta (para ofrecer reiniciar). */
    public synchronized int sincronizar()
    {
        if (!carpetaPermitida())
            return 0;
        File dir = carpeta();
        if (!dir.isDirectory() && !dir.mkdirs())
            return 0;
        int entraron = 0;
        boolean gameDirSaves = false;
        for (File f : saves())
            gameDirSaves |= f.getParentFile().equals(mGameDir);
        for (File f : saves())
            copiarSiMasNueva(f, new File(dir, f.getName()));
        File[] fuera = dir.listFiles();
        if (fuera != null)
            for (File f : fuera) {
                if (!isSave(f))
                    continue;
                File enJuego = new File(mGameDir, f.getName());
                File dentro = enJuego.exists() || gameDirSaves ? enJuego : new File(mDataDir, f.getName());
                if (copiarSiMasNueva(f, dentro))
                    entraron++;
            }
        return entraron;
    }

    private static boolean copiarSiMasNueva(File src, File dst)
    {
        if (dst.exists() && dst.lastModified() >= src.lastModified() - 2000)
            return false;
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            copy(in, out);
        } catch (Exception e) {
            return false;
        }
        //noinspection ResultOfMethodCallIgnored
        dst.setLastModified(src.lastModified()); // misma hora: así no se copian de ida y vuelta
        return true;
    }

    /**
     * Reinicia el juego abriendo la app de nuevo (proceso limpio). El F12 de mkxp-z reinicia por
     * dentro y algunos juegos se rompen ("disposed viewport" en CustomTilemap).
     */
    static void reiniciar(Activity a)
    {
        // Una pantallita en otro proceso cierra este y vuelve a abrir el juego (como ProcessPhoenix)
        a.startActivity(new Intent(a, Reinicio.class).putExtra("pid", android.os.Process.myPid())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /** Ofrece reiniciar cuando entraron partidas con el juego ya abierto. */
    public void ofrecerReinicio(int n)
    {
        if (n <= 0)
            return;
        new AlertDialog.Builder(mActivity)
            .setMessage(mActivity.getString(R.string.rmxp_saves_imported, n))
            .setPositiveButton(R.string.rmxp_reset, (d, w) -> {
                Partidas.reiniciar(mActivity);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private final List<android.os.FileObserver> mObservers = new ArrayList<>();

    /** Cada vez que el juego guarda, la partida se copia a la carpeta al momento. */
    @SuppressWarnings("deprecation")
    public void vigilar()
    {
        if (!mObservers.isEmpty())
            return;
        for (File dir : new File[] { mDataDir, mGameDir }) {
            android.os.FileObserver o = new android.os.FileObserver(dir.getPath(),
                    android.os.FileObserver.CLOSE_WRITE | android.os.FileObserver.MOVED_TO) {
                @Override
                public void onEvent(int event, String path)
                {
                    if (path != null && path.toLowerCase().matches(".*\\.(rxdata|rvdata2?)$"))
                        sincronizar();
                }
            };
            o.startWatching();
            mObservers.add(o);
        }
    }

    private void toast(int text)
    {
        Toast.makeText(mActivity, text, Toast.LENGTH_LONG).show();
    }
}
