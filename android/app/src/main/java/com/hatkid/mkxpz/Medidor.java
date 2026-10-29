package com.hatkid.mkxpz;

import android.annotation.SuppressLint;
import android.app.Activity;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * RMXP-Android-Ports: medidor de rendimiento (FPS, velocidad, RAM y CPU de la app, batería).
 * Con hasta 2 datos va en una línea; con más, en lista. Se arrastra para moverlo.
 */
public class Medidor
{
    private static final String[] KEYS = { "med_fps", "med_vel", "med_ram", "med_cpu", "med_bat" };
    private static final int[] NAMES = { R.string.rmxp_meter_fps, R.string.rmxp_meter_speed,
        R.string.rmxp_meter_ram, R.string.rmxp_meter_cpu, R.string.rmxp_meter_battery };
    private final Activity mActivity;
    private final SharedPreferences mPrefs;
    private final IntSupplier mSpeed;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private TextView mLabel, mPreview;
    private int mLastFrames;
    private long mLastCpu, mLastTime;

    public Medidor(Activity activity, SharedPreferences prefs, IntSupplier speed)
    {
        mActivity = activity;
        mPrefs = prefs;
        mSpeed = speed;
    }

    private boolean on(int i) { return mPrefs.getBoolean(KEYS[i], i == 0 && mPrefs.getInt("ver_fps", 0) == 1); }

    @SuppressLint("ClickableViewAccessibility")
    public void attachTo(ViewGroup layout)
    {
        float dp = mActivity.getResources().getDisplayMetrics().density;
        mLabel = new TextView(mActivity);
        mLabel.setTextSize(13);
        mLabel.setShadowLayer(3, 0, 0, Color.BLACK);
        mLabel.setLineSpacing(0, 1.1f);
        int pad = Math.round(6 * dp);
        mLabel.setPadding(pad * 2, pad, pad * 2, pad);
        layout.addView(mLabel, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Arrastrar para moverlo; la posición se guarda como fracción de la pantalla
        mLabel.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy;
            public boolean onTouch(View v, MotionEvent e) {
                View parent = (View) v.getParent();
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dx = v.getX() - e.getRawX();
                        dy = v.getY() - e.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        v.setX(Math.max(0, Math.min(e.getRawX() + dx, parent.getWidth() - v.getWidth())));
                        v.setY(Math.max(0, Math.min(e.getRawY() + dy, parent.getHeight() - v.getHeight())));
                        return true;
                    case MotionEvent.ACTION_UP:
                        mPrefs.edit().putInt("med_anchor", 4).putFloat("med_x", v.getX() / Math.max(1, parent.getWidth()))
                            .putFloat("med_y", v.getY() / Math.max(1, parent.getHeight())).apply();
                        return true;
                }
                return false;
            }
        });
        mLabel.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> place());
        layout.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> place());

        mLastFrames = MainActivity.nativeGetDrawnFrames();
        mLastTime = SystemClock.elapsedRealtime();
        mLastCpu = cpuTicks();
        apply();
        mHandler.post(new Runnable() {
            public void run() {
                update();
                mHandler.postDelayed(this, 1000);
            }
        });
    }

    private void place()
    {
        View parent = (View) mLabel.getParent();
        if (parent == null || parent.getWidth() == 0)
            return;
        float maxX = Math.max(0, parent.getWidth() - mLabel.getWidth());
        float maxY = Math.max(0, parent.getHeight() - mLabel.getHeight());
        int anchor = mPrefs.getInt("med_anchor", mPrefs.contains("med_x") ? 4 : 0);
        mLabel.setX(anchor == 4 ? Math.max(0, Math.min(maxX, mPrefs.getFloat("med_x", 0)*parent.getWidth())) :
            (anchor == 1 || anchor == 3 ? maxX : 0));
        mLabel.setY(anchor == 4 ? Math.max(0, Math.min(maxY, mPrefs.getFloat("med_y", 0)*parent.getHeight())) :
            (anchor >= 2 ? maxY : 0));
    }

    private void apply()
    {
        if (mLabel == null) return;
        mLabel.setTextColor(mPrefs.getInt("med_color", mActivity.getColor(R.color.rmxp_ambar)));
        mLabel.setTextSize(Math.max(10, Math.min(24, mPrefs.getInt("med_text_size", 13))));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(10 * mActivity.getResources().getDisplayMetrics().density);
        bg.setColor((Math.round(Math.max(0, Math.min(100, mPrefs.getInt("med_background", 35)))*2.55f) << 24)
            | (mActivity.getColor(R.color.rmxp_tinta) & 0xFFFFFF));
        mLabel.setBackground(bg);
        boolean any = false;
        for (int i = 0; i < KEYS.length; i++)
            any |= on(i);
        mLabel.setVisibility(any && mPrefs.getBoolean("med_enabled", true) ? View.VISIBLE : View.GONE);
        place();
    }

    /** Tiempo de CPU usado por la app (en "ticks" de 1/100 s), de /proc/self/stat. */
    private static long cpuTicks()
    {
        try (RandomAccessFile f = new RandomAccessFile("/proc/self/stat", "r")) {
            String line = f.readLine();
            String[] parts = line.substring(line.lastIndexOf(')') + 2).split(" "); // parts[0] = campo 3
            return Long.parseLong(parts[11]) + Long.parseLong(parts[12]); // utime + stime
        } catch (Exception e) {
            return -1;
        }
    }

    /** Memoria en uso (RSS) leída de /proc: rápida, a diferencia de Debug.getPss() (~50 ms). */
    private static long ramMb()
    {
        try (RandomAccessFile f = new RandomAccessFile("/proc/self/statm", "r")) {
            return Long.parseLong(f.readLine().split(" ")[1]) * 4096 / 1048576; // páginas de 4 KB
        } catch (Exception e) {
            return 0;
        }
    }

    private void update()
    {
        long now = SystemClock.elapsedRealtime();
        int frames = MainActivity.nativeGetDrawnFrames();
        long cpu = cpuTicks();
        float seconds = Math.max(0.001f, (now - mLastTime) / 1000f);
        if (mLabel.getVisibility() == View.VISIBLE) {
            List<String> items = new ArrayList<>();
            if (on(0)) items.add(Math.round((frames - mLastFrames) / seconds) + " FPS");
            if (on(1)) items.add("x" + mSpeed.getAsInt());
            if (on(2)) items.add("RAM " + ramMb() + " MB");
            if (on(3) && cpu >= 0 && mLastCpu >= 0) {
                int cores = Runtime.getRuntime().availableProcessors();
                items.add("CPU " + Math.round((cpu - mLastCpu) / seconds / cores) + " %");
            }
            if (on(4)) {
                Intent b = mActivity.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (b != null) {
                    int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / Math.max(1, b.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
                    items.add("BAT " + level + " %  " + b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10 + " °C");
                }
            }
            int mode = mPrefs.getInt("med_layout", 1);
            if (mode == 1) {
                StringBuilder compact = new StringBuilder();
                for (int i = 0; i < items.size(); i++) {
                    if (i > 0) compact.append(i % 2 == 0 ? "\n" : " · ");
                    compact.append(items.get(i));
                }
                mLabel.setText(compact.toString());
            } else mLabel.setText(String.join(mode == 2 || items.size() > 2 ? "\n" : "   ", items));
        }
        if (mPreview != null && mPreview.isAttachedToWindow()) {
            mPreview.setText(mLabel.getText());
            mPreview.setTextSize(mPrefs.getInt("med_text_size", 13));
            mPreview.setTextColor(mLabel.getCurrentTextColor());
            mPreview.setBackground(mLabel.getBackground().getConstantState().newDrawable());
        } else mPreview = null;
        mLastFrames = frames;
        mLastCpu = cpu;
        mLastTime = now;
    }

    /** Same themed side panel as the controls editor; edits preview immediately. */
    public void showSettings(Runnable move)
    {
        PanelLateral panel = new PanelLateral(mActivity, R.string.rmxp_meter);
        Runnable refresh = () -> { apply(); update(); };
        Apariencia.toggle(panel, panel.top, R.string.ap_show_meter, mPrefs.getBoolean("med_enabled", true), on -> {
            mPrefs.edit().putBoolean("med_enabled", on).apply(); refresh.run();
        });
        LinearLayout data = Apariencia.card(panel, R.string.ap_data, true);
        for (int i = 0; i < KEYS.length; i++) {
            final int index = i;
            Apariencia.toggle(panel, data, NAMES[i], on(i), checked -> {
                mPrefs.edit().putBoolean(KEYS[index], checked).apply(); refresh.run();
            });
        }
        LinearLayout appearance = Apariencia.card(panel, R.string.ap_appearance, true);
        Apariencia.choice(panel, appearance, R.string.ap_layout,
            new String[]{mActivity.getString(R.string.ap_compact), mActivity.getString(R.string.ap_list)},
            mPrefs.getInt("med_layout", 1) == 2 ? 1 : 0, index -> {
                mPrefs.edit().putInt("med_layout", index == 0 ? 1 : 2).apply(); refresh.run();
            });
        android.widget.Button color = panel.button(mActivity.getString(R.string.ap_text_color) + "  ▾", () -> {});
        appearance.addView(color);
        LinearLayout picker = Apariencia.column(mActivity);
        appearance.addView(picker); picker.setVisibility(View.GONE);
        color.setOnClickListener(v -> picker.setVisibility(picker.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        final boolean[] updatingSystem = {false};
        android.widget.Switch system = Apariencia.toggle(panel, picker, R.string.ap_system_color, !mPrefs.contains("med_color"), on -> {
            if (updatingSystem[0]) return;
            if (on) mPrefs.edit().remove("med_color").apply();
            else mPrefs.edit().putInt("med_color", mActivity.getColor(R.color.rmxp_ambar)).apply();
            refresh.run(); showSettings(move);
        });
        Apariencia.color(panel, picker, mPrefs.getInt("med_color", mActivity.getColor(R.color.rmxp_ambar)), c -> {
            mPrefs.edit().putInt("med_color", c).apply();
            updatingSystem[0] = true; system.setChecked(false); updatingSystem[0] = false;
            refresh.run();
        });
        Apariencia.slider(panel, appearance, R.string.ap_text_size, 10, 24, mPrefs.getInt("med_text_size",13), " sp", n -> {
            mPrefs.edit().putInt("med_text_size", n).apply(); refresh.run();
        });
        Apariencia.slider(panel, appearance, R.string.ap_background_opacity, 0, 100, mPrefs.getInt("med_background",35), " %", n -> {
            mPrefs.edit().putInt("med_background", n).apply(); refresh.run();
        });
        Apariencia.choice(panel, appearance, R.string.ap_position, new String[]{
            mActivity.getString(R.string.ap_top_left), mActivity.getString(R.string.ap_top_right),
            mActivity.getString(R.string.ap_bottom_left), mActivity.getString(R.string.ap_bottom_right),
            mActivity.getString(R.string.ap_custom)},
            mPrefs.getInt("med_anchor", mPrefs.contains("med_x") ? 4 : 0), n -> {
                mPrefs.edit().putInt("med_anchor", n).apply(); refresh.run();
            });
        appearance.addView(panel.button(mActivity.getString(R.string.ap_move_meter), () -> {
            boolean any = false;
            for (int i = 0; i < KEYS.length; i++) any |= on(i);
            SharedPreferences.Editor edit = mPrefs.edit().putBoolean("med_enabled", true);
            if (!any) edit.putBoolean(KEYS[0], true);
            edit.apply(); refresh.run(); panel.dismiss(); move.run();
        }));
        appearance.addView(panel.label(mActivity.getString(R.string.ap_move_hint),12));
        TextView preview = panel.label("60 FPS · ×1\nRAM 181 MB · CPU 6 %\nBAT 46 % · 38 °C", 13);
        preview.setContentDescription(mActivity.getString(R.string.ap_preview));
        if (mLabel != null) {
            preview.setText(mLabel.getText()); preview.setTextSize(mPrefs.getInt("med_text_size",13));
            preview.setTextColor(mLabel.getCurrentTextColor());
        }
        panel.top.addView(preview);
        mPreview = preview;
    }
}
