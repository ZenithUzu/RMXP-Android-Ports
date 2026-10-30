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

    private static GradientDrawable frame(PanelLateral panel, int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color); bg.setCornerRadius(panel.dp(radius));
        bg.setStroke(panel.dp(1), (panel.activity.getColor(R.color.rmxp_musgo) & 0xFFFFFF) | 0x55000000);
        return bg;
    }

    public static LinearLayout card(PanelLateral panel, int title, boolean expanded) {
        Activity a = panel.activity;
        LinearLayout outer = column(a), body = column(a);
        outer.setBackground(frame(panel, (a.getColor(R.color.rmxp_pizarra) & 0xFFFFFF) | 0x40000000, 12));
        outer.setPadding(panel.dp(12), 0, panel.dp(12), panel.dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, panel.dp(6), 0, panel.dp(6));
        panel.content.addView(outer, lp);
        TextView header = panel.label(a.getString(title) + (expanded ? "  ⌃" : "  ⌄"), 15);
        header.setTypeface(null, Typeface.BOLD); header.setPadding(0, 0, 0, 0);
        header.setFocusable(true); header.setClickable(true);
        outer.addView(header, new LinearLayout.LayoutParams(-1, panel.dp(32)));
        outer.addView(body); body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        header.setOnClickListener(v -> {
            boolean show = body.getVisibility() != View.VISIBLE;
            body.setVisibility(show ? View.VISIBLE : View.GONE);
            header.setText(a.getString(title) + (show ? "  ⌃" : "  ⌄"));
        });
        return body;
    }

    public static void headerControl(PanelLateral panel, LinearLayout body, View control, int width) {
        LinearLayout outer=(LinearLayout)body.getParent();
        View heading=outer.getChildAt(0);outer.removeView(heading);
        if(control.getParent()!=null)((ViewGroup)control.getParent()).removeView(control);
        LinearLayout row=new LinearLayout(panel.activity);row.setBaselineAligned(false);row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(heading,new LinearLayout.LayoutParams(0,panel.dp(36),1));
        row.addView(control,new LinearLayout.LayoutParams(panel.dp(width),panel.dp(36)));
        outer.addView(row,0);
    }

    public static Switch toggle(PanelLateral panel, LinearLayout box, int name, boolean checked,
                                java.util.function.Consumer<Boolean> change) {
        return toggle(panel, box, name, 0, checked, change);
    }

    public static Switch toggle(PanelLateral panel, LinearLayout box, int name, int icon, boolean checked,
                                java.util.function.Consumer<Boolean> change) {
        Activity a = panel.activity;
        LinearLayout row = new LinearLayout(a); row.setBaselineAligned(false); row.setGravity(Gravity.CENTER_VERTICAL);
        if (icon != 0) {
            ImageView image = new ImageView(a); image.setImageResource(icon);
            image.setImageTintList(ColorStateList.valueOf(a.getColor(R.color.rmxp_ambar)));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(panel.dp(20), panel.dp(20));
            ip.setMargins(0, 0, panel.dp(12), 0); row.addView(image, ip);
        }
        Switch sw = new Switch(a); sw.setText(name); sw.setTextSize(13); sw.setIncludeFontPadding(false);
        sw.setTextColor(a.getColor(R.color.rmxp_niebla)); sw.setMinHeight(0); sw.setMinimumHeight(0);
        ColorStateList thumb = new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
            new int[]{a.getColor(R.color.rmxp_ambar), a.getColor(R.color.rmxp_liquen)});
        sw.setThumbTintList(thumb); sw.setTrackTintList(ColorStateList.valueOf(a.getColor(R.color.rmxp_musgo)));
        sw.setChecked(checked); sw.setOnCheckedChangeListener((b,on) -> change.accept(on));
        row.addView(sw, new LinearLayout.LayoutParams(0, panel.dp(32), 1));
        box.addView(row); return sw;
    }

    public static LinearLayout segmented(PanelLateral panel, String[] names, int selected, IntConsumer change) {
        LinearLayout row = new LinearLayout(panel.activity); row.setBaselineAligned(false);
        row.setBackground(frame(panel, panel.activity.getColor(R.color.rmxp_pizarra), 10));
        row.setPadding(panel.dp(2), panel.dp(2), panel.dp(2), panel.dp(2));
        for (int i=0; i<names.length; i++) {
            final int index=i;
            TextView chip = new TextView(panel.activity);
            chip.setText(names[i]); chip.setTextSize(13); chip.setGravity(Gravity.CENTER);
            chip.setIncludeFontPadding(false); chip.setMaxLines(2);
            chip.setTextColor(panel.activity.getColor(i==selected ? R.color.rmxp_tinta : R.color.rmxp_niebla));
            int color=panel.activity.getColor(i==selected ? R.color.rmxp_ambar : R.color.rmxp_pizarra);
            android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
            GradientDrawable focus=frame(panel,color,8); focus.setStroke(panel.dp(2),panel.activity.getColor(R.color.rmxp_ambar));
            states.addState(new int[]{android.R.attr.state_focused},focus);
            states.addState(new int[]{},frame(panel,color,8)); chip.setBackground(states);
            chip.setFocusable(true); chip.setClickable(true); chip.setOnClickListener(v -> change.accept(index));
            row.addView(chip, new LinearLayout.LayoutParams(0, panel.dp(32), 1));
        }
        return row;
    }

    public static SeekBar slider(PanelLateral panel, LinearLayout box, int name, int min, int max,
                                 int value, String unit, IntConsumer change) {
        LinearLayout caption = new LinearLayout(panel.activity); caption.setBaselineAligned(false);
        TextView label=panel.label(panel.activity.getString(name),13), amount=panel.label(value+unit,13);
        label.setPadding(0,0,0,0); amount.setPadding(0,0,0,0); amount.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        caption.addView(label,new LinearLayout.LayoutParams(0,panel.dp(24),1));
        caption.addView(amount,new LinearLayout.LayoutParams(-2,panel.dp(24))); box.addView(caption);
        SeekBar bar=new SeekBar(panel.activity); bar.setMin(min); bar.setMax(max);
        bar.setProgress(Math.max(min,Math.min(max,value))); bar.setKeyProgressIncrement(1);
        bar.setContentDescription(panel.activity.getString(name));
        ColorStateList accent=ColorStateList.valueOf(panel.activity.getColor(R.color.rmxp_ambar));
        bar.setProgressTintList(accent); bar.setThumbTintList(accent);
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(panel.activity.getColor(R.color.rmxp_musgo)));
        bar.setPadding(panel.dp(4),0,panel.dp(4),0);
        amount.setText(bar.getProgress()+unit); box.addView(bar,new LinearLayout.LayoutParams(-1,panel.dp(28)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int n,boolean user){amount.setText(n+unit);if(user)change.accept(n);}
            public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}
        }); return bar;
    }

    public static Spinner choice(PanelLateral panel, LinearLayout box, int name, String[] options,
                                  int selected, IntConsumer change) {
        Activity a=panel.activity;
        LinearLayout row=new LinearLayout(a); row.setGravity(Gravity.CENTER_VERTICAL); row.setBaselineAligned(false);
        row.setBackground(frame(panel,(a.getColor(R.color.rmxp_pizarra)&0xFFFFFF)|0x55000000,10));
        row.setPadding(panel.dp(8),0,panel.dp(4),0);
        TextView label=panel.label(a.getString(name)+":",13); label.setPadding(0,0,panel.dp(8),0);
        row.addView(label,new LinearLayout.LayoutParams(-2,panel.dp(36)));
        Spinner spinner=new Spinner(a);
        spinner.setAdapter(new ArrayAdapter<String>(a,android.R.layout.simple_spinner_dropdown_item,options){
            @Override public View getView(int position,View convert,ViewGroup parent){
                TextView v=(TextView)super.getView(position,convert,parent);
                v.setTextSize(13);v.setTextColor(a.getColor(R.color.rmxp_niebla));v.setSingleLine();v.setMinHeight(0);v.setMinimumHeight(0);v.setHeight(panel.dp(32));
                v.setPadding(0,0,0,0);v.setGravity(Gravity.CENTER_VERTICAL);return v;
            }
        });
        spinner.setSelection(selected); spinner.setContentDescription(a.getString(name));
        row.addView(spinner,new LinearLayout.LayoutParams(0,panel.dp(36),1));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,panel.dp(36));lp.setMargins(0,panel.dp(4),0,panel.dp(4));
        box.addView(row,lp);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            int previous=selected;
            public void onItemSelected(AdapterView<?> parent,View v,int index,long id){if(index!=previous){previous=index;change.accept(index);}}
            public void onNothingSelected(AdapterView<?> parent){}
        });return spinner;
    }

    /** Inline HSV plane, hue strip, swatch and #RRGGBB: the same layout for every color. */
    public static void color(PanelLateral panel, LinearLayout box, int initial, IntConsumer change) {
        Activity a=panel.activity; float[] hsv=new float[3];Color.colorToHSV(initial|0xFF000000,hsv);
        LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setBaselineAligned(false);
        LinearLayout plane=column(a), value= new LinearLayout(a);value.setGravity(Gravity.CENTER_VERTICAL);value.setBaselineAligned(false);
        EditText hex=new EditText(a);hex.setSingleLine();hex.setTextSize(13);hex.setSelectAllOnFocus(true);
        hex.setTextColor(a.getColor(R.color.rmxp_niebla));hex.setHint("#RRGGBB");hex.setContentDescription(a.getString(R.string.ap_hex));
        hex.setPadding(panel.dp(6),0,panel.dp(6),0);hex.setBackground(frame(panel,a.getColor(R.color.rmxp_tinta),8));
        hex.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        View swatch=new View(a);value.addView(swatch,new LinearLayout.LayoutParams(panel.dp(28),panel.dp(36)));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,panel.dp(36),1);hp.setMargins(panel.dp(6),0,0,0);value.addView(hex,hp);
        final boolean[] syncing={false};final ColorSquare[] square=new ColorSquare[1];
        SeekBar hue=new SeekBar(a);hue.setMax(360);hue.setKeyProgressIncrement(1);hue.setProgress(Math.round(hsv[0]));
        hue.setContentDescription(a.getString(R.string.ap_hue));
        Runnable refresh=()->{
            int c=Color.HSVToColor(hsv);syncing[0]=true;hex.setText(String.format(Locale.ROOT,"#%06X",c&0xFFFFFF));
            hex.setError(null);syncing[0]=false;swatch.setBackground(frame(panel,c,6));square[0].invalidate();
            square[0].setContentDescription(a.getString(R.string.ap_saturation)+" "+Math.round(hsv[1]*100)+" %, "+a.getString(R.string.ap_brightness)+" "+Math.round(hsv[2]*100)+" %");
        };
        square[0]=new ColorSquare(a,hsv,()->{refresh.run();change.accept(Color.HSVToColor(hsv));});
        plane.addView(square[0],new LinearLayout.LayoutParams(-1,panel.dp(56)));
        GradientDrawable rainbow=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{Color.RED,Color.YELLOW,Color.GREEN,Color.CYAN,Color.BLUE,Color.MAGENTA,Color.RED});
        rainbow.setCornerRadius(panel.dp(4));hue.setPadding(panel.dp(4),0,panel.dp(4),0);
        hue.setProgressDrawable(new android.graphics.drawable.InsetDrawable(rainbow,0,panel.dp(8),0,panel.dp(8)));
        hue.setThumbTintList(ColorStateList.valueOf(a.getColor(R.color.rmxp_ambar)));
        plane.addView(hue,new LinearLayout.LayoutParams(-1,panel.dp(24)));
        row.addView(plane,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(panel.dp(136),panel.dp(56));vp.setMargins(panel.dp(10),0,0,panel.dp(24));
        row.addView(value,vp);box.addView(row);refresh.run();
        hue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int n,boolean user){if(user){hsv[0]=n;refresh.run();change.accept(Color.HSVToColor(hsv));}}
            public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}
        });
        hex.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}
            public void afterTextChanged(Editable s){
                if(syncing[0])return;String text=s.toString();
                if(!text.matches("#[0-9a-fA-F]{6}")){hex.setError(a.getString(R.string.ap_invalid_hex));return;}
                int c=Color.parseColor(text);Color.colorToHSV(c,hsv);hue.setProgress(Math.round(hsv[0]));
                refresh.run();hex.setSelection(hex.length());change.accept(c);
            }
        });
    }

    private static final class ColorSquare extends View {
        private final float[] hsv;
        private final Runnable change;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ColorSquare(Activity a, float[] hsv, Runnable change) {
            super(a); this.hsv = hsv; this.change = change;
            setFocusable(true);
        }
        @Override public boolean onKeyDown(int key, android.view.KeyEvent e) {
            if(key==android.view.KeyEvent.KEYCODE_DPAD_LEFT) hsv[1]=Math.max(0,hsv[1]-.01f);
            else if(key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT) hsv[1]=Math.min(1,hsv[1]+.01f);
            else if(key==android.view.KeyEvent.KEYCODE_DPAD_DOWN) hsv[2]=Math.max(0,hsv[2]-.01f);
            else if(key==android.view.KeyEvent.KEYCODE_DPAD_UP) hsv[2]=Math.min(1,hsv[2]+.01f);
            else return super.onKeyDown(key,e);
            change.run();return true;
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
