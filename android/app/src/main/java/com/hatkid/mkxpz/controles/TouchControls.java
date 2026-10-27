package com.hatkid.mkxpz.controles;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
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
import android.widget.SeekBar;
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
    private FrameLayout mLayer;
    private List<Control> mControls;
    private boolean mEditing, mShown = true, mUserVisible = true;
    private View mToolbar;

    public TouchControls(Activity activity, SharedPreferences prefs, Actions actions)
    {
        mActivity = activity;
        mPrefs = prefs;
        mActions = actions;
        mAccent = activity.getColor(R.color.rmxp_ambar);
    }

    private float dp(float v) { return v * mActivity.getResources().getDisplayMetrics().density; }

    public void attachTo(ViewGroup parent)
    {
        mLayer = new FrameLayout(mActivity);
        parent.addView(mLayer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mLayer.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot)
                placeAll();
        });
        mControls = Control.fromJson(mPrefs.getString("controles_json", Control.toJson(Control.predeterminados())));
        rebuild();
        InputManager im = (InputManager) mActivity.getSystemService(Activity.INPUT_SERVICE);
        im.registerInputDeviceListener(this, new Handler(Looper.getMainLooper()));
        updateForDevices();
    }

    // ------------------------------------------------------------ dibujar

    private void rebuild()
    {
        mLayer.removeAllViews();
        for (Control c : mControls)
            mLayer.addView(new ControlView(mActivity, c, mAccent, this));
        placeAll();
        if (mEditing)
            mLayer.addView(mToolbar);
        refreshTurbo();
    }

    private void placeAll()
    {
        float global = mPrefs.getInt("opacidad_global", 80) / 100f;
        int w = mLayer.getWidth(), h = mLayer.getHeight();
        for (int i = 0; i < mLayer.getChildCount(); i++) {
            if (!(mLayer.getChildAt(i) instanceof ControlView))
                continue;
            ControlView v = (ControlView) mLayer.getChildAt(i);
            Control c = v.control;
            int size = Math.round(dp(c.tam));
            int vw = c.pastilla ? Math.round(size * 1.6f) : size;
            v.setLayoutParams(new FrameLayout.LayoutParams(vw, size));
            v.setX(c.x * w - vw / 2f);
            v.setY(c.y * h - size / 2f);
            v.setAlpha(c.opacidad * global);
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
        mPrefs.edit().putString("controles_json", Control.toJson(mControls)).apply();
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

    private void updateVisibility()
    {
        if (mLayer != null)
            mLayer.setVisibility((mUserVisible && mShown) || mEditing ? View.VISIBLE : View.GONE);
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

    public void startEditing(Runnable onDone)
    {
        mEditing = true;
        mToolbar = buildToolbar(onDone);
        rebuild();
        updateVisibility();
    }

    private View buildToolbar(Runnable onDone)
    {
        LinearLayout bar = new LinearLayout(mActivity);
        bar.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(mActivity.getColor(R.color.rmxp_tinta));
        bg.setCornerRadius(dp(24));
        bar.setBackground(bg);
        int pad = Math.round(dp(8));
        bar.setPadding(pad, pad, pad, pad);
        bar.addView(toolButton(R.string.rmxp_edit_add, v -> addControl()));
        bar.addView(toolButton(R.string.rmxp_edit_opacity_all, v -> globalOpacity()));
        bar.addView(toolButton(R.string.rmxp_edit_reset, v -> {
            mControls = Control.predeterminados();
            save();
            rebuild();
        }));
        Button done = toolButton(R.string.rmxp_edit_done, v -> {
            mEditing = false;
            save();
            rebuild();
            updateVisibility();
            onDone.run();
        });
        done.setBackgroundTintList(ColorStateList.valueOf(mAccent));
        done.setTextColor(mActivity.getColor(R.color.rmxp_tinta));
        bar.addView(done);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        bar.setLayoutParams(p);
        return bar;
    }

    private Button toolButton(int text, View.OnClickListener click)
    {
        Button b = new Button(mActivity, null, 0, R.style.RmxpButton);
        b.setText(text);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(dp(44)));
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
                    v.control.x = (v.getX() + v.getWidth() / 2f) / mLayer.getWidth();
                    v.control.y = (v.getY() + v.getHeight() / 2f) / mLayer.getHeight();
                    save();
                } else {
                    editControl(v.control);
                }
                mDragging = null;
                break;
        }
        return true;
    }

    private void addControl()
    {
        Control c = Control.boton("b" + System.currentTimeMillis(), "", android.view.KeyEvent.KEYCODE_ENTER, 0.5f, 0.5f, 64, false);
        mControls.add(c);
        save();
        rebuild();
        editControl(c);
    }

    private void globalOpacity()
    {
        SeekBar bar = new SeekBar(mActivity);
        bar.setMax(90);
        bar.setProgress(mPrefs.getInt("opacidad_global", 80) - 10);
        bar.setPadding(Math.round(dp(24)), Math.round(dp(24)), Math.round(dp(24)), Math.round(dp(8)));
        bar.setOnSeekBarChangeListener(new SimpleSeek(p -> {
            mPrefs.edit().putInt("opacidad_global", p + 10).apply();
            placeAll();
        }));
        new AlertDialog.Builder(mActivity).setTitle(R.string.rmxp_edit_opacity_all).setView(bar)
            .setPositiveButton(R.string.rmxp_edit_done, null).show();
    }

    /** Ventana para editar un control: etiqueta, tecla, forma, tamaño, color, opacidad, borrar. */
    private void editControl(Control c)
    {
        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(dp(20));
        box.setPadding(pad, Math.round(dp(8)), pad, 0);

        AlertDialog[] dialog = new AlertDialog[1];
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
                key.setText(mActivity.getString(R.string.rmxp_edit_key) + ": " + Teclas.nombre(k));
                rebuild();
                save();
            }));
            box.addView(key);

            Switch pill = new Switch(mActivity);
            pill.setText(R.string.rmxp_edit_pill);
            pill.setChecked(c.pastilla);
            pill.setOnCheckedChangeListener((b, on) -> { c.pastilla = on; placeAndSave(); });
            box.addView(pill);
        }

        box.addView(label(R.string.rmxp_pad_size));
        box.addView(seek(32, 220, c.tam, v -> { c.tam = v; placeAndSave(); }));
        box.addView(label(R.string.rmxp_edit_opacity));
        box.addView(seek(10, 100, Math.round(c.opacidad * 100), v -> { c.opacidad = v / 100f; placeAndSave(); }));

        box.addView(label(R.string.rmxp_edit_color));
        LinearLayout colors = new LinearLayout(mActivity);
        for (int color : COLORES) {
            View swatch = new View(mActivity);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(color);
            d.setStroke(Math.round(dp(2)), 0x66888888);
            swatch.setBackground(d);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Math.round(dp(28)), Math.round(dp(28)));
            p.rightMargin = Math.round(dp(6));
            swatch.setOnClickListener(v -> { c.color = color; placeAndSave(); });
            colors.addView(swatch, p);
        }
        box.addView(colors);

        ScrollView scroll = new ScrollView(mActivity);
        scroll.addView(box);
        AlertDialog.Builder b = new AlertDialog.Builder(mActivity).setView(scroll)
            .setPositiveButton(R.string.rmxp_edit_done, null);
        if (!c.cruceta || mControls.size() > 1)
            b.setNegativeButton(R.string.rmxp_edit_delete, (d, w) -> {
                mControls.remove(c);
                save();
                rebuild();
            });
        dialog[0] = b.show();
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

    private static class SimpleSeek implements SeekBar.OnSeekBarChangeListener
    {
        final IntSink sink;
        SimpleSeek(IntSink sink) { this.sink = sink; }
        public void onProgressChanged(SeekBar s, int p, boolean user) { if (user) sink.put(p); }
        public void onStartTrackingTouch(SeekBar s) {}
        public void onStopTrackingTouch(SeekBar s) {}
    }

    private SeekBar seek(int min, int max, int value, IntSink sink)
    {
        SeekBar bar = new SeekBar(mActivity);
        bar.setMax(max - min);
        bar.setProgress(value - min);
        bar.setOnSeekBarChangeListener(new SimpleSeek(p -> sink.put(p + min)));
        return bar;
    }

    // ------------------------------------------------------------ elegir tecla

    public interface KeySink { void put(int keycode); }

    /** Cuadrícula con acciones arriba y todas las teclas abajo, como en Kirin. */
    public static void pickKey(Activity a, boolean withActions, KeySink sink)
    {
        float dp = a.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        GridLayout grid = new GridLayout(a);
        grid.setColumnCount(6);
        AlertDialog[] dialog = new AlertDialog[1];

        if (withActions) {
            LinearLayout actions = new LinearLayout(a);
            actions.setGravity(Gravity.CENTER);
            for (int act : new int[] { Control.TECLADO, Control.TURBO, Control.MENU, Control.OCULTAR })
                actions.addView(keyCell(a, dp, Teclas.nombre(act), () -> { sink.put(act); dialog[0].dismiss(); }));
            box.addView(actions);
        }
        for (Map.Entry<Integer, String> k : Teclas.TODAS.entrySet())
            grid.addView(keyCell(a, dp, k.getValue(), () -> { sink.put(k.getKey()); dialog[0].dismiss(); }));
        box.addView(grid);

        ScrollView scroll = new ScrollView(a);
        scroll.addView(box);
        dialog[0] = new AlertDialog.Builder(a).setTitle(R.string.rmxp_edit_choose).setView(scroll)
            .setNegativeButton(android.R.string.cancel, null).show();
    }

    private static Button keyCell(Activity a, float dp, String text, Runnable click)
    {
        Button b = new Button(a);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setOnClickListener(v -> click.run());
        GridLayout.LayoutParams p = new GridLayout.LayoutParams();
        p.width = Math.round(64 * dp);
        p.height = Math.round(52 * dp);
        p.setMargins(Math.round(3 * dp), Math.round(3 * dp), Math.round(3 * dp), Math.round(3 * dp));
        b.setLayoutParams(p);
        return b;
    }
}
