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
    private final IntSupplier mSpeed, mFrames;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private TextView mLabel;
    private int mLastFrames;
    private long mLastCpu, mLastTime;

    public Medidor(Activity activity, SharedPreferences prefs, IntSupplier speed, IntSupplier frames)
    {
        mActivity = activity;
        mPrefs = prefs;
        mSpeed = speed;
        mFrames = frames;
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

        mLastFrames = mFrames.getAsInt();
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

    public void dispose() { mHandler.removeCallbacksAndMessages(null); }

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
        int frames = mFrames.getAsInt();
        long cpu = cpuTicks();
        float seconds = Math.max(0.001f, (now - mLastTime) / 1000f);
        if (mLabel.getVisibility() == View.VISIBLE) {
            List<String> items = new ArrayList<>(), memory = new ArrayList<>(), battery = new ArrayList<>();
            if (on(0)) items.add(Math.round((frames - mLastFrames) / seconds) + " FPS");
            if (on(1)) items.add("x" + mSpeed.getAsInt());
            if (on(2)) memory.add("RAM " + ramMb() + " MB");
            if (on(3) && cpu >= 0 && mLastCpu >= 0) {
                int cores = Runtime.getRuntime().availableProcessors();
                memory.add("CPU " + Math.round((cpu - mLastCpu) / seconds / cores) + " %");
            }
            if (on(4)) {
                Intent b = mActivity.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (b != null) {
                    int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / Math.max(1, b.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
                    battery.add("BAT " + level + " %  " + b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10 + " °C");
                }
            }
            int mode=mPrefs.getInt("med_layout",2);
            List<String> lines=new ArrayList<>();
            if(!items.isEmpty())lines.add(String.join(" · ",items));
            if(!memory.isEmpty())lines.add(String.join(" · ",memory));
            if(!battery.isEmpty())lines.add(String.join(" · ",battery));
            String text=String.join(mode==1?" · ":"\n",lines);
            android.text.SpannableString styled=new android.text.SpannableString(text);
            int firstLine=text.indexOf('\n');
            if(mode!=1 && firstLine>0){
                styled.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),0,firstLine,0);
                styled.setSpan(new android.text.style.RelativeSizeSpan(1.12f),0,firstLine,0);
            }
            mLabel.setText(styled);
        }
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
        int[] icons={R.drawable.rmxp_ic_fps,R.drawable.rmxp_ic_speed,R.drawable.rmxp_ic_chip,R.drawable.rmxp_ic_chip,R.drawable.rmxp_ic_battery};
        for (int i = 0; i < KEYS.length; i++) {
            final int index = i;
            Apariencia.toggle(panel, data, NAMES[i], icons[i], on(i), checked -> {
                mPrefs.edit().putBoolean(KEYS[index], checked).apply(); refresh.run();
            });
        }
        LinearLayout appearance = Apariencia.card(panel, R.string.ap_appearance, true);
        LinearLayout layout=Apariencia.segmented(panel,new String[]{mActivity.getString(R.string.ap_one_line),mActivity.getString(R.string.ap_list)},
            mPrefs.getInt("med_layout",2)==2?1:0,index->{
                mPrefs.edit().putInt("med_layout",index==0?1:2).apply();refresh.run();showSettings(move);
            });
        Apariencia.headerControl(panel,appearance,layout,196);
        LinearLayout colorRow=new LinearLayout(mActivity);colorRow.setBaselineAligned(false);colorRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        colorRow.addView(panel.label(mActivity.getString(R.string.ap_text_color),13),new LinearLayout.LayoutParams(0,panel.dp(36),1));
        int currentColor=mPrefs.getInt("med_color",mActivity.getColor(R.color.rmxp_ambar));
        android.widget.Button color=panel.button(String.format(java.util.Locale.ROOT,"#%06X  ›",currentColor&0xFFFFFF),()->{});
        android.graphics.drawable.GradientDrawable dot=new android.graphics.drawable.GradientDrawable();
        dot.setColor(currentColor);dot.setCornerRadius(panel.dp(12));dot.setSize(panel.dp(24),panel.dp(24));
        color.setCompoundDrawablesRelativeWithIntrinsicBounds(dot,null,null,null);color.setCompoundDrawablePadding(panel.dp(8));
        colorRow.addView(color,new LinearLayout.LayoutParams(panel.dp(164),panel.dp(36)));appearance.addView(colorRow);
        LinearLayout picker=Apariencia.column(mActivity);appearance.addView(picker);picker.setVisibility(View.GONE);
        color.setOnClickListener(v->picker.setVisibility(picker.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        final boolean[] updatingSystem={false};
        android.widget.Switch system=Apariencia.toggle(panel,picker,R.string.ap_system_color,!mPrefs.contains("med_color"),on->{
            if(updatingSystem[0])return;
            if(on)mPrefs.edit().remove("med_color").apply();
            else mPrefs.edit().putInt("med_color",mActivity.getColor(R.color.rmxp_ambar)).apply();
            refresh.run();showSettings(move);
        });
        Apariencia.color(panel,picker,currentColor,c->{
            mPrefs.edit().putInt("med_color",c).apply();updatingSystem[0]=true;system.setChecked(false);updatingSystem[0]=false;
            dot.setColor(c);color.setText(String.format(java.util.Locale.ROOT,"#%06X  ›",c&0xFFFFFF));refresh.run();
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
        android.widget.Button moveButton=panel.button("✥  "+mActivity.getString(R.string.ap_move_meter), () -> {
            boolean any = false;
            for (int i = 0; i < KEYS.length; i++) any |= on(i);
            SharedPreferences.Editor edit = mPrefs.edit().putBoolean("med_enabled", true);
            if (!any) edit.putBoolean(KEYS[0], true);
            edit.apply(); refresh.run(); panel.dismiss(); move.run();
        });
        GradientDrawable moveBg=new GradientDrawable();moveBg.setColor(Color.TRANSPARENT);
        moveBg.setCornerRadius(panel.dp(8));moveBg.setStroke(panel.dp(1),mActivity.getColor(R.color.rmxp_ambar));
        moveButton.setBackground(moveBg);moveButton.setTextColor(mActivity.getColor(R.color.rmxp_ambar));
        LinearLayout.LayoutParams moveParams=new LinearLayout.LayoutParams(-1,panel.dp(36));moveParams.setMargins(0,panel.dp(6),0,0);
        appearance.addView(moveButton,moveParams);
        appearance.addView(panel.label(mActivity.getString(R.string.ap_move_hint),12));
    }
}
