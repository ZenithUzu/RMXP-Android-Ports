package com.hatkid.mkxpz;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
    private static final int[] COLORES = { 0xFFF2B544, 0xFFFFFFFF, 0xFF4CAF50, 0xFF00BCD4,
        0xFFE5394F, 0xFFFF7043, 0xFF9C27B0, 0xFF212121 };

    private final Activity mActivity;
    private final SharedPreferences mPrefs;
    private final IntSupplier mSpeed;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private TextView mLabel;
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
                        mPrefs.edit().putFloat("med_x", v.getX() / parent.getWidth())
                            .putFloat("med_y", v.getY() / parent.getHeight()).apply();
                        return true;
                }
                return false;
            }
        });
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
        mLabel.setX(mPrefs.getFloat("med_x", 0) * parent.getWidth());
        mLabel.setY(mPrefs.getFloat("med_y", 0) * parent.getHeight());
    }

    private void apply()
    {
        mLabel.setTextColor(mPrefs.getInt("med_color", COLORES[0]));
        boolean any = false;
        for (int i = 0; i < KEYS.length; i++)
            any |= on(i);
        mLabel.setVisibility(any ? View.VISIBLE : View.GONE);
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
            if (on(2)) items.add("RAM " + Debug.getPss() / 1024 + " MB");
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
            mLabel.setText(String.join(items.size() > 2 ? "\n" : "   ", items));
        }
        mLastFrames = frames;
        mLastCpu = cpu;
        mLastTime = now;
    }

    /** Ventana para elegir qué datos ver y el color. */
    public void showSettings()
    {
        float dp = mActivity.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(mActivity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * dp);
        box.setPadding(pad, Math.round(8 * dp), pad, 0);
        for (int i = 0; i < KEYS.length; i++) {
            final int index = i;
            CheckBox c = new CheckBox(mActivity);
            c.setText(NAMES[i]);
            c.setChecked(on(i));
            c.setOnCheckedChangeListener((b, checked) -> {
                mPrefs.edit().putBoolean(KEYS[index], checked).apply();
                apply();
                update();
            });
            box.addView(c);
        }
        LinearLayout colors = new LinearLayout(mActivity);
        colors.setPadding(0, Math.round(12 * dp), 0, 0);
        for (int color : COLORES) {
            View swatch = new View(mActivity);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(color);
            d.setStroke(Math.round(2 * dp), 0x66888888);
            swatch.setBackground(d);
            swatch.setOnClickListener(v -> { mPrefs.edit().putInt("med_color", color).apply(); apply(); });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Math.round(28 * dp), Math.round(28 * dp));
            p.rightMargin = Math.round(6 * dp);
            colors.addView(swatch, p);
        }
        box.addView(colors);
        TextView hint = new TextView(mActivity);
        hint.setText(R.string.rmxp_meter_hint);
        hint.setPadding(0, Math.round(12 * dp), 0, 0);
        box.addView(hint);
        ScrollView scroll = new ScrollView(mActivity);
        scroll.addView(box);
        new AlertDialog.Builder(mActivity).setTitle(R.string.rmxp_meter).setView(scroll)
            .setPositiveButton(R.string.rmxp_edit_done, null).show();
    }
}
