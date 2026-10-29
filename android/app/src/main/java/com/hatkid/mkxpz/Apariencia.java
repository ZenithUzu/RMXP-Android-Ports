package com.hatkid.mkxpz;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import java.util.Locale;
import java.util.function.IntConsumer;

/** Shared, theme-aware color and opacity editor. Values chosen by the user stay literal. */
public final class Apariencia {
    private Apariencia() {}

    public static LinearLayout column(Activity a) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        return box;
    }

    public static LinearLayout card(PanelLateral panel, int title, boolean expanded) {
        Activity a = panel.activity;
        LinearLayout outer = column(a), body = column(a);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(a.getColor(R.color.rmxp_pizarra));
        bg.setCornerRadius(panel.dp(14));
        outer.setBackground(bg);
        outer.setPadding(panel.dp(12), 0, panel.dp(12), panel.dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, panel.dp(6), 0, panel.dp(6));
        panel.content.addView(outer, lp);
        Button header = panel.button(a.getString(title) + (expanded ? "  ▴" : "  ▾"), () -> {});
        header.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        outer.addView(header);
        outer.addView(body);
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        header.setOnClickListener(v -> {
            boolean show = body.getVisibility() != View.VISIBLE;
            body.setVisibility(show ? View.VISIBLE : View.GONE);
            header.setText(a.getString(title) + (show ? "  ▴" : "  ▾"));
        });
        return body;
    }

    public static Switch toggle(PanelLateral panel, LinearLayout box, int name, boolean checked,
                                java.util.function.Consumer<Boolean> change) {
        Switch s = new Switch(panel.activity);
        s.setText(name); s.setTextColor(panel.activity.getColor(R.color.rmxp_niebla));
        s.setMinHeight(panel.dp(48)); s.setChecked(checked);
        s.setOnCheckedChangeListener((b, on) -> change.accept(on));
        box.addView(s);
        return s;
    }

    public static SeekBar slider(PanelLateral panel, LinearLayout box, int name, int min, int max,
                                 int value, String unit, IntConsumer change) {
        TextView label = panel.label("", 14);
        box.addView(label);
        SeekBar bar = new SeekBar(panel.activity);
        bar.setMin(min); bar.setMax(max); bar.setProgress(Math.max(min, Math.min(max, value)));
        bar.setContentDescription(panel.activity.getString(name));
        ColorStateList accent = ColorStateList.valueOf(panel.activity.getColor(R.color.rmxp_ambar));
        bar.setProgressTintList(accent); bar.setThumbTintList(accent);
        label.setText(panel.activity.getString(name) + "   " + bar.getProgress() + unit);
        box.addView(bar, new LinearLayout.LayoutParams(-1, panel.dp(48)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int n, boolean user) {
                label.setText(panel.activity.getString(name) + "   " + n + unit);
                if (user) change.accept(n);
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
        return bar;
    }

    public static Spinner choice(PanelLateral panel, LinearLayout box, int name, String[] options,
                                  int selected, IntConsumer change) {
        box.addView(panel.label(panel.activity.getString(name), 14));
        Spinner spinner = new Spinner(panel.activity);
        spinner.setAdapter(new ArrayAdapter<String>(panel.activity, android.R.layout.simple_spinner_dropdown_item, options));
        spinner.setSelection(selected);
        spinner.setContentDescription(panel.activity.getString(name));
        box.addView(spinner, new LinearLayout.LayoutParams(-1, panel.dp(48)));
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            int previous = selected;
            public void onItemSelected(AdapterView<?> parent, View v, int index, long id) {
                if (v instanceof TextView) ((TextView) v).setTextColor(panel.activity.getColor(R.color.rmxp_niebla));
                if (index != previous) { previous = index; change.accept(index); }
            }
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        return spinner;
    }

    public static void color(PanelLateral panel, LinearLayout box, int initial, IntConsumer change) {
        Activity a = panel.activity;
        float[] hsv = new float[3]; Color.colorToHSV(initial | 0xFF000000, hsv);
        LinearLayout pickerRow = new LinearLayout(a);
        LinearLayout row = column(a);
        EditText hex = new EditText(a);
        hex.setSingleLine(); hex.setTextColor(a.getColor(R.color.rmxp_niebla));
        hex.setHint("#RRGGBB"); hex.setContentDescription(a.getString(R.string.ap_hex));
        View swatch = new View(a);
        row.addView(hex, new LinearLayout.LayoutParams(-1, panel.dp(48)));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, panel.dp(40));
        sp.setMargins(panel.dp(8), 0, 0, 0); row.addView(swatch, sp);
        final boolean[] syncing = {false};
        final ColorSquare[] square = new ColorSquare[1];
        final SeekBar[] axes = new SeekBar[2];
        SeekBar hue = new SeekBar(a);
        hue.setMax(360); hue.setProgress(Math.round(hsv[0]));
        hue.setContentDescription(a.getString(R.string.ap_hue));
        Runnable refresh = () -> {
            int c = Color.HSVToColor(hsv);
            syncing[0] = true;
            hex.setText(String.format(Locale.ROOT, "#%06X", c & 0xFFFFFF));
            hex.setError(null); syncing[0] = false;
            GradientDrawable bg = new GradientDrawable(); bg.setColor(c); bg.setCornerRadius(panel.dp(8));
            bg.setStroke(panel.dp(1), a.getColor(R.color.rmxp_liquen)); swatch.setBackground(bg);
            square[0].invalidate();
            if (axes[0] != null) axes[0].setProgress(Math.round(hsv[1]*100));
            if (axes[1] != null) axes[1].setProgress(Math.round(hsv[2]*100));
        };
        square[0] = new ColorSquare(a, hsv, () -> { refresh.run(); change.accept(Color.HSVToColor(hsv)); });
        pickerRow.addView(square[0], new LinearLayout.LayoutParams(0, panel.dp(96), 1));
        pickerRow.addView(row, new LinearLayout.LayoutParams(panel.dp(132), -2));
        box.addView(pickerRow);
        GradientDrawable rainbow = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[] {Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED});
        hue.setProgressDrawable(rainbow);
        box.addView(hue, new LinearLayout.LayoutParams(-1, panel.dp(48)));
        refresh.run();
        hue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int n, boolean user) {
                if (user) { hsv[0] = n; refresh.run(); change.accept(Color.HSVToColor(hsv)); }
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
        hex.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) {
                if (syncing[0]) return;
                String text = s.toString();
                if (!text.matches("#[0-9a-fA-F]{6}")) { hex.setError(a.getString(R.string.ap_invalid_hex)); return; }
                int c = Color.parseColor(text);
                Color.colorToHSV(c, hsv); hue.setProgress(Math.round(hsv[0]));
                swatch.setBackgroundColor(c); square[0].invalidate();
                if (axes[0] != null) axes[0].setProgress(Math.round(hsv[1]*100));
                if (axes[1] != null) axes[1].setProgress(Math.round(hsv[2]*100));
                hex.setError(null); change.accept(c);
            }
        });
        // Native sliders expose both axes to keyboards, controllers and accessibility services.
        LinearLayout precise = column(a);
        precise.setVisibility(View.GONE);
        box.addView(panel.button(a.getString(R.string.ap_precise) + "  ▾", () ->
            precise.setVisibility(precise.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE)));
        box.addView(precise);
        axes[0] = slider(panel, precise, R.string.ap_saturation, 0, 100, Math.round(hsv[1]*100), " %", n -> {
            hsv[1] = n/100f; refresh.run(); change.accept(Color.HSVToColor(hsv));
        });
        axes[1] = slider(panel, precise, R.string.ap_brightness, 0, 100, Math.round(hsv[2]*100), " %", n -> {
            hsv[2] = n/100f; refresh.run(); change.accept(Color.HSVToColor(hsv));
        });
    }

    private static final class ColorSquare extends View {
        private final float[] hsv;
        private final Runnable change;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ColorSquare(Activity a, float[] hsv, Runnable change) {
            super(a); this.hsv = hsv; this.change = change;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            paint.setShader(new LinearGradient(0, 0, w, 0, Color.WHITE,
                Color.HSVToColor(new float[]{hsv[0], 1, 1}), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(new LinearGradient(0, 0, 0, h, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint); paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3);
            paint.setColor(Color.BLACK); c.drawCircle(hsv[1]*w, (1-hsv[2])*h, 10, paint);
            paint.setColor(Color.WHITE); c.drawCircle(hsv[1]*w, (1-hsv[2])*h, 8, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN || e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                getParent().requestDisallowInterceptTouchEvent(true);
                hsv[1] = Math.max(0, Math.min(1, e.getX()/Math.max(1,getWidth())));
                hsv[2] = 1-Math.max(0, Math.min(1, e.getY()/Math.max(1,getHeight())));
                change.run(); return true;
            }
            if (e.getActionMasked() == MotionEvent.ACTION_UP) { performClick(); return true; }
            return e.getActionMasked() == MotionEvent.ACTION_CANCEL;
        }
        public boolean performClick() { super.performClick(); return true; }
    }
}
