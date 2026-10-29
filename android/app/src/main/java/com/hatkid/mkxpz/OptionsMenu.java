package com.hatkid.mkxpz;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
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
    private TextView mSpeedValue, mStretchValue, mHzHint;
    private Medidor mMedidor;
    public final Teclado teclado;
    private boolean mTurbo = false;
    private boolean mBackground;
    private int mHz = 60;

    public final TouchControls controls;
    public final PadMapper pad;
    public final Partidas partidas;
    public final Registro registro;
    public final Perfil perfil;

    public OptionsMenu(Activity activity, String gamePath)
    {
        mActivity = activity;
        mGamePath = gamePath;
        mPrefs = activity.getSharedPreferences("rmxp_opciones", Activity.MODE_PRIVATE);
        pad = new PadMapper(mPrefs, this);
        partidas = new Partidas(activity, new File(gamePath));
        registro = new Registro(activity, new File(gamePath));
        perfil = new Perfil(activity, mPrefs, this::recargarControles);
        controls = new TouchControls(activity, mPrefs, this, pad);
        teclado = new Teclado(activity, mPrefs, controls::keyboardVisible);
    }

    private int pref(String key, int def) { return mPrefs.getInt(key, def); }
    private void save(String key, int value) { mPrefs.edit().putInt(key, value).apply(); }

    public boolean isOpen() { return mRoot != null && mRoot.getVisibility() == View.VISIBLE; }

    /** Crea las vistas encima del juego. Llamar una vez, con el layout de SDL. */
    private void recargarControles() { controls.recargar(); }

    public void attachTo(ViewGroup layout)
    {
        float dp = mActivity.getResources().getDisplayMetrics().density;
        controls.attachTo(layout);

        mMedidor = new Medidor(mActivity, mPrefs, () -> mTurbo ? pref("velocidad", 5) : 1);
        mMedidor.attachTo(layout);
        teclado.attachTo(layout); // encima de los controles

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

        mSpeedValue = mRoot.findViewById(R.id.rmxp_speed_value);
        mStretchValue = mRoot.findViewById(R.id.rmxp_stretch_value);
        mHzHint = mRoot.findViewById(R.id.rmxp_hz_hint);

        chips(R.id.rmxp_sections, "seccion_menu", 0, this::selectSection);
        selectSection(pref("seccion_menu", 0));

        slider(R.id.rmxp_speed, "velocidad", 5, 2, v -> applySpeed());
        slider(R.id.rmxp_stretch, "estirar", 0, 0, v -> applyVideo());
        chips(R.id.rmxp_hz, "hz", 0, v -> applyHz());
        chips(R.id.rmxp_fps, "fps", 0, v -> applyHz());
        Switch pause = mRoot.findViewById(R.id.rmxp_keep_running);
        pause.setChecked(pref("seguir_en_menu", 1) == 0);
        pause.setOnCheckedChangeListener((button, checked) -> {
            save("seguir_en_menu", checked ? 0 : 1); applySpeed();
        });
        toggle(R.id.rmxp_pad_show, "controles", 1, v -> controls.setUserVisible(v == 1));
        toggle(R.id.rmxp_vibrar, "vibrar", 1, v -> com.hatkid.mkxpz.controles.ControlView.vibrar = v == 1);
        com.hatkid.mkxpz.controles.ControlView.vibrar = pref("vibrar", 1) == 1;
        mRoot.findViewById(R.id.rmxp_ctrl_export).setOnClickListener(v -> perfil.exportar());
        mRoot.findViewById(R.id.rmxp_ctrl_import).setOnClickListener(v -> perfil.importar());
        mRoot.findViewById(R.id.rmxp_saves_list).setOnClickListener(v -> partidas.lista());

        mRoot.findViewById(R.id.rmxp_resume).setOnClickListener(v -> close());
        mRoot.findViewById(R.id.rmxp_reset).setOnClickListener(v -> {
            close();
            Partidas.reiniciar(mActivity);
        });
        mRoot.findViewById(R.id.rmxp_quit).setOnClickListener(v -> {
            // Cerrar la app de golpe: con el juego en pausa, el cierre normal de SDL se queda
            // esperando al hilo del juego y el proceso sigue vivo (al volver: pantalla negra).
            mActivity.finishAndRemoveTask();
            android.os.Process.killProcess(android.os.Process.myPid());
        });
        mRoot.findViewById(R.id.rmxp_pad_edit).setOnClickListener(v -> {
            teclado.hide();
            mRoot.setVisibility(View.GONE);
            controls.startEditing(this::applySpeed);
            applySpeed();
        });
        mRoot.findViewById(R.id.rmxp_input).setOnClickListener(v -> showInputMapping());
        mRoot.findViewById(R.id.rmxp_meter).setOnClickListener(v -> mMedidor.showSettings());
        mRoot.findViewById(R.id.rmxp_saves_export).setOnClickListener(v -> partidas.export());
        mRoot.findViewById(R.id.rmxp_saves_import).setOnClickListener(v -> partidas.pickImport());
        mRoot.findViewById(R.id.rmxp_log).setOnClickListener(v -> registro.save());
        mRoot.findViewById(R.id.rmxp_folder).setOnClickListener(v -> {
            if (partidas.carpetaPermitida())
                android.widget.Toast.makeText(mActivity, partidas.carpeta().getPath(), android.widget.Toast.LENGTH_LONG).show();
            else
                partidas.pedirCarpeta(false);
        });
    }

    private void selectSection(int selected)
    {
        int[] ids = { R.id.rmxp_section_0, R.id.rmxp_section_1, R.id.rmxp_section_2 };
        selected = Math.max(0, Math.min(selected, ids.length - 1));
        for (int i = 0; i < ids.length; i++)
            mRoot.findViewById(ids[i]).setVisibility(i == selected ? View.VISIBLE : View.GONE);
        ((ScrollView) mPanel).smoothScrollTo(0, 0);
    }

    /** "Mando y teclado": a qué tecla manda cada botón del mando físico. */
    private void showInputMapping()
    {
        float dp = mActivity.getResources().getDisplayMetrics().density;
        PanelLateral panel = new PanelLateral(mActivity, R.string.rmxp_input);
        LinearLayout box = panel.content;
        TextView hint = new TextView(mActivity);
        hint.setText(R.string.rmxp_input_hint);
        box.addView(hint);
        for (Map.Entry<Integer, String> b : PadMapper.BOTONES.entrySet()) {
            Button row = panel.button("", () -> {});
            row.setAllCaps(false);
            row.setText(b.getValue() + "  →  " + Teclas.nombre(pad.keyFor(b.getKey())));
            row.setOnClickListener(v -> TouchControls.pickKey(mActivity, true, k -> {
                pad.setKey(b.getKey(), k);
                row.setText(b.getValue() + "  →  " + Teclas.nombre(k));
                controls.rebuild();
                showInputMapping();
            }));
            box.addView(row);
        }
        box.addView(panel.button(mActivity.getString(R.string.rmxp_edit_reset), () -> {
            pad.reset(); controls.rebuild(); panel.dismiss(); showInputMapping();
        }));
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

    // ---------------------------------------------------------------- aplicar

    private void applyAll()
    {
        applySpeed();
        applyVideo();
        applyHz();
        controls.setUserVisible(pref("controles", 1) == 1);
        //noinspection ResultOfMethodCallIgnored
        new File(mGamePath, "rmxp_pantalla.txt").delete(); // de la panorámica que se quitó
    }

    private void applySpeed()
    {
        if (mRoot == null) return;
        int speed = pref("velocidad", 5);
        mSpeedValue.setText("x" + speed);
        controls.refreshTurbo();
        boolean paused = mBackground || ((isOpen() || controls.isEditing()) && pref("seguir_en_menu", 1) == 0);
        MainActivity.nativeSetSpeed(paused ? 0 : mTurbo ? speed : 1);
        ((TextView) mRoot.findViewById(R.id.rmxp_status)).setText(
            paused ? R.string.rmxp_paused : R.string.rmxp_running);
    }

    private void applyVideo()
    {
        // La opción vieja "Llenar" (pantalla = 1) equivale a estirar al 100 %
        int stretch = mPrefs.contains("estirar") ? pref("estirar", 0) : pref("pantalla", 0) * 100;
        mStretchValue.setText(stretch + " %");
        // Filtro fijo "nítido" (vecino más cercano, como Kirin). Va después de nativeSetStretch,
        // que por dentro pide el filtro 3 (suave y borroso); la última orden es la que vale.
        MainActivity.nativeSetStretch(stretch);
        MainActivity.nativeSetVideo(0, 0);
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
        int target = fpsTarget(pref("fps", 0), mHz);
        MainActivity.nativeSetFps(target);
        android.view.Surface surface = SDLActivity.getNativeSurface();
        if (surface != null && surface.isValid()) {
            if (Build.VERSION.SDK_INT >= 31)
                surface.setFrameRate(mHz, android.view.Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                    android.view.Surface.CHANGE_FRAME_RATE_ALWAYS);
            else if (Build.VERSION.SDK_INT >= 30)
                surface.setFrameRate(mHz, android.view.Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
        }
        mHzHint.setText(mActivity.getString(R.string.rmxp_hz_hint, mHz,
            Math.round(display.getRefreshRate())));
        mHandler.postDelayed(() -> {
            if (isOpen()) mHzHint.setText(mActivity.getString(R.string.rmxp_hz_hint, mHz,
                Math.round(display.getRefreshRate())));
        }, 500);
        android.util.Log.i("RMXP-Pantalla", "requestedHz=" + mHz + " actualHz="
            + display.getRefreshRate() + " targetFps=" + target);

    }

    static int fpsTarget(int mode, int hz)
    {
        return mode == 1 ? hz : mode == 2 ? 60 : mode == 3 ? 120 : 0;
    }

    public void refreshDisplay() { applyHz(); }

    // ---------------------------------------------------------------- controles → juego

    @Override public void keyDown(int keycode) { SDLActivity.onNativeKeyDown(keycode); }
    @Override public void keyUp(int keycode) { SDLActivity.onNativeKeyUp(keycode); }
    @Override public boolean turboOn() { return mTurbo; }

    @Override
    public void action(int action)
    {
        switch (action) {
            case Control.TECLADO: teclado.toggle(); break;
            case Control.TURBO: mTurbo = !mTurbo; applySpeed(); break;
            case Control.MENU: open(); break;
            case Control.OCULTAR: mHandler.post(controls::onPhysicalInput); break; // vuelven al tocar
        }
    }

    // ---------------------------------------------------------------- abrir / cerrar

    public void open()
    {
        if (isOpen() || controls.isEditing())
            return;
        teclado.hide();
        mRoot.setVisibility(View.VISIBLE);
        ((ScrollView) mPanel).scrollTo(0, 0);
        refreshDisplay();
        controls.releasePressed();
        applySpeed();
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
        mBackground = background;
        if (background)
            controls.releasePressed();
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
