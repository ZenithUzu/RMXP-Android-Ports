package com.hatkid.mkxpz;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.view.KeyEvent;
import android.view.View;
import android.view.MotionEvent;
import android.view.InputDevice;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.os.storage.StorageManager;
import android.os.storage.OnObbStateChangeListener;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import java.util.Locale;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import android.content.res.AssetManager;
import android.widget.Toast;

import org.libsdl.app.SDLActivity;

public class MainActivity extends SDLActivity
{
    // This activity inherits from SDLActivity activity.
    // Put your Java-side stuff here.

    private static final String TAG = "mkxp-z[Activity]";
    private static final String GAME_PATH_DEFAULT = Environment.getExternalStorageDirectory() + "/mkxp-z";
    private static String GAME_PATH = GAME_PATH_DEFAULT;
    private static String OBB_MAIN_FILENAME;
    private static boolean DEBUG = false;

    // Bundled game (APK assets) extraction
    private static final String ASSETS_GAME_DIR = "game";
    private static final String EXTRACTED_DIR = "game";
    private static final String EXTRACT_MARKER = ".extract_ok";
    // "Ahorrar espacio": files listed here stay inside the APK (the engine reads them from it)
    private static final String LIGERO = "rmxp_ligero.txt";
    private java.util.Set<String> mVirtual;

    protected boolean mStarted = false;

    private StorageManager mStorageManager;


    // RMXP-Android-Ports: options menu, fast forward and video settings
    private OptionsMenu mMenu;

    /** Implemented in graphics.cpp. speed: 0 = paused, 1 = normal, 2..10 = fast forward. */
    public static native void nativeSetSpeed(int speed);
    /** filter: 0 sharp, 1 smooth, 2 pixel perfect, 3 sharp smooth. screen: 0 original, 1 fill, 2 wide. */
    public static native void nativeSetVideo(int filter, int screen);
    /** 0 = the game's own frame rate, otherwise frames per second (screen Hz). */
    public static native void nativeSetFps(int fps);
    /** 0 = the game's own shape, 100 = fill the screen. */
    public static native void nativeSetStretch(int percent);
    /** Frames shown so far, for the FPS counter. */
    public static native int nativeGetDrawnFrames();

    /** True when the APK ships a game under assets/game. */
    private boolean hasBundledAssets()
    {
        try {
            String[] entries = getAssets().list(ASSETS_GAME_DIR);
            return entries != null && entries.length > 0;
        } catch (IOException e) {
            return false;
        }
    }

    /** Files that stay inside the APK ("Ahorrar espacio"), or null when everything is extracted. */
    private java.util.Set<String> readVirtual()
    {
        java.util.Set<String> set = new java.util.HashSet<>();
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(
                getAssets().open(ASSETS_GAME_DIR + "/" + LIGERO), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null)
                if (!line.isEmpty())
                    set.add(line);
        } catch (IOException e) {
            return null;
        }
        return set;
    }

    private boolean isVirtual(String rel)
    {
        return mVirtual != null && mVirtual.contains(rel);
    }

    /** True when the extracted copy matches the current APK version. */
    private boolean markerIsCurrent()
    {
        try {
            File marker = new File(getFilesDir(), EXTRACTED_DIR + "/" + EXTRACT_MARKER);
            if (!marker.exists()) {
                return false;
            }
            String stored = new String(java.nio.file.Files.readAllBytes(marker.toPath())).trim();
            return stored.equals(String.valueOf(BuildConfig.VERSION_CODE));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extracts the bundled game without risking the player's data (RMXP-Android-Ports).
     * The new version goes to "game.new" first; only when it is complete does it replace
     * "game". Anything the player created inside the old folder (saves, settings) that the
     * new version does not ship is moved over, never overwritten or deleted. If the app is
     * closed halfway, the old game stays untouched and the next launch tries again.
     */
    private boolean extractBundledGame()
    {
        AssetManager am = getAssets();
        File game = new File(getFilesDir(), EXTRACTED_DIR);
        File fresh = new File(getFilesDir(), EXTRACTED_DIR + ".new");
        File old = new File(getFilesDir(), EXTRACTED_DIR + ".old");
        Log.i(TAG, "Extracting bundled game from APK assets to " + fresh);
        deleteRecursive(fresh); // leftover from an interrupted update
        try {
            extractAssetDir(am, ASSETS_GAME_DIR, fresh);
            if (game.exists())
                moveMissing(game, fresh, "");
            FileOutputStream fos = new FileOutputStream(new File(fresh, EXTRACT_MARKER));
            try {
                fos.write(String.valueOf(BuildConfig.VERSION_CODE).getBytes());
            } finally {
                fos.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract bundled game", e);
            return false;
        }
        deleteRecursive(old);
        if (game.exists() && !game.renameTo(old))
            return false;
        if (!fresh.renameTo(game)) {
            //noinspection ResultOfMethodCallIgnored
            old.renameTo(game);
            return false;
        }
        deleteRecursive(old);
        return true;
    }

    /** Moves into dst every file of src that dst does not have (player saves). */
    private void moveMissing(File src, File dst, String rel)
    {
        File[] children = src.listFiles();
        if (children == null)
            return;
        for (File c : children) {
            File target = new File(dst, c.getName());
            String childRel = rel + c.getName();
            // Game files now read from the APK, and the old list, are not player data
            if (c.getName().equals(EXTRACT_MARKER) || childRel.equals(LIGERO) || isVirtual(childRel))
                continue;
            if (!target.exists()) {
                //noinspection ResultOfMethodCallIgnored
                c.renameTo(target);
            } else if (c.isDirectory() && target.isDirectory()) {
                moveMissing(c, target, childRel + "/");
            }
        }
    }

    private void extractAssetDir(AssetManager am, String assetPath, File outDir) throws IOException
    {
        String[] entries = am.list(assetPath);
        if (entries == null || entries.length == 0) {
            // It is a file
            copyAssetFile(am, assetPath, outDir);
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        outDir.mkdirs();
        for (String entry : entries) {
            String childAsset = assetPath + "/" + entry;
            File childOut = new File(outDir, entry);
            String[] subEntries = am.list(childAsset);
            if (subEntries != null && subEntries.length > 0) {
                extractAssetDir(am, childAsset, childOut);
            } else if (isVirtual(childAsset.substring(ASSETS_GAME_DIR.length() + 1))) {
                continue; // stays in the APK; the folder exists so the game can still list it
            } else {
                copyAssetFile(am, childAsset, childOut);
            }
        }
    }

    private void copyAssetFile(AssetManager am, String assetPath, File outFile) throws IOException
    {
        File parent = outFile.getParentFile();
        if (parent != null) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        InputStream in = am.open(assetPath);
        FileOutputStream out = new FileOutputStream(outFile);
        byte[] buffer = new byte[1024 * 512];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        out.close();
        in.close();
    }

    private void deleteRecursive(File f)
    {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) {
                    deleteRecursive(c);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private void runSDLThread()
    {
        if (!mStarted) {
            Log.i(TAG, "Game path: " + GAME_PATH);
        }

        mStarted = true;

        // Run (resume) native SDL thread
        if (mHasMultiWindow) {
            resumeNativeThread();
        }
    }

    OnObbStateChangeListener obbListener = new OnObbStateChangeListener()
    {
        @Override
        public void onObbStateChange(String path, int state)
        {
            super.onObbStateChange(path, state);

            Log.v(TAG, "OBB state of " + path + " changed to " + state);

            switch (state)
            {
                case OnObbStateChangeListener.MOUNTED:
                    String obbPath = mStorageManager.getMountedObbPath(path);
                    Log.v(TAG, "OBB " + path + " is mounted to " + obbPath);
                    GAME_PATH = obbPath;
                    break;

                case OnObbStateChangeListener.UNMOUNTED:
                    Log.v(TAG, "OBB " + path + " is unmounted");
                    GAME_PATH = GAME_PATH_DEFAULT;
                    break;

                default:
                    Log.e(TAG, "Failed to mount OBB " + path + ": Got state " + state);
                    break;
            }

            runSDLThread();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        // Recolector de basura de Ruby: montón inicial grande y límites más altos, para que
        // las pausas de limpieza (tirones al guardar, en batalla o al cambiar de zona) pasen
        // menos seguido. Ruby los lee al arrancar; gasta algo más de RAM.
        String[][] gc = {
            { "RUBY_GC_HEAP_INIT_SLOTS", "800000" },
            { "RUBY_GC_HEAP_OLDOBJECT_LIMIT_FACTOR", "3.0" },
            { "RUBY_GC_MALLOC_LIMIT", "67108864" },
            { "RUBY_GC_MALLOC_LIMIT_MAX", "134217728" },
            { "RUBY_GC_OLDMALLOC_LIMIT", "67108864" },
            { "RUBY_GC_OLDMALLOC_LIMIT_MAX", "268435456" },
        };
        for (String[] v : gc) {
            try { android.system.Os.setenv(v[0], v[1], false); } catch (Exception ignored) { }
        }

        mVirtual = readVirtual();
        if (mVirtual != null) {
            try { android.system.Os.setenv("RMXP_APK", getApplicationInfo().sourceDir, true); } catch (Exception ignored) { }
        }

        super.onCreate(savedInstanceState);

        hideSystemBars();
        if (Build.VERSION.SDK_INT >= 28) {
            android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }

        mStorageManager = (StorageManager) getSystemService(STORAGE_SERVICE);

        // Get main OBB filepath
        final String obbPrefix = "main"; // "main", "patch"
        final int obbVersion = 1;
        OBB_MAIN_FILENAME = getObbDir() + "/" + obbPrefix + "." + obbVersion + "." + getPackageName() + ".obb";

        // Get Debug flag
        try {
            ActivityInfo actInfo = getPackageManager().getActivityInfo(this.getComponentName(), PackageManager.GET_META_DATA);
            DEBUG = actInfo.metaData.getBoolean("mkxp_debug");
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Failed to set debug flag: " + e);
            e.printStackTrace();
        }

        // RMXP-Android-Ports: on-screen controls, physical gamepad mapping and options menu
        if (mLayout != null) {
            mMenu = new OptionsMenu(this, new File(getFilesDir(), EXTRACTED_DIR).getAbsolutePath());
            mMenu.attachTo(mLayout);
        }
    }

    @Override
    protected void onStart()
    {
        super.onStart();

        if (!mStarted) {
            // Check for main OBB file
            if (new File(OBB_MAIN_FILENAME).exists()) {
                Log.v(TAG, "Main OBB file found, starting with main OBB mount");

                // Try to mount main OBB file
                mStorageManager.mountObb(OBB_MAIN_FILENAME, null, obbListener);
            } else {
                Log.v(TAG, "Main OBB file not found");

                // Prefer the game bundled in the APK; fall back to external storage
                File dest = new File(getFilesDir(), EXTRACTED_DIR);
                if (new File(dest, EXTRACT_MARKER).exists() && markerIsCurrent()) {
                    GAME_PATH = dest.getAbsolutePath();
                    Log.i(TAG, "Using bundled game at " + GAME_PATH);
                    runSDLThread();
                } else if (hasBundledAssets()) {
                    GAME_PATH = dest.getAbsolutePath();
                    Log.i(TAG, "Extracting bundled game to " + GAME_PATH);
                    Toast.makeText(this, R.string.rmxp_preparing, Toast.LENGTH_LONG).show();
                    final Activity activity = this;
                    new Thread(() -> {
                        if (!extractBundledGame()) {
                            Log.e(TAG, "Bundled game extraction failed");
                            activity.runOnUiThread(() -> {
                                Toast.makeText(activity, R.string.rmxp_update_failed, Toast.LENGTH_LONG).show();
                                activity.finish();
                            });
                            return;
                        }
                        activity.runOnUiThread(() -> runSDLThread());
                    }).start();
                } else {
                    Log.v(TAG, "No bundled game assets, starting from " + GAME_PATH);
                    runSDLThread();
                }
            }
        } else {
            // onStart: Resume SDL thread
            runSDLThread();
        }
    }

    // RMXP-Android-Ports: full screen without status/navigation bars, also over the camera cutout
    private void hideSystemBars()
    {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(android.view.WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (mMenu != null && mMenu.partidas.onResult(requestCode, resultCode, data))
            return;
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
            // Resume here, not in onResume: by now the new drawing surface exists
            if (mMenu != null)
                mMenu.onBackground(false);
        }
    }

    @Override
    protected void onPause()
    {
        if (mMenu != null)
            mMenu.onBackground(true);
        super.onPause();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        hideSystemBars();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        // HACK: Exiting the JVM (process) since Ruby does not likes when we
        // trying to re-initialize Ruby VM in mkxp-z (JNI native library)
        // that leads to segmentation fault, even we have cleanup the Ruby VM.
        System.exit(0);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent evt)
    {
        if (mMenu == null)
            return super.dispatchKeyEvent(evt);

        // The phone's Back opens/closes the menu (a gamepad's Back is mapped like B)
        if (evt.getKeyCode() == KeyEvent.KEYCODE_BACK && !isFromGamepad(evt)) {
            if (evt.getAction() == KeyEvent.ACTION_UP && mMenu.teclado.shown())
                mMenu.teclado.hide();
            else if (evt.getAction() == KeyEvent.ACTION_UP && !mMenu.controls.isEditing())
                mMenu.toggle();
            return true;
        }

        // While the menu is open, the keys move around the menu instead of the game
        if (mMenu.isOpen())
            return getWindow().superDispatchKeyEvent(evt);

        int code = evt.getKeyCode();
        boolean volume = code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_MUTE;
        InputDevice device = evt.getDevice();
        if (!volume && device != null && !device.isVirtual())
            mMenu.controls.onPhysicalInput(); // a real gamepad/keyboard is in use: hide on-screen controls

        if (mMenu.pad.onKey(evt))
            return true;

        return super.dispatchKeyEvent(evt);
    }

    private static boolean isFromGamepad(KeyEvent evt)
    {
        InputDevice device = evt.getDevice();
        return device != null && (device.getSources() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent evt)
    {
        // Touching the screen brings the on-screen controls back
        if (mMenu != null && evt.getActionMasked() == MotionEvent.ACTION_DOWN)
            mMenu.controls.onScreenTouched();

        return super.dispatchTouchEvent(evt);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent evt)
    {
        if (mMenu != null && mMenu.pad.onMotion(evt))
            return true;

        return super.onGenericMotionEvent(evt);
    }

    /**
     * This method is for arguments for launching native mkxp-z.
     * 
     * @return arguments for the mkxp-z
     */
    @Override
    protected String[] getArguments()
    {
        String[] args;

        if (DEBUG) {
            // Arguments in Debug mode
            args = new String[] { "debug" };
        } else {
            // Arguments in normal mode
            args = new String[] {};
        }

        return args;
    }

    /**
     * This static method is used in native mkxp-z. (see systemImpl.cpp)
     * This method returns a string of current device locale tag. (e.g. "en_US")
     * 
     * @return string of locale tag
     */
    @SuppressWarnings("unused")
    private static String getSystemLanguage()
    {
        return Locale.getDefault().toString();
    }

    /**
     * This static method is used in native mkxp-z. (see android-binding.cpp)
     * This method returns a boolean indicating that the device has a vibrator or not.
     * 
     * @return boolean
     */
    @SuppressWarnings("unused")
    private static boolean hasVibrator()
    {
        Vibrator vib = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        return vib.hasVibrator();
    }

    /**
     * This static method is used in native mkxp-z. (see android-binding.cpp)
     * This method makes device vibrating with given milliseconds duration.
     * 
     * @param duration milliseconds duration of vibration
     */
    @SuppressWarnings("unused")
    private static void vibrate(int duration)
    {
        Vibrator vib = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.EFFECT_HEAVY_CLICK));
        } else {
            vib.vibrate(duration);
        }
    }

    /**
     * This static method is used in native mkxp-z. (see android-binding.cpp)
     * This method turns off the current device vibration.
     */
    @SuppressWarnings("unused")
    private static void vibrateStop()
    {
        Vibrator vib = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        vib.cancel();
    }

    /**
     * This static method is used in native mkxp-z. (see android-binding.cpp)
     * This method returns a boolean indicating the app is in multi window mode or not.
     * (Multi-window mode supports from Android 7.0 Nougat (API 24) and higher.)
     * 
     * @param activity current MainActivity instance
     * @return boolean
     */
    @SuppressWarnings("unused")
    private static boolean inMultiWindow(Activity activity)
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInMultiWindowMode();
    }
}