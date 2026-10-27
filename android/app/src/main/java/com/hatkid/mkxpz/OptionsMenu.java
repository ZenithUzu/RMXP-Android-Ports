package com.hatkid.mkxpz;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.util.Map;

import org.libsdl.app.SDLActivity;
import com.hatkid.mkxpz.controles.Control;
import com.hatkid.mkxpz.controles.PadMapper;
import com.hatkid.mkxpz.controles.Teclas;
import com.hatkid.mkxpz.controles.TouchControls;

/**
 * RMXP-Android-Ports: menú de opciones, medidor de FPS y conexión entre los controles
 * (en pantalla y mando físico) y el juego. Las opciones se guardan en SharedPreferences y
 * se mandan al motor con MainActivity.native* (ver graphics.cpp).
 */
public class OptionsMenu implements TouchControls.Actions
{
    private final Activity mActivity;
    private final SharedPreferences mPrefs;
    private final String mGamePath;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private View mRoot, mPanel;
    private TextView mSpeedValue, mScreenHint, mHzHint, mFpsLabel;
    private boolean mTurbo = false;
    private int mHz = 60;

    public final TouchControls controls;
    public final PadMapper pad;

    public OptionsMenu(Activity activity, String gamePath)
    {
        mActivity = activity;
        mGamePath = gamePath;
        mPrefs = activity.getSharedPreferences("rmxp_opciones", Activity.MODE_PRIVATE);
        pad = new PadMapper(mPrefs, this);
        controls = new TouchControls(activity, mPrefs, this, pad);
    }

    private int pref(String key, int def) { return mPrefs.getInt(key, def); }
    private void save(String key, int value) { mPrefs.edit().putInt(key, value).apply(); }

    public boolean isOpen() { return mRoot != null && mRoot.getVisibility() == View.VISIBLE; }

    /** Crea las vistas encima del juego. Llamar una vez, con el layout de SDL. */
    public void attachTo(ViewGroup layout)
    {
        float dp = mActivity.getResources().getDisplayMetrics().density;
        controls.attachTo(layout);

        // Medidor de FPS arriba a la izquierda
        mFpsLabel = new TextView(mActivity);
        mFpsLabel.setTextColor(mActivity.getColor(R.color.rmxp_ambar));
        mFpsLabel.setTextSize(13);
        mFpsLabel.setShadowLayer(3, 0, 0, Color.BLACK);
        mFpsLabel.setPadding(Math.round(12 * dp), Math.round(6 * dp), 0, 0);
        layout.addView(mFpsLabel, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mHandler.post(new Runnable() {
            int last = MainActivity.nativeGetDrawnFrames();
            public void run() {
                int now = MainActivity.nativeGetDrawnFrames();
                mFpsLabel.setText((now - last) + " FPS" + (mTurbo && !isOpen() ? "  x" + pref("velocidad", 5) : ""));
                last = now;
                mHandler.postDelayed(this, 1000);
            }
        });

        // Menú (oculto hasta que se abre)
        mRoot = LayoutInflater.from(mActivity).inflate(R.layout.rmxp_menu, layout, false);
        layout.addView(mRoot, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mPanel = mRoot.findViewById(R.id.rmxp_menu_panel);
        mRoot.setOnClickListener(v -> close()); // tocar el juego cierra el menú
        bindMenu();

        applyAll();
    }

    private void bindMenu()
    {
        TextView title = mRoot.findViewById(R.id.rmxp_title);
        title.setText(mActivity.getTitle());
        Typeface gameFont = findGameFont();
        if (gameFont != null)
            title.setTypeface(gameFont);

        mSpeedValue = mRoot.findViewById(R.id.rmxp_speed_value);
        mScreenHint = mRoot.findViewById(R.id.rmxp_screen_hint);
        mHzHint = mRoot.findViewById(R.id.rmxp_hz_hint);

        slider(R.id.rmxp_speed, "velocidad", 5, 2, v -> applySpeed());
        chips(R.id.rmxp_screen, "pantalla", 0, v -> applyVideo());
        chips(R.id.rmxp_hz, "hz", 0, v -> applyHz());
        chips(R.id.rmxp_fps, "fps", 0, v -> applyHz());
        toggle(R.id.rmxp_fps_show, "ver_fps", 0, v -> mFpsLabel.setVisibility(v == 1 ? View.VISIBLE : View.GONE));
        toggle(R.id.rmxp_pad_show, "controles", 1, v -> controls.setUserVisible(v == 1));

        mRoot.findViewById(R.id.rmxp_resume).setOnClickListener(v -> close());
        mRoot.findViewById(R.id.rmxp_reset).setOnClickListener(v -> {
            close();
            // F12 = reiniciar en mkxp-z
            SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_F12);
            SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_F12);
        });
        mRoot.findViewById(R.id.rmxp_quit).setOnClickListener(v -> mActivity.finish());
        mRoot.findViewById(R.id.rmxp_pad_edit).setOnClickListener(v -> {
            // El juego sigue en pausa mientras se editan los controles
            mRoot.setVisibility(View.GONE);
            controls.startEditing(this::applySpeed);
        });
        mRoot.findViewById(R.id.rmxp_input).setOnClickListener(v -> showInputMapping());
    }

    /** "Mando y teclado": a qué tecla manda cada botón del mando físico. */
    private void showInputMapping()
    {
        float dp = mActivity.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * dp);
        box.setPadding(padding, Math.round(8 * dp), padding, 0);
        TextView hint = new TextView(mActivity);
        hint.setText(R.string.rmxp_input_hint);
        box.addView(hint);
        for (Map.Entry<Integer, String> b : PadMapper.BOTONES.entrySet()) {
            Button row = new Button(mActivity);
            row.setAllCaps(false);
            row.setText(b.getValue() + "  →  " + Teclas.nombre(pad.keyFor(b.getKey())));
            row.setOnClickListener(v -> TouchControls.pickKey(mActivity, true, k -> {
                pad.setKey(b.getKey(), k);
                row.setText(b.getValue() + "  →  " + Teclas.nombre(k));
                controls.rebuild(); // el botón en pantalla con el mismo nombre también cambia
            }));
            box.addView(row);
        }
        ScrollView scroll = new ScrollView(mActivity);
        scroll.addView(box);
        new AlertDialog.Builder(mActivity).setTitle(R.string.rmxp_input).setView(scroll)
            .setPositiveButton(R.string.rmxp_edit_done, null)
            .setNeutralButton(R.string.rmxp_edit_reset, (d, w) -> { pad.reset(); controls.rebuild(); })
            .show();
    }

    private interface Changed { void run(int value); }

    /** Deslizador: guarda min + posición. */
    private void slider(int id, String key, int def, int min, Changed changed)
    {
        SeekBar bar = mRoot.findViewById(id);
        bar.setProgress(pref(key, def) - min);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                save(key, progress + min);
                changed.run(progress + min);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
    }

    /** Grupo de opciones: guarda el número de la opción elegida. */
    private void chips(int id, String key, int def, Changed changed)
    {
        RadioGroup group = mRoot.findViewById(id);
        int chosen = Math.min(pref(key, def), group.getChildCount() - 1);
        for (int i = 0; i < group.getChildCount(); i++) {
            View chip = group.getChildAt(i);
            chip.setId(View.generateViewId());
            if (i == chosen)
                group.check(chip.getId());
        }
        group.setOnCheckedChangeListener((g, checkedId) -> {
            int index = g.indexOfChild(g.findViewById(checkedId));
            save(key, index);
            changed.run(index);
        });
    }

    private void toggle(int id, String key, int def, Changed changed)
    {
        Switch sw = mRoot.findViewById(id);
        sw.setChecked(pref(key, def) == 1);
        sw.setOnCheckedChangeListener((b, on) -> {
            save(key, on ? 1 : 0);
            changed.run(on ? 1 : 0);
        });
    }

    /** Usa la primera fuente .ttf/.otf del juego para el título del menú. */
    private Typeface findGameFont()
    {
        File[] fonts = new File(mGamePath, "Fonts").listFiles();
        if (fonts == null)
            return null;
        for (File f : fonts) {
            String n = f.getName().toLowerCase();
            if (n.endsWith(".ttf") || n.endsWith(".otf")) {
                try {
                    return Typeface.createFromFile(f);
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- aplicar

    private void applyAll()
    {
        applySpeed();
        applyVideo();
        applyHz();
        controls.setUserVisible(pref("controles", 1) == 1);
        mFpsLabel.setVisibility(pref("ver_fps", 0) == 1 ? View.VISIBLE : View.GONE);
        //noinspection ResultOfMethodCallIgnored
        new File(mGamePath, "rmxp_pantalla.txt").delete(); // de la panorámica que se quitó
    }

    private void applySpeed()
    {
        int speed = pref("velocidad", 5);
        mSpeedValue.setText("x" + speed);
        controls.refreshTurbo();
        if (!isOpen() && !controls.isEditing())
            MainActivity.nativeSetSpeed(mTurbo ? speed : 1);
    }

    private void applyVideo()
    {
        int screen = Math.min(pref("pantalla", 0), 1);
        // Filtro fijo "nítido suave": píxeles parejos que llenan la pantalla
        MainActivity.nativeSetVideo(3, screen);
        mScreenHint.setText(screen == 0 ? R.string.rmxp_screen_hint_0 : R.string.rmxp_screen_hint_1);
    }

    /**
     * Elige la frecuencia de la pantalla. "Auto" busca una que sea múltiplo de 40 y 60 fps
     * (los fps de RPG Maker XP y VX), como 120 Hz, para que cada cuadro dure lo mismo.
     */
    private void applyHz()
    {
        int mode = pref("hz", 0);
        Display display = mActivity.getWindowManager().getDefaultDisplay();
        mHz = Math.round(display.getRefreshRate());
        if (Build.VERSION.SDK_INT >= 23) {
            Display.Mode current = display.getMode(), best = null;
            for (Display.Mode m : display.getSupportedModes()) {
                if (m.getPhysicalWidth() != current.getPhysicalWidth() || m.getPhysicalHeight() != current.getPhysicalHeight())
                    continue;
                int hz = Math.round(m.getRefreshRate());
                boolean fits = mode == 2 || (mode == 1 ? hz == 60 : hz == 60 || hz % 120 == 0);
                if (fits && (best == null || hz > Math.round(best.getRefreshRate())))
                    best = m;
            }
            if (best != null) {
                WindowManager.LayoutParams lp = mActivity.getWindow().getAttributes();
                lp.preferredDisplayModeId = best.getModeId();
                mActivity.getWindow().setAttributes(lp);
                mHz = Math.round(best.getRefreshRate());
            }
        }
        mHzHint.setText(mActivity.getString(R.string.rmxp_hz_hint, mHz));
        // "Igual a la pantalla": el juego corre a tantos fps como Hz tenga la pantalla
        MainActivity.nativeSetFps(pref("fps", 0) == 1 ? mHz : 0);
    }

    // ---------------------------------------------------------------- controles → juego

    @Override public void keyDown(int keycode) { SDLActivity.onNativeKeyDown(keycode); }
    @Override public void keyUp(int keycode) { SDLActivity.onNativeKeyUp(keycode); }
    @Override public boolean turboOn() { return mTurbo; }

    @Override
    public void action(int action)
    {
        switch (action) {
            case Control.TECLADO: toggleKeyboard(); break;
            case Control.TURBO: mTurbo = !mTurbo; applySpeed(); break;
            case Control.MENU: open(); break;
            case Control.OCULTAR: mHandler.post(controls::onPhysicalInput); break; // vuelven al tocar
        }
    }

    /** Muestra u oculta el teclado de Android (para escribir nombres; Enter confirma). */
    private void toggleKeyboard()
    {
        if (SDLActivity.isScreenKeyboardShown()) {
            InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(mActivity.getWindow().getDecorView().getWindowToken(), 0);
        } else {
            SDLActivity.showTextInput(0, 0, 1, 1);
        }
    }

    // ---------------------------------------------------------------- abrir / cerrar

    public void open()
    {
        if (isOpen() || controls.isEditing())
            return;
        MainActivity.nativeSetSpeed(0); // pausa el juego
        mRoot.setVisibility(View.VISIBLE);
        mPanel.setTranslationX(mPanel.getWidth() > 0 ? mPanel.getWidth() : 1000);
        mPanel.animate().translationX(0).setDuration(180).start();
        mRoot.findViewById(R.id.rmxp_resume).requestFocus();
    }

    public void close()
    {
        if (!isOpen())
            return;
        mRoot.setVisibility(View.GONE);
        applySpeed(); // quita la pausa
    }

    /** Al salir a otra app el juego se pausa (no dibuja sin pantalla); al volver sigue. */
    public void onBackground(boolean background)
    {
        if (background)
            MainActivity.nativeSetSpeed(0);
        else if (!isOpen() && !controls.isEditing())
            applySpeed();
    }

    public void toggle()
    {
        if (isOpen())
            close();
        else
            open();
    }
}
