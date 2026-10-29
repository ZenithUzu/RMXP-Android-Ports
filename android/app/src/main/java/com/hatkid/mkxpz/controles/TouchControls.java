package com.hatkid.mkxpz.controles;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;
import java.util.Map;

import com.hatkid.mkxpz.R;
import com.hatkid.mkxpz.Apariencia;
import com.hatkid.mkxpz.PanelLateral;

/**
 * Controles en pantalla estilo Kirin: cada botón se puede mover, añadir, borrar, mapear,
 * y cambiar de color, forma, tamaño y opacidad. Se ocultan solos al conectar un mando o
 * un teclado, y vuelven al tocar la pantalla o al desconectarlo.
 */
public class TouchControls implements ControlView.Listener, InputManager.InputDeviceListener
{
    /** Lo que hacen las acciones (teclado, turbo, menú) y las teclas. */
    public interface Actions
    {
        void keyDown(int keycode);
        void keyUp(int keycode);
        void action(int action);
        boolean turboOn();
        void controllerConnected(InputDevice device);
    }

    private final Activity mActivity;
    private final SharedPreferences mPrefs;
    private final Actions mActions;
    private final int mAccent;
    private final PadMapper mPad;
    private FrameLayout mLayer;
    private List<Control> mControls;
    private boolean mEditing, mShown = true, mUserVisible = true, mKeyboard;
    private View mToolbar;

    public TouchControls(Activity activity, SharedPreferences prefs, Actions actions, PadMapper pad)
    {
        mActivity = activity;
        mPad = pad;
        mPrefs = prefs;
        mActions = actions;
        mAccent = activity.getColor(R.color.rmxp_ambar);
    }

    private float dp(float v) { return v * mActivity.getResources().getDisplayMetrics().density; }

    public void attachTo(ViewGroup parent)
    {
        mLayer = new FrameLayout(mActivity);
        mLayer.setTag("rmxp_touch_controls");
        parent.addView(mLayer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mLayer.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot)
                placeAll();
        });
        // "controles_v2": la distribución pareja nueva reemplaza una vez a la anterior
        String json = mPrefs.getString("controles_v2", null);
        mControls = json == null ? null : Control.fromJson(json);
        if (mControls == null)
            mControls = predeterminados();
        rebuild();
        InputManager im = (InputManager) mActivity.getSystemService(Activity.INPUT_SERVICE);
        im.registerInputDeviceListener(this, new Handler(Looper.getMainLooper()));
        updateForDevices();
    }

    private List<Control> predeterminados()
    {
        android.util.DisplayMetrics m = mActivity.getResources().getDisplayMetrics();
        float w = Math.max(m.widthPixels, m.heightPixels) / m.density, h = Math.min(m.widthPixels, m.heightPixels) / m.density;
        return Control.predeterminados(w, h);
    }

    /** Talla y opacidad de un control: las de todos ("Igual para todos") o las suyas. */
    private int talla(Control c) { return mPrefs.getBoolean("tam_fijo", true) ? mPrefs.getInt("tam_global", 1) : c.tam; }
    private float opacidad(Control c)
    {
        return mPrefs.getBoolean("opac_fija", true) ? mPrefs.getInt("opac_global", 75) / 100f : c.opacidad;
    }

    /** Después de importar un perfil de controles. */
    public void recargar()
    {
        String json = mPrefs.getString("controles_v2", null);
        mControls = json == null ? null : Control.fromJson(json);
        if (mControls == null)
            mControls = predeterminados();
        rebuild();
    }

    // ------------------------------------------------------------ dibujar

    /** Vuelve a dibujar todo (también después de cambiar el mapeo del mando). */
    public void rebuild()
    {
        mLayer.removeAllViews();
        for (Control c : mControls) {
            if (c.mando != 0)
                c.tecla = mPad.keyForScreen(c.mando); // misma tecla que el botón del mando
            ControlView v = new ControlView(mActivity, c, mAccent, this);
            v.editando = mEditing;
            v.elegido = mEditing && c == mElegido;
            mLayer.addView(v);
        }
        placeAll();
        if (mEditing) {
            mLayer.addView(mToolbar);
            if (mElegido != null && mControls.contains(mElegido))
                mostrarAjuste();
        }
        refreshTurbo();
    }

    private void placeAll()
    {
        int w = mLayer.getWidth(), h = mLayer.getHeight();
        for (int i = 0; i < mLayer.getChildCount(); i++) {
            if (!(mLayer.getChildAt(i) instanceof ControlView))
                continue;
            ControlView v = (ControlView) mLayer.getChildAt(i);
            Control c = v.control;
            float size = dp(Control.TALLAS[talla(c)]);
            if (c.cruceta)
                size *= Control.CRUCETA;
            float bw = c.pastilla ? size * Control.PASTILLA : size;
            v.badge = c.cruceta || c.tecla < 0 ? 0 : Math.max(dp(9), size * 0.17f);
            v.pad = v.badge * 0.6f;
            int vw = Math.round(bw + 2 * v.pad), vh = Math.round(size + 2 * v.pad);
            v.setLayoutParams(new FrameLayout.LayoutParams(vw, vh));
            v.setX(c.x * w - vw / 2f);
            v.setY(c.y * h - vh / 2f);
            // La transparencia se aplica dentro del botón: así la insignia puede tener la suya
            v.setAlpha(1);
            v.bodyAlpha = opacidad(c);
            boolean propia = mPrefs.getBoolean("insig_propia", false);
            v.showBadge = mPrefs.getBoolean("insig_mostrar", true);
            v.badgeAlpha = propia ? mPrefs.getInt("insig_opac", 100) / 100f : v.bodyAlpha;
            v.badgeColor = propia ? (mPrefs.getInt("insig_color", mAccent) | 0xFF000000) : 0;
            v.labelOwn = c.labelOwn || mPrefs.getBoolean("label_own", false);
            v.labelColor = c.labelOwn ? c.labelColor : mPrefs.getInt("label_color", mAccent);
            v.labelAlpha = c.labelOwn ? c.labelOpacity : mPrefs.getInt("label_opacity", 100) / 100f;
            v.badgeLeft = c.x < 0.5f; // L con su letra a la izquierda, R a la derecha
            v.skin = mPrefs.getInt("skin", 0);
            v.invalidate();
        }
    }

    public void refreshTurbo()
    {
        for (int i = 0; mLayer != null && i < mLayer.getChildCount(); i++) {
            View v = mLayer.getChildAt(i);
            if (v instanceof ControlView && ((ControlView) v).control.tecla == Control.TURBO) {
                v.setActivated(mActions.turboOn());
                v.invalidate();
            }
        }
    }

    private void save()
    {
        mPrefs.edit().putString("controles_v2", Control.toJson(mControls)).apply();
    }

    // ------------------------------------------------------------ visibilidad

    /** "Mostrar controles" del menú. */
    public void setUserVisible(boolean visible)
    {
        mUserVisible = visible;
        updateVisibility();
    }

    /** Se llama al tocar la pantalla: los controles vuelven aunque haya un mando. */
    public void onScreenTouched()
    {
        if (!mShown) {
            mPad.releaseAll();
            mShown = true;
            updateVisibility();
        }
    }

    /** Se llama cuando llega una tecla de un mando o teclado físico. */
    public void onPhysicalInput()
    {
        if (mShown && !mEditing) {
            releasePressed();
            mShown = false;
            updateVisibility();
        }
    }

    public void keyboardVisible(boolean shown)
    {
        if (shown) releasePressed();
        else mShown = true; // Closing the touch keyboard restores the touch input mode.
        mKeyboard = shown;
        updateVisibility();
    }

    private void updateVisibility()
    {
        if (mLayer != null)
            mLayer.setVisibility((!mKeyboard && mUserVisible && mShown) || mEditing ? View.VISIBLE : View.GONE);
    }

    private static boolean isPhysical(InputDevice d)
    {
        if (d == null || d.isVirtual())
            return false;
        int s = d.getSources();
        boolean pad = (s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
            || (s & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
        boolean keyboard = (s & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
            && d.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC;
        return pad || keyboard;
    }

    private void updateForDevices()
    {
        boolean connected = false;
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            connected |= isPhysical(device);
            if (PadMapper.isGamepad(device)) mPad.connected(device);
        }
        mShown = !connected;
        updateVisibility();
    }

    @Override public void onInputDeviceAdded(int id) { updateForDevices(); }
    @Override public void onInputDeviceRemoved(int id) { mPad.disconnected(id); updateForDevices(); }
    @Override public void onInputDeviceChanged(int id) { updateForDevices(); }

    // ------------------------------------------------------------ pulsar

    @Override
    public void press(int tecla)
    {
        if (tecla > 0)
            mActions.keyDown(tecla);
        else if (tecla < 0)
            mActions.action(tecla);
        refreshTurbo();
    }

    @Override
    public void release(int tecla)
    {
        if (tecla > 0)
            mActions.keyUp(tecla);
    }

    // ------------------------------------------------------------ editor

    public boolean isEditing() { return mEditing; }

    public void releasePressed()
    {
        if (mLayer == null) return;
        for (int i = 0; i < mLayer.getChildCount(); i++) {
            View child = mLayer.getChildAt(i);
            if (child instanceof ControlView)
                ((ControlView) child).releasePressed();
        }
    }

    public void startEditing(Runnable onDone)
    {
        releasePressed();
        mEditing = true;
        mElegido = null;
        mToolbar = buildToolbar(onDone);
        rebuild();
        updateVisibility();
    }

    /** Barra del editor: arriba, translúcida, y se arrastra desde ⠿ para no estorbar. */
    @SuppressLint("ClickableViewAccessibility")
    private View buildToolbar(Runnable onDone)
    {
        LinearLayout bar = new LinearLayout(mActivity);
        bar.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xB3000000 | (mActivity.getColor(R.color.rmxp_tinta) & 0xFFFFFF));
        bg.setCornerRadius(dp(22));
        bar.setBackground(bg);
        int pad = Math.round(dp(6));
        bar.setPadding(pad, pad, pad, pad);

        TextView handle = new TextView(mActivity);
        handle.setText("⠿");
        handle.setTextSize(24);
        handle.setTextColor(mActivity.getColor(R.color.rmxp_niebla));
        handle.setGravity(Gravity.CENTER);
        handle.setContentDescription(mActivity.getString(R.string.rmxp_edit_move_toolbar));
        bar.addView(handle, new LinearLayout.LayoutParams(Math.round(dp(40)), Math.round(dp(44))));
        handle.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY, baseX, baseY;
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = e.getRawX(); startY = e.getRawY();
                        baseX = bar.getX(); baseY = bar.getY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        bar.setX(Math.max(0, Math.min(mLayer.getWidth() - bar.getWidth(), baseX + e.getRawX() - startX)));
                        bar.setY(Math.max(0, Math.min(mLayer.getHeight() - bar.getHeight(), baseY + e.getRawY() - startY)));
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                    default: return false;
                }
            }
        });

        // Pocas opciones a la vista: Añadir, Opciones ▾ (lo demás) y Listo
        bar.addView(toolButton(R.string.rmxp_edit_add, v -> addControl()));
        Button mas = toolButton(R.string.rmxp_edit_more, null);
        mas.setOnClickListener(v -> showEditorOptions());
        bar.addView(mas);
        Button done = toolButton(R.string.rmxp_edit_done, v -> {
            mEditing = false;
            save();
            rebuild();
            updateVisibility();
            onDone.run();
        });
        ((GradientDrawable) done.getBackground()).setColor(mAccent);
        done.setTextColor(mActivity.getColor(R.color.rmxp_tinta));
        bar.addView(done);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        p.topMargin = Math.round(dp(8));
        bar.setLayoutParams(p);
        return bar;
    }

    private Button toolButton(int text, View.OnClickListener click)
    {
        Button b = new Button(mActivity);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(mActivity.getColor(R.color.rmxp_pizarra));
        bg.setCornerRadius(dp(14));
        b.setBackground(bg);
        b.setTextColor(mActivity.getColor(R.color.rmxp_niebla));
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinimumWidth(Math.round(dp(64)));
        b.setTextSize(13);
        b.setPadding(Math.round(dp(10)), 0, Math.round(dp(10)), 0);
        b.setText(text);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(dp(40)));
        p.leftMargin = p.rightMargin = Math.round(dp(4));
        b.setLayoutParams(p);
        return b;
    }

    private ControlView mDragging;
    private float mStartX, mStartY, mBaseX, mBaseY;
    private boolean mMoved;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean editTouch(ControlView v, MotionEvent e)
    {
        if (!mEditing)
            return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                boolean nuevo = mElegido != v.control;
                if (nuevo) {
                    mElegido = v.control;
                    for (int i = 0; i < mLayer.getChildCount(); i++)
                        if (mLayer.getChildAt(i) instanceof ControlView) {
                            ControlView o = (ControlView) mLayer.getChildAt(i);
                            o.elegido = o == v;
                            o.invalidate();
                        }
                }
                if (mPanel != null)
                    mLayer.removeView(mPanel); // se esconde mientras se arrastra
                mRecienElegido = nuevo;
                mDragging = v;
                mStartX = e.getRawX();
                mStartY = e.getRawY();
                mBaseX = v.getX();
                mBaseY = v.getY();
                mMoved = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getRawX() - mStartX, dy = e.getRawY() - mStartY;
                if (Math.abs(dx) > dp(8) || Math.abs(dy) > dp(8))
                    mMoved = true;
                if (mMoved && mDragging == v) {
                    v.setX(mBaseX + dx);
                    v.setY(mBaseY + dy);
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mDragging != v)
                    break;
                if (mMoved) {
                    // Se acomoda a una cuadrícula de 8 dp: así se alinean fácil, como en Winlator
                    float g = dp(8);
                    float cx = Math.round((v.getX() + v.getWidth() / 2f) / g) * g;
                    float cy = Math.round((v.getY() + v.getHeight() / 2f) / g) * g;
                    v.control.x = cx / mLayer.getWidth();
                    v.control.y = cy / mLayer.getHeight();
                    placeAll();
                    save();
                    rebuild();
                } else if (mRecienElegido) {
                    mostrarAjuste(); // primer toque: solo elegirlo
                } else {
                    mostrarAjuste();
                    editControl(v.control);
                }
                mDragging = null;
                break;
        }
        return true;
    }

    private Control mElegido;
    private View mPanel;
    private boolean mRecienElegido;

    /** Panel pequeño junto al botón elegido: tamaño − / +, editar y borrar. */
    private void mostrarAjuste()
    {
        ControlView cv = null;
        for (int i = 0; i < mLayer.getChildCount(); i++)
            if (mLayer.getChildAt(i) instanceof ControlView && ((ControlView) mLayer.getChildAt(i)).control == mElegido)
                cv = (ControlView) mLayer.getChildAt(i);
        if (cv == null)
            return;
        Control c = mElegido;
        LinearLayout p = new LinearLayout(mActivity);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE6000000 | (mActivity.getColor(R.color.rmxp_tinta) & 0xFFFFFF));
        bg.setCornerRadius(dp(18));
        p.setBackground(bg);
        int pad = Math.round(dp(4));
        p.setPadding(pad, pad, pad, pad);
        p.addView(chico("−", v -> cambiarTalla(c, -1)));
        p.addView(chico("+", v -> cambiarTalla(c, +1)));
        p.addView(chico("✎", v -> editControl(c)));
        if (!c.cruceta || mControls.size() > 1)
            p.addView(chico("🗑", v -> { mControls.remove(c); mElegido = null; save(); rebuild(); }));
        mPanel = p;
        p.setTag("rmxp_selected_control");
        mLayer.addView(p, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams dock = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        dock.topMargin = Math.round(dp(64));
        p.setLayoutParams(dock);
    }

    private Button chico(String t, View.OnClickListener click)
    {
        Button b = toolButton(R.string.rmxp_edit_add, click);
        b.setText(t);
        b.setMinWidth(0);
        b.setMinimumWidth(Math.round(dp(44)));
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    /** − / + del panel: si "igual para todos" estaba puesto, se apaga dejando a cada uno su talla actual. */
    private void cambiarTalla(Control c, int paso)
    {
        if (mPrefs.getBoolean("tam_fijo", true)) {
            int g = mPrefs.getInt("tam_global", 1);
            for (Control o : mControls)
                o.tam = g;
            mPrefs.edit().putBoolean("tam_fijo", false).apply();
        }
        c.tam = Math.max(0, Math.min(Control.TALLAS.length - 1, c.tam + paso));
        save();
        rebuild();
    }

    private void addControl()
    {
        Control c = Control.boton("b" + System.currentTimeMillis(), "", android.view.KeyEvent.KEYCODE_ENTER, 0.5f, 0.5f, 1, false);
        mControls.add(c);
        save();
        rebuild();
        editControl(c);
    }

    private void showEditorOptions()
    {
        com.hatkid.mkxpz.PanelLateral panel = new com.hatkid.mkxpz.PanelLateral(mActivity, R.string.rmxp_edit_more);
        LinearLayout box = panel.content;
        Switch badges = new Switch(mActivity);
        badges.setText(R.string.rmxp_badge_show);
        badges.setChecked(mPrefs.getBoolean("insig_mostrar", true));
        badges.setOnCheckedChangeListener((b, on) -> {
            mPrefs.edit().putBoolean("insig_mostrar", on).apply();
            placeAll();
        });
        box.addView(badges);
        panel.section(R.string.rmxp_pad_size);
        Switch tam_fijo = new Switch(mActivity);
        tam_fijo.setText(R.string.rmxp_global_size);
        tam_fijo.setChecked(mPrefs.getBoolean("tam_fijo", true));
        tam_fijo.setOnCheckedChangeListener((b, on) -> { mPrefs.edit().putBoolean("tam_fijo", on).apply(); placeAll(); });
        box.addView(tam_fijo);
        box.addView(chips(tallaNames(), mPrefs.getInt("tam_global", 1), i -> {
            tam_fijo.setChecked(true); mPrefs.edit().putInt("tam_global", i).apply(); placeAll();
        }));
        panel.section(R.string.rmxp_edit_opacity_all);
        Switch opac_fija = new Switch(mActivity);
        opac_fija.setText(R.string.rmxp_global_opacity);
        opac_fija.setChecked(mPrefs.getBoolean("opac_fija", true));
        opac_fija.setOnCheckedChangeListener((b, on) -> { mPrefs.edit().putBoolean("opac_fija", on).apply(); placeAll(); });
        box.addView(opac_fija);
        Apariencia.slider(panel, box, R.string.ap_button_opacity, 0, 100, mPrefs.getInt("opac_global", 75), " %", n -> {
            opac_fija.setChecked(true); mPrefs.edit().putInt("opac_global", n).apply(); placeAll();
        });
        panel.section(R.string.rmxp_edit_style);
        box.addView(chips(new String[] { mActivity.getString(R.string.rmxp_skin_modern),
            mActivity.getString(R.string.rmxp_skin_outline) }, mPrefs.getInt("skin", 0), i -> {
            mPrefs.edit().putInt("skin", i).apply(); placeAll();
        }));
        box.addView(panel.button(mActivity.getString(R.string.ap_color_opacity), () -> chooseColors(null)));
        box.addView(panel.button(mActivity.getString(R.string.rmxp_edit_reset), () -> {
            new AlertDialog.Builder(mActivity).setMessage(R.string.rmxp_reset_layout_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.rmxp_edit_reset, (d, which) -> {
                    mControls = predeterminados(); mElegido = null; save(); rebuild(); panel.dismiss();
                }).show();
        }));
    }

    private void chooseColors() { chooseColors(null); }

    private void chooseColors(Control target)
    {
        PanelLateral panel = new PanelLateral(mActivity, R.string.ap_color_opacity);
        if (mControls.isEmpty()) {
            panel.content.addView(panel.button(mActivity.getString(R.string.rmxp_edit_add), () -> { panel.dismiss(); addControl(); }));
            return;
        }
        String[] names = new String[mControls.size() + 1];
        names[0] = mActivity.getString(R.string.ap_all);
        for (int i = 0; i < mControls.size(); i++) {
            Control c = mControls.get(i);
            names[i + 1] = c.cruceta ? mActivity.getString(R.string.rmxp_dpad) :
                c.etiqueta.isEmpty() ? Teclas.nombre(c.tecla) : c.etiqueta;
        }
        Apariencia.choice(panel, panel.top, R.string.ap_apply_to, names,
            target == null ? 0 : mControls.indexOf(target) + 1,
            n -> chooseColors(n == 0 ? null : mControls.get(n - 1)));
        LinearLayout previewRow = new LinearLayout(mActivity);
        previewRow.setGravity(Gravity.CENTER_VERTICAL);
        previewRow.setBaselineAligned(false);
        previewRow.addView(panel.label(mActivity.getString(R.string.ap_preview), 13), new LinearLayout.LayoutParams(0, -2, 1));
        Control sample = target == null ? mControls.stream().filter(c -> !c.cruceta && c.tecla > 0).findFirst().orElse(mControls.get(0)) : target;
        ControlView preview = new ControlView(mActivity, sample, mAccent, new ControlView.Listener() {
            public void press(int key) {} public void release(int key) {}
            public boolean editTouch(ControlView v, MotionEvent e) { return true; }
        });
        previewRow.addView(preview, new LinearLayout.LayoutParams(Math.round(dp(sample.pastilla ? 104 : 72)), Math.round(dp(72))));
        Runnable refresh = () -> {
            placeAndSave();
            preview.badge = dp(10); preview.pad = dp(8);
            preview.skin = mPrefs.getInt("skin", 0);
            preview.bodyAlpha = opacidad(sample);
            preview.showBadge = mPrefs.getBoolean("insig_mostrar", true);
            boolean ownBadge = mPrefs.getBoolean("insig_propia", false);
            preview.badgeAlpha = ownBadge ? mPrefs.getInt("insig_opac", 100)/100f : preview.bodyAlpha;
            preview.badgeColor = ownBadge ? mPrefs.getInt("insig_color", mAccent) : 0;
            preview.labelOwn = sample.labelOwn || mPrefs.getBoolean("label_own", false);
            preview.labelColor = sample.labelOwn ? sample.labelColor : mPrefs.getInt("label_color", mAccent);
            preview.labelAlpha = sample.labelOwn ? sample.labelOpacity : mPrefs.getInt("label_opacity", 100)/100f;
            preview.invalidate();
        };
        LinearLayout buttons = Apariencia.card(panel, R.string.ap_buttons, true);
        final boolean[] updatingSystem = {false};
        Switch system = Apariencia.toggle(panel, buttons, R.string.ap_system_color,
            (target == null ? mControls.get(0) : target).systemColor, on -> {
                if (updatingSystem[0]) return;
                if (target == null) for (Control c : mControls) { c.color = on ? 0xFFFFFFFF : mAccent; c.systemColor = on; }
                else { target.color = on ? 0xFFFFFFFF : mAccent; target.systemColor = on; }
                chooseColors(target); placeAndSave();
            });
        Control first = target == null ? mControls.get(0) : target;
        Apariencia.color(panel, buttons, first.systemColor ? mActivity.getColor(R.color.rmxp_pizarra) : first.color, color -> {
            if (target == null) for (Control c : mControls) { c.color = color; c.systemColor = false; }
            else { target.color = color; target.systemColor = false; }
            updatingSystem[0] = true; system.setChecked(false); updatingSystem[0] = false;
            refresh.run();
        });
        Apariencia.slider(panel, buttons, R.string.ap_button_opacity, 0, 100, Math.round(opacidad(first)*100), " %", n -> {
            if (target == null) mPrefs.edit().putBoolean("opac_fija", true).putInt("opac_global", n).apply();
            else {
                // Materialize the previous global value before switching to individual opacity.
                if (mPrefs.getBoolean("opac_fija", true)) {
                    for (Control c : mControls) c.opacidad = opacidad(c);
                    mPrefs.edit().putBoolean("opac_fija", false).apply();
                }
                target.opacidad = n / 100f;
            }
            refresh.run();
        });
        LinearLayout labels = Apariencia.card(panel, R.string.ap_labels, false);
        LinearLayout extra = Apariencia.column(mActivity);
        boolean own = target == null ? mPrefs.getBoolean("label_own", false) : target.labelOwn;
        Apariencia.toggle(panel, labels, R.string.ap_independent, own, on -> {
            if (target == null) {
                for (Control c : mControls) c.labelOwn = false;
                mPrefs.edit().putBoolean("label_own", on).apply();
            }
            else target.labelOwn = on;
            extra.setVisibility(on ? View.VISIBLE : View.GONE); refresh.run();
        });
        labels.addView(extra); extra.setVisibility(own ? View.VISIBLE : View.GONE);
        Apariencia.color(panel, extra, target == null ? mPrefs.getInt("label_color", mAccent) : target.labelColor, color -> {
            if (target == null) {
                for (Control c : mControls) c.labelOwn = false;
                mPrefs.edit().putInt("label_color", color).apply();
            } else target.labelColor = color;
            refresh.run();
        });
        Apariencia.slider(panel, extra, R.string.ap_label_opacity, 0, 100,
            target == null ? mPrefs.getInt("label_opacity", 100) : Math.round(target.labelOpacity*100), " %", n -> {
                if (target == null) {
                    for (Control c : mControls) c.labelOwn = false;
                    mPrefs.edit().putInt("label_opacity", n).apply();
                } else target.labelOpacity = n/100f;
                refresh.run();
            });
        // Keep the pre-existing small key-badge preferences available without renaming their meaning.
        LinearLayout badges = Apariencia.card(panel, R.string.rmxp_color_badges, false);
        Apariencia.toggle(panel, badges, R.string.ap_independent, mPrefs.getBoolean("insig_propia", false), on -> {
            mPrefs.edit().putBoolean("insig_propia", on).apply(); refresh.run();
        });
        Apariencia.color(panel, badges, mPrefs.getInt("insig_color", mAccent), color -> {
            mPrefs.edit().putInt("insig_color", color & 0xFFFFFF).apply(); refresh.run();
        });
        Apariencia.slider(panel, badges, R.string.ap_label_opacity, 0, 100, mPrefs.getInt("insig_opac",100), " %", n -> {
            mPrefs.edit().putInt("insig_opac", n).apply(); refresh.run();
        });
        panel.top.addView(previewRow); refresh.run();
        panel.content.addView(panel.button(mActivity.getString(R.string.rmxp_edit_reset), () -> {
            if (target == null) {
                for (Control c : mControls) { c.color = 0xFFFFFFFF; c.systemColor = true; c.labelOwn = false; c.opacidad = 1; }
                mPrefs.edit().putBoolean("label_own", false).putBoolean("opac_fija",true).putInt("opac_global",75)
                    .putBoolean("insig_propia",false).apply();
            } else { target.color = 0xFFFFFFFF; target.systemColor = true; target.labelOwn = false; target.opacidad = 1; }
            placeAndSave(); chooseColors(target);
        }));
    }

    private String[] tallaNames()
    {
        return new String[] { mActivity.getString(R.string.rmxp_size_s), mActivity.getString(R.string.rmxp_size_m),
            mActivity.getString(R.string.rmxp_size_l) };
    }

    /** Fila de opciones fijas (solo una elegida). */
    private View chips(String[] names, int selected, IntSink sink)
    {
        android.widget.RadioGroup g = new android.widget.RadioGroup(mActivity);
        g.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < names.length; i++) {
            android.widget.RadioButton r = new android.widget.RadioButton(new android.view.ContextThemeWrapper(mActivity, R.style.RmxpChip), null, 0);
            r.setClickable(true);
            r.setFocusable(true);
            r.setButtonDrawable((android.graphics.drawable.Drawable) null);
            r.setBackgroundResource(R.drawable.rmxp_chip);
            r.setTextColor(mActivity.getColorStateList(R.color.rmxp_chip_text));
            r.setGravity(Gravity.CENTER);
            r.setTextSize(12);
            r.setPadding(0, 0, 0, 0);
            r.setText(names[i]);
            r.setId(View.generateViewId());
            g.addView(r, new android.widget.RadioGroup.LayoutParams(0, Math.round(dp(44)), 1));
            if (i == selected) g.check(r.getId());
        }
        g.setOnCheckedChangeListener((group, id) -> {
            View selectedView = group.findViewById(id);
            if (selectedView != null) sink.put(group.indexOfChild(selectedView));
        });
        return g;
    }

    private TextView hint(int text)
    {
        TextView t = new TextView(mActivity);
        t.setText(text);
        t.setTextSize(12);
        t.setAlpha(0.7f);
        return t;
    }

    /** Ventana para editar un control: etiqueta, tecla, forma, tamaño, color, opacidad, borrar. */
    private void editControl(Control c)
    {
        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(dp(20));
        box.setPadding(pad, Math.round(dp(8)), pad, 0);

        if (!c.cruceta) {
            EditText label = new EditText(mActivity);
            label.setHint(R.string.rmxp_edit_label);
            label.setText(c.etiqueta);
            label.setSingleLine();
            label.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
                public void onTextChanged(CharSequence s, int a, int b, int d) {}
                public void afterTextChanged(Editable s) { c.etiqueta = s.toString(); placeAndSave(); }
            });
            box.addView(label);

            Button key = new Button(mActivity);
            key.setAllCaps(false);
            key.setText(mActivity.getString(R.string.rmxp_edit_key) + ": " + Teclas.nombre(c.tecla));
            key.setOnClickListener(v -> pickKey(mActivity, true, k -> {
                c.tecla = k;
                if (c.mando != 0)
                    mPad.setScreenKey(c.mando, k); // el botón del mando también cambia
                key.setText(mActivity.getString(R.string.rmxp_edit_key) + ": " + Teclas.nombre(k));
                rebuild();
                save();
                editControl(c);
            }));
            box.addView(key);

            Switch pill = new Switch(mActivity);
            pill.setText(R.string.rmxp_edit_pill);
            pill.setChecked(c.pastilla);
            pill.setOnCheckedChangeListener((b, on) -> { c.pastilla = on; placeAndSave(); });
            box.addView(pill);
        }

        // Tamaño y opacidad propios: solo si "Igual para todos" está apagado en la barra
        box.addView(label(R.string.rmxp_pad_size));
        if (mPrefs.getBoolean("tam_fijo", true))
            box.addView(hint(R.string.rmxp_edit_locked));
        else
            box.addView(chips(tallaNames(), c.tam, i -> { c.tam = i; placeAndSave(); }));
        com.hatkid.mkxpz.PanelLateral panel = new com.hatkid.mkxpz.PanelLateral(mActivity, R.string.rmxp_pad_edit);
        box.setPadding(0, 0, 0, 0);
        panel.content.addView(box);
        panel.content.addView(panel.button(mActivity.getString(R.string.ap_color_opacity), () -> chooseColors(c)));
        if (!c.cruceta || mControls.size() > 1)
            panel.content.addView(panel.button(mActivity.getString(R.string.rmxp_edit_delete), () -> {
                mControls.remove(c); mElegido = null; save(); rebuild(); panel.dismiss();
            }));
    }

    private void placeAndSave()
    {
        placeAll();
        save();
    }

    private TextView label(int text)
    {
        TextView t = new TextView(mActivity);
        t.setText(text);
        t.setPadding(0, Math.round(dp(12)), 0, 0);
        return t;
    }

    private interface IntSink { void put(int v); }

    // ------------------------------------------------------------ elegir tecla

    public interface KeySink { void put(int keycode); }

    /** Searchable key/action picker, shared by touch controls and physical input mappings. */
    public static void pickKey(Activity a, boolean withActions, KeySink sink)
    {
        pickKey(a, withActions, null, sink);
    }

    public static void pickKey(Activity a, boolean withActions, String source, KeySink sink)
    {
        PanelLateral panel = new PanelLateral(a, R.string.rmxp_edit_choose);
        if (source != null) panel.top.addView(panel.label(a.getString(R.string.map_source) + ": " + source, 14));
        EditText search = new EditText(a);
        search.setSingleLine(); search.setHint(R.string.map_search);
        search.setTextColor(a.getColor(R.color.rmxp_niebla));
        search.setContentDescription(a.getString(R.string.map_search));
        panel.top.addView(search);
        GridLayout grid = new GridLayout(a);
        panel.content.addView(grid);
        final int[] category = {0};
        Runnable render = () -> {
            String query = search.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
            java.util.LinkedHashMap<Integer,String> keys = new java.util.LinkedHashMap<>();
            keys.put(0,a.getString(R.string.rmxp_unassigned));
            if (withActions && (category[0] == 0 || !query.isEmpty()))
                for (int action : new int[]{Control.MENU,Control.TURBO,Control.TECLADO,Control.OCULTAR})
                    keys.put(action,Teclas.nombre(action));
            if (!query.isEmpty() || category[0] == (withActions ? 2 : 1)) keys.putAll(Teclas.TODAS);
            else if (!withActions || category[0] == 1)
                for (int code : Teclas.COMUNES) keys.put(code,Teclas.nombre(code));
            grid.removeAllViews();
            int columns = withActions && category[0] == 0 && query.isEmpty() ? 2 : 3;
            grid.setColumnCount(columns);
            for (Map.Entry<Integer,String> entry : keys.entrySet()) {
                int code = entry.getKey();
                String role = code < 0 ? a.getString(R.string.map_action) : Teclas.uso(a,code);
                String text = entry.getValue() + (role.isEmpty() ? "" : "\n" + role);
                if (!query.isEmpty() && !text.toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
                Button key = panel.button(text, () -> { panel.dismiss(); sink.put(code); });
                key.setTextSize(12); key.setPadding(0,0,0,0);
                key.setContentDescription(entry.getValue());
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = panel.dp((Math.min(360,a.getResources().getDisplayMetrics().widthPixels /
                    a.getResources().getDisplayMetrics().density)-40)/columns)-panel.dp(6);
                lp.height = panel.dp(56); lp.setMargins(panel.dp(3),panel.dp(3),panel.dp(3),panel.dp(3));
                grid.addView(key,lp);
            }
        };
        com.hatkid.mkxpz.Apariencia.choice(panel,panel.top,R.string.map_destination,
            withActions ? new String[]{a.getString(R.string.map_actions),a.getString(R.string.map_common),a.getString(R.string.map_all_keys)} :
                new String[]{a.getString(R.string.map_common),a.getString(R.string.map_all_keys)},
            0,n -> { category[0]=n;render.run(); });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){}
            public void afterTextChanged(Editable s){render.run();}
        });
        render.run();
    }
}
