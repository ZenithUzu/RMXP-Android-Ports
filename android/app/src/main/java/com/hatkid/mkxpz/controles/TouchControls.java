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
    }

    // Colores que se pueden elegir (el blanco es el "sin color")
    static final int[] COLORES = { 0xFFFFFFFF, 0xFFE5394F, 0xFFF2B544, 0xFF4CAF50, 0xFF2196F3,
        0xFF9C27B0, 0xFFFF7043, 0xFF00BCD4, 0xFF607D8B, 0xFF212121 };

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
                c.tecla = mPad.keyFor(c.mando); // misma tecla que el botón del mando
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
            v.badge = c.cruceta ? 0 : Math.max(dp(9), size * 0.17f);
            v.pad = v.badge * 0.6f;
            int vw = Math.round(bw + 2 * v.pad), vh = Math.round(size + 2 * v.pad);
            v.setLayoutParams(new FrameLayout.LayoutParams(vw, vh));
            v.setX(c.x * w - vw / 2f);
            v.setY(c.y * h - vh / 2f);
            // La transparencia se aplica dentro del botón: así la insignia puede tener la suya
            v.setAlpha(1);
            v.bodyAlpha = opacidad(c);
            boolean propia = mPrefs.getBoolean("insig_propia", false);
            v.badgeAlpha = propia ? mPrefs.getInt("insig_opac", 100) / 100f : v.bodyAlpha;
            v.badgeColor = propia ? mPrefs.getInt("insig_color", 0) : 0;
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
            mShown = true;
            updateVisibility();
        }
    }

    /** Se llama cuando llega una tecla de un mando o teclado físico. */
    public void onPhysicalInput()
    {
        if (mShown && !mEditing) {
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
        for (int id : InputDevice.getDeviceIds())
            connected |= isPhysical(InputDevice.getDevice(id));
        mShown = !connected;
        updateVisibility();
    }

    @Override public void onInputDeviceAdded(int id) { updateForDevices(); }
    @Override public void onInputDeviceRemoved(int id) { updateForDevices(); }
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
        b.setPadding(Math.round(dp(14)), 0, Math.round(dp(14)), 0);
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
        box.addView(chips(opacNames(), indexOf(Control.OPACIDADES, mPrefs.getInt("opac_global", 75)), i -> {
            opac_fija.setChecked(true); mPrefs.edit().putInt("opac_global", Control.OPACIDADES[i]).apply(); placeAll();
        }));
        panel.section(R.string.rmxp_edit_style);
        box.addView(chips(new String[] { mActivity.getString(R.string.rmxp_skin_modern),
            mActivity.getString(R.string.rmxp_skin_outline) }, mPrefs.getInt("skin", 0), i -> {
            mPrefs.edit().putInt("skin", i).apply(); placeAll();
        }));
        panel.section(R.string.rmxp_color_buttons);
        box.addView(swatches(COLORES, color -> {
            for (Control c : mControls) c.color = color;
            placeAndSave();
        }));
        box.addView(panel.button(mActivity.getString(R.string.rmxp_badge_own), () -> { panel.dismiss(); chooseColors(); }));
        box.addView(panel.button(mActivity.getString(R.string.rmxp_edit_reset), () -> {
            new AlertDialog.Builder(mActivity).setMessage(R.string.rmxp_reset_layout_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.rmxp_edit_reset, (d, which) -> {
                    mControls = predeterminados(); mElegido = null; save(); rebuild(); panel.dismiss();
                }).show();
        }));
    }

    /**
     * Colores: uno para todos los botones a la vez y, aparte, el de las letras pequeñas
     * (insignias) con su propia transparencia, si se activa "Color y transparencia propios".
     */
    private void chooseColors()
    {
        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(dp(20));
        box.setPadding(pad, Math.round(dp(8)), pad, 0);

        box.addView(label(R.string.rmxp_color_buttons));
        box.addView(swatches(COLORES, color -> {
            for (Control c : mControls)
                c.color = color;
            placeAndSave();
        }));

        box.addView(label(R.string.rmxp_color_badges));
        Switch own = new Switch(mActivity);
        own.setText(R.string.rmxp_badge_own);
        own.setChecked(mPrefs.getBoolean("insig_propia", false));
        box.addView(own);
        LinearLayout extra = new LinearLayout(mActivity);
        extra.setOrientation(LinearLayout.VERTICAL);
        int[] badgeColors = new int[COLORES.length + 1];
        badgeColors[0] = mAccent; // el ámbar de siempre
        System.arraycopy(COLORES, 0, badgeColors, 1, COLORES.length);
        extra.addView(swatches(badgeColors, color -> {
            mPrefs.edit().putInt("insig_color", color & 0xFFFFFF).apply();
            placeAll();
        }));
        extra.addView(chips(opacNames(), indexOf(Control.OPACIDADES, mPrefs.getInt("insig_opac", 100)), i -> {
            mPrefs.edit().putInt("insig_opac", Control.OPACIDADES[i]).apply();
            placeAll();
        }));
        extra.setVisibility(own.isChecked() ? View.VISIBLE : View.GONE);
        own.setOnCheckedChangeListener((b, on) -> {
            mPrefs.edit().putBoolean("insig_propia", on).apply();
            extra.setVisibility(on ? View.VISIBLE : View.GONE);
            placeAll();
        });
        box.addView(extra);
        box.addView(hint(R.string.rmxp_badge_hint));

        com.hatkid.mkxpz.PanelLateral panel = new com.hatkid.mkxpz.PanelLateral(mActivity, R.string.rmxp_edit_color);
        box.setPadding(0, 0, 0, 0);
        panel.content.addView(box);
    }

    private View swatches(int[] colors, IntSink sink)
    {
        GridLayout grid = new GridLayout(mActivity);
        grid.setColumnCount(6);
        for (int color : colors) {
            View swatch = new View(mActivity);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(color); bg.setCornerRadius(dp(12));
            bg.setStroke(Math.round(dp(2)), 0x66888888);
            swatch.setBackground(bg);
            swatch.setContentDescription(String.format("#%06X", color & 0xFFFFFF));
            swatch.setOnClickListener(v -> sink.put(color));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = lp.height = Math.round(dp(44));
            lp.setMargins(0, Math.round(dp(4)), Math.round(dp(4)), 0);
            grid.addView(swatch, lp);
        }
        return grid;
    }

    private String[] tallaNames()
    {
        return new String[] { mActivity.getString(R.string.rmxp_size_s), mActivity.getString(R.string.rmxp_size_m),
            mActivity.getString(R.string.rmxp_size_l) };
    }

    private static String[] opacNames()
    {
        String[] n = new String[Control.OPACIDADES.length];
        for (int i = 0; i < n.length; i++)
            n[i] = Control.OPACIDADES[i] + " %";
        return n;
    }

    private static int indexOf(int[] a, int v)
    {
        int best = 0;
        for (int i = 0; i < a.length; i++)
            if (Math.abs(a[i] - v) < Math.abs(a[best] - v))
                best = i;
        return best;
    }

    /** Fila de opciones fijas (solo una elegida). */
    private View chips(String[] names, int selected, IntSink sink)
    {
        android.widget.RadioGroup g = new android.widget.RadioGroup(mActivity);
        g.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < names.length; i++) {
            android.widget.RadioButton r = new android.widget.RadioButton(new android.view.ContextThemeWrapper(mActivity, R.style.RmxpChip), null, 0);
            r.setButtonDrawable((android.graphics.drawable.Drawable) null);
            r.setBackgroundResource(R.drawable.rmxp_chip);
            r.setTextColor(mActivity.getColorStateList(R.color.rmxp_chip_text));
            r.setGravity(Gravity.CENTER);
            r.setTextSize(12);
            r.setPadding(0, 0, 0, 0);
            r.setText(names[i]);
            r.setId(View.generateViewId());
            r.setChecked(i == selected);
            final int index = i;
            r.setOnCheckedChangeListener((b, on) -> { if (on) sink.put(index); });
            g.addView(r, new android.widget.RadioGroup.LayoutParams(0, Math.round(dp(44)), 1));
        }
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
                    mPad.setKey(c.mando, k); // el botón del mando también cambia
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
        box.addView(label(R.string.rmxp_edit_opacity));
        if (mPrefs.getBoolean("opac_fija", true))
            box.addView(hint(R.string.rmxp_edit_locked));
        else
            box.addView(chips(opacNames(), indexOf(Control.OPACIDADES, Math.round(c.opacidad * 100)),
                i -> { c.opacidad = Control.OPACIDADES[i] / 100f; placeAndSave(); }));

        box.addView(label(R.string.rmxp_edit_color));
        box.addView(swatches(COLORES, color -> { c.color = color; placeAndSave(); }));

        com.hatkid.mkxpz.PanelLateral panel = new com.hatkid.mkxpz.PanelLateral(mActivity, R.string.rmxp_pad_edit);
        box.setPadding(0, 0, 0, 0);
        panel.content.addView(box);
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

    /** Cuadrícula con acciones arriba y todas las teclas abajo, como en Kirin. */
    public static void pickKey(Activity a, boolean withActions, KeySink sink)
    {
        float dp = a.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        GridLayout grid = new GridLayout(a);
        grid.setColumnCount(4);
        com.hatkid.mkxpz.PanelLateral panel = new com.hatkid.mkxpz.PanelLateral(a, R.string.rmxp_edit_choose);

        if (withActions) {
            LinearLayout actions = new LinearLayout(a);
            actions.setGravity(Gravity.CENTER);
            for (int act : new int[] { Control.TECLADO, Control.TURBO, Control.MENU, Control.OCULTAR })
                actions.addView(keyCell(a, dp, Teclas.nombre(act), () -> { sink.put(act); panel.dismiss(); }));
            box.addView(actions);
        }
        for (Map.Entry<Integer, String> k : Teclas.TODAS.entrySet())
            grid.addView(keyCell(a, dp, k.getValue(), () -> { sink.put(k.getKey()); panel.dismiss(); }));
        box.addView(grid);

        panel.content.addView(box);
    }

    private static Button keyCell(Activity a, float dp, String text, Runnable click)
    {
        Button b = new Button(a);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setTextColor(a.getColor(R.color.rmxp_niebla));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(a.getColor(R.color.rmxp_pizarra)); bg.setCornerRadius(10 * dp);
        b.setBackground(bg);
        b.setOnClickListener(v -> click.run());
        GridLayout.LayoutParams p = new GridLayout.LayoutParams();
        p.width = Math.round((Math.min(360, a.getResources().getDisplayMetrics().widthPixels / dp) - 40) / 4 * dp) - Math.round(6 * dp);
        p.height = Math.round(52 * dp);
        p.setMargins(Math.round(3 * dp), Math.round(3 * dp), Math.round(3 * dp), Math.round(3 * dp));
        b.setLayoutParams(p);
        return b;
    }
}
