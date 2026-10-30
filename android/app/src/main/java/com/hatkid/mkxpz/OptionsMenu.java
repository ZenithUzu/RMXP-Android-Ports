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
import android.view.MotionEvent;
import android.os.SystemClock;
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
        teclado = new Teclado(activity, mPrefs, shown -> {
            if (shown) pad.releaseAll();
            controls.keyboardVisible(shown);
        });
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
        mRoot.findViewById(R.id.rmxp_meter).setOnClickListener(v -> mMedidor.showSettings(this::close));
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

    private int mMappingTab;

    @Override public void controllerConnected(android.view.InputDevice device)
    {
        mHandler.post(() -> {
            if (mRoot == null) return;
            controls.releasePressed();
            controls.rebuild();
        });
    }

    private boolean mCapturing, mCaptureAxis, mCaptureScreenshot;
    private int mCaptureSource, mLastMapped = -1;
    private TextView mCaptureHint;
    private int mNavigationDirection;
    private long mNavigationTime;

    private View mAutoMapSheet;

    private void mappingRows(PanelLateral panel, LinearLayout box, java.util.List<Integer> codes)
    {
        int columns=codes.size()==1 || mActivity.getResources().getConfiguration().fontScale>1.3f ||
            mActivity.getResources().getDisplayMetrics().widthPixels / mActivity.getResources().getDisplayMetrics().density<340?1:2;
        for (int i=0; i<codes.size(); i+=columns) {
            LinearLayout row = new LinearLayout(mActivity);
            row.setBaselineAligned(false);
            for (int column=0; column<columns; column++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,-1,1);
                lp.setMargins(column==0?0:panel.dp(4),panel.dp(3),column==columns-1?0:panel.dp(4),panel.dp(3));
                if (i+column>=codes.size()) { row.addView(new View(mActivity),lp); continue; }
                int code=codes.get(i+column), key=pad.keyFor(code);
                String name=code==0?mActivity.getString(R.string.map_capture_button):pad.name(code);
                String target=key==0?mActivity.getString(R.string.rmxp_unassigned):Teclas.nombre(key);
                LinearLayout cell=new LinearLayout(mActivity);
                cell.setGravity(android.view.Gravity.CENTER_VERTICAL); cell.setBaselineAligned(false);
                cell.setMinimumHeight(panel.dp(48)); cell.setPadding(panel.dp(8),panel.dp(6),panel.dp(8),panel.dp(6));
                // Reuse the panel's focused/pressed background without nested clickable buttons.
                cell.setBackground(panel.button("",()->{}).getBackground());
                cell.setFocusable(true); cell.setClickable(true);
                cell.setOnClickListener(v->{
                    if(code==0){captureMapping();mCaptureScreenshot=true;mCaptureHint.setText(R.string.map_capture_screenshot);}
                    else selectMapping(code);
                });
                cell.setContentDescription(name+" → "+target+(key<0?", "+mActivity.getString(R.string.map_action):""));
                TextView badge=panel.label(name,12);
                badge.setGravity(android.view.Gravity.CENTER); badge.setPadding(0,0,0,0);
                badge.setMaxLines(1); badge.setIncludeFontPadding(false);
                android.graphics.drawable.GradientDrawable outline=new android.graphics.drawable.GradientDrawable();
                outline.setColor(android.graphics.Color.TRANSPARENT);
                outline.setCornerRadius(panel.dp(name.length()==1 || name.equals("L3") || name.equals("R3")?16:6));
                int color=mActivity.getColor(R.color.rmxp_niebla);
                if (name.equals("A")) color=0xFF66BB6A;
                else if (name.equals("B")) color=0xFFEF5350;
                else if (name.equals("X")) color=0xFF42A5F5;
                else if (name.equals("Y")) color=0xFFFFCA28;
                outline.setStroke(panel.dp(1),color); badge.setTextColor(color); badge.setBackground(outline);
                cell.addView(badge,new LinearLayout.LayoutParams(panel.dp(name.length()>4?62:32),panel.dp(32)));
                TextView arrow=panel.label("→",12); arrow.setGravity(android.view.Gravity.CENTER); arrow.setPadding(0,0,0,0);
                cell.addView(arrow,new LinearLayout.LayoutParams(panel.dp(18),-2));
                TextView value=panel.label(target,12); value.setGravity(android.view.Gravity.CENTER);
                value.setPadding(panel.dp(2),panel.dp(4),panel.dp(2),panel.dp(4));
                value.setMaxLines(3);
                cell.addView(value,new LinearLayout.LayoutParams(0,-2,1)); row.addView(cell,lp);
                if(code==mLastMapped) cell.post(()->{
                    cell.requestFocus();cell.requestRectangleOnScreen(new android.graphics.Rect(0,0,cell.getWidth(),cell.getHeight()),false);
                });
            }
            box.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
    }

    private void autoMap(PanelLateral panel)
    {
        new AlertDialog.Builder(mActivity).setMessage(R.string.map_auto_confirm)
            .setNegativeButton(android.R.string.cancel,null)
            .setPositiveButton(android.R.string.ok,(d,w)->{
                pad.releaseAll(); controls.releasePressed();
                LinearLayout body=Apariencia.column(mActivity);
                TextView title=panel.label(mActivity.getString(R.string.map_auto),18);
                title.setGravity(android.view.Gravity.CENTER); body.addView(title);
                View diagram=new View(mActivity) {
                    final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                    @Override protected void onDraw(android.graphics.Canvas canvas) {
                        float cx=getWidth()/2f,cy=getHeight()/2f,offset=panel.dp(30),radius=panel.dp(19);
                        String[] labels=pad.profile()==PadMapper.SWITCH?new String[]{"X","Y","A","B"}:new String[]{"Y","X","B","A"};
                        float[] dx={0,-offset,offset,0},dy={-offset,0,0,offset};
                        for(int i=0;i<4;i++) {
                            paint.setColor(mActivity.getColor(i==2?R.color.rmxp_ambar:R.color.rmxp_musgo));
                            paint.setStyle(android.graphics.Paint.Style.FILL);canvas.drawCircle(cx+dx[i],cy+dy[i],radius,paint);
                            paint.setColor(mActivity.getColor(i==2?R.color.rmxp_tinta:R.color.rmxp_niebla));
                            paint.setTextSize(panel.dp(17));paint.setTextAlign(android.graphics.Paint.Align.CENTER);
                            canvas.drawText(labels[i],cx+dx[i],cy+dy[i]-(paint.ascent()+paint.descent())/2,paint);
                        }
                    }
                };
                diagram.setContentDescription(mActivity.getString(R.string.map_auto_prompt));
                body.addView(diagram,new LinearLayout.LayoutParams(-1,panel.dp(116)));
                TextView prompt=panel.label(mActivity.getString(R.string.map_auto_prompt),15);
                prompt.setGravity(android.view.Gravity.CENTER);body.addView(prompt);
                TextView hint=panel.label(mActivity.getString(R.string.map_auto_hint),12);
                hint.setGravity(android.view.Gravity.CENTER);body.addView(hint);
                Button cancel=panel.button(mActivity.getString(android.R.string.cancel),this::dismissAutoMap);
                body.addView(cancel);
                mAutoMapSheet=panel.showSheet(body);
                mAutoMapSheet.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
                    public void onViewAttachedToWindow(View v){}
                    public void onViewDetachedFromWindow(View v){if(mAutoMapSheet==v)mAutoMapSheet=null;}
                });
                cancel.requestFocus();
            }).show();
    }

    private void dismissAutoMap()
    {
        View sheet=mAutoMapSheet; mAutoMapSheet=null;
        if(sheet!=null && sheet.getParent()!=null)((ViewGroup)sheet.getParent()).removeView(sheet);
    }

    private void selectMapping(int source)
    {
        mLastMapped = source;
        TouchControls.pickKey(mActivity, true, pad.name(source), key -> {
            pad.setKey(source, key); controls.rebuild(); showInputMapping();
        });
    }

    private void captureMapping()
    {
        pad.releaseAll(); controls.releasePressed();
        PanelLateral panel = new PanelLateral(mActivity, R.string.map_capture);
        mCaptureHint = panel.label(mActivity.getString(R.string.map_capture_hint), 16);
        panel.content.addView(mCaptureHint);
        panel.content.addView(panel.button(mActivity.getString(android.R.string.cancel), () -> { panel.dismiss(); showInputMapping(); }));
        mCaptureSource = 0; mCaptureAxis = false; mCapturing = true; mCaptureScreenshot=false;
        panel.content.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) {}
            public void onViewDetachedFromWindow(View v) { mCapturing = false; mCaptureSource = 0; }
        });
    }

    public boolean captureKey(KeyEvent event)
    {
        if (mAutoMapSheet != null) {
            if (event.getKeyCode()==KeyEvent.KEYCODE_BACK) {
                if(event.getAction()==KeyEvent.ACTION_UP)dismissAutoMap();
                return true;
            }
            if (!PadMapper.isGamepad(event.getDevice())) return true;
            int code=event.getKeyCode();
            if(event.getAction()==KeyEvent.ACTION_UP &&
                    (code==KeyEvent.KEYCODE_BUTTON_A || code==KeyEvent.KEYCODE_BUTTON_B)) {
                pad.connected(event.getDevice());
                pad.selectProfile(code==KeyEvent.KEYCODE_BUTTON_A?PadMapper.SWITCH:PadMapper.XBOX);
                pad.reset(); dismissAutoMap(); controls.rebuild(); showInputMapping();
            }
            return true;
        }
        if (!mCapturing) return false;
        int source = pad.sourceForKey(event);
        if(mCaptureScreenshot && event.getKeyCode()==KeyEvent.KEYCODE_BACK && !PadMapper.isGamepad(event.getDevice())) return false;
        if (mCaptureScreenshot && (source>=PadMapper.KEYBOARD || !PadMapper.isGamepad(event.getDevice()) ||
            source==KeyEvent.KEYCODE_BUTTON_A || source==KeyEvent.KEYCODE_BUTTON_B ||
            source==KeyEvent.KEYCODE_BUTTON_X || source==KeyEvent.KEYCODE_BUTTON_Y ||
            source==KeyEvent.KEYCODE_BUTTON_MODE || source==KeyEvent.KEYCODE_BACK ||
            (source>=KeyEvent.KEYCODE_BUTTON_L1 && source<=KeyEvent.KEYCODE_BUTTON_SELECT) ||
            (source>=KeyEvent.KEYCODE_DPAD_UP && source<=KeyEvent.KEYCODE_DPAD_RIGHT))) return true;
        if (!PadMapper.validSource(source)) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0 && mCaptureSource == 0) {
            mCaptureSource = source;
            mCaptureHint.setText(pad.name(source) + "\n" + mActivity.getString(R.string.map_release));
        } else if (event.getAction() == KeyEvent.ACTION_UP && source == mCaptureSource && !mCaptureAxis) {
            captured(source);
        }
        return true;
    }

    void captured(int source)
    {
        if (!mCapturing || !PadMapper.validSource(source)) return;
        mCapturing = false; mCaptureSource = 0;
        if(mCaptureScreenshot){pad.setCaptureButton(source);mCaptureScreenshot=false;}
        mMappingTab = source >= PadMapper.KEYBOARD ? 2 : source >= 1000 ? 1 : 0;
        selectMapping(source);
    }

    public KeyEvent navigationKey(KeyEvent event)
    {
        if (!PadMapper.isGamepad(event.getDevice())) return event;
        int confirm = pad.profile() == PadMapper.SWITCH ? KeyEvent.KEYCODE_BUTTON_B : KeyEvent.KEYCODE_BUTTON_A;
        if (event.getKeyCode() != confirm) return event;
        return new KeyEvent(event.getDownTime(), event.getEventTime(), event.getAction(),
            KeyEvent.KEYCODE_DPAD_CENTER, event.getRepeatCount(), event.getMetaState(),
            event.getDeviceId(), event.getScanCode(), event.getFlags(), event.getSource());
    }

    public void menuMotion(MotionEvent event)
    {
        if (mAutoMapSheet != null) return;
        int source = pad.sourceForMotion(event);
        if (mCapturing) {
            if (mCaptureScreenshot) return;
            if (source != 0 && mCaptureSource == 0) {
                mCaptureSource = source; mCaptureAxis = true;
                mCaptureHint.setText(pad.name(source) + "\n" + mActivity.getString(R.string.map_release));
            } else if (source == 0 && mCaptureAxis && mCaptureSource != 0) captured(mCaptureSource);
            return;
        }
        int direction = 0;
        switch (source) {
            case PadMapper.LS_UP: case PadMapper.RS_UP: case KeyEvent.KEYCODE_DPAD_UP: direction = KeyEvent.KEYCODE_DPAD_UP; break;
            case PadMapper.LS_DOWN: case PadMapper.RS_DOWN: case KeyEvent.KEYCODE_DPAD_DOWN: direction = KeyEvent.KEYCODE_DPAD_DOWN; break;
            case PadMapper.LS_LEFT: case PadMapper.RS_LEFT: case KeyEvent.KEYCODE_DPAD_LEFT: direction = KeyEvent.KEYCODE_DPAD_LEFT; break;
            case PadMapper.LS_RIGHT: case PadMapper.RS_RIGHT: case KeyEvent.KEYCODE_DPAD_RIGHT: direction = KeyEvent.KEYCODE_DPAD_RIGHT; break;
        }
        long now = SystemClock.uptimeMillis();
        if (direction != 0 && (direction != mNavigationDirection || now - mNavigationTime > 220)) {
            mActivity.getWindow().superDispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, direction));
            mActivity.getWindow().superDispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, direction));
            mNavigationTime = now;
        }
        mNavigationDirection = direction;
    }

    private void showInputMapping()
    {
        PanelLateral panel = new PanelLateral(mActivity, R.string.rmxp_input);
        android.widget.Spinner profile = new android.widget.Spinner(mActivity);
        profile.setContentDescription(mActivity.getString(R.string.map_layout));
        profile.setAdapter(new android.widget.ArrayAdapter<String>(mActivity,
            android.R.layout.simple_spinner_dropdown_item,new String[]{"Xbox","Switch"}));
        profile.setSelection(pad.profile() == PadMapper.SWITCH ? 1 : 0);
        profile.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int index,long id) {
                if (view instanceof TextView) ((TextView)view).setTextColor(mActivity.getColor(R.color.rmxp_niebla));
                int selected = index == 0 ? PadMapper.XBOX : PadMapper.SWITCH;
                if (selected != pad.profile()) { pad.selectProfile(selected); controls.rebuild(); showInputMapping(); }
            }
        });
        // Only the title is pinned: controls can scroll on short screens or large font sizes.
        panel.content.addView(profile,new LinearLayout.LayoutParams(-1,panel.dp(48)));
        panel.content.addView(panel.button(mActivity.getString(R.string.map_auto),()->autoMap(panel)));
        LinearLayout tabs=new LinearLayout(mActivity); tabs.setBaselineAligned(false);tabs.setMinimumHeight(panel.dp(48));
        String[] labels={mActivity.getString(R.string.rmxp_pad_buttons),mActivity.getString(R.string.map_axes),mActivity.getString(R.string.map_keyboard)};
        for(int i=0;i<labels.length;i++) {
            final int tab=i;
            Button button=panel.button(labels[i],()->{mMappingTab=tab;mLastMapped=-1;showInputMapping();});
            button.setTextSize(12);button.setMaxLines(2);button.setPadding(panel.dp(5),panel.dp(8),panel.dp(5),panel.dp(8));
            button.setMinimumHeight(panel.dp(48));button.setSelected(i==mMappingTab);
            if(i==mMappingTab) {
                button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(mActivity.getColor(R.color.rmxp_ambar)));
                button.setTextColor(mActivity.getColor(R.color.rmxp_tinta));
            }
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
            lp.setMargins(i==0?0:panel.dp(4),panel.dp(6),i==2?0:panel.dp(4),panel.dp(6));
            tabs.addView(button,lp);
        }
        panel.content.addView(tabs);
        panel.content.addView(panel.label(mActivity.getString(R.string.map_grid_hint),12));
        java.util.List<Integer> sources = pad.sources(mMappingTab == 2);
        if (mMappingTab == 2) {
            if (sources.isEmpty()) panel.content.addView(panel.label(mActivity.getString(R.string.map_keyboard_empty), 14));
            mappingRows(panel, panel.content, sources);
        } else if (mMappingTab == 1) {
            int[][] groups = {{KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT},
                {PadMapper.LS_UP, PadMapper.LS_DOWN, PadMapper.LS_LEFT, PadMapper.LS_RIGHT},
                {PadMapper.RS_UP, PadMapper.RS_DOWN, PadMapper.RS_LEFT, PadMapper.RS_RIGHT}};
            int[] titles = {R.string.rmxp_dpad, R.string.rmxp_left_stick, R.string.rmxp_right_stick};
            for (int i = 0; i < groups.length; i++) {
                LinearLayout body = Apariencia.card(panel, titles[i], i == 0 || java.util.Arrays.stream(groups[i]).anyMatch(n -> n == mLastMapped));
                java.util.List<Integer> group = new java.util.ArrayList<>();
                for (int source : groups[i]) group.add(source);
                mappingRows(panel, body, group);
            }
            java.util.List<Integer> extraAxes = new java.util.ArrayList<>();
            for (int code : sources) if (code >= 1100 && code < 1228) extraAxes.add(code);
            if (!extraAxes.isEmpty()) mappingRows(panel, Apariencia.card(panel, R.string.map_extra_axes, true), extraAxes);
        } else {
            boolean nintendo=pad.profile()==PadMapper.SWITCH;
            java.util.List<Integer> common = java.util.Arrays.asList(
                nintendo?KeyEvent.KEYCODE_BUTTON_B:KeyEvent.KEYCODE_BUTTON_A,nintendo?KeyEvent.KEYCODE_BUTTON_A:KeyEvent.KEYCODE_BUTTON_B,
                nintendo?KeyEvent.KEYCODE_BUTTON_Y:KeyEvent.KEYCODE_BUTTON_X,nintendo?KeyEvent.KEYCODE_BUTTON_X:KeyEvent.KEYCODE_BUTTON_Y,
                KeyEvent.KEYCODE_BUTTON_L1,KeyEvent.KEYCODE_BUTTON_R1,
                KeyEvent.KEYCODE_BUTTON_L2,KeyEvent.KEYCODE_BUTTON_R2,KeyEvent.KEYCODE_BUTTON_START,KeyEvent.KEYCODE_BUTTON_SELECT,
                KeyEvent.KEYCODE_BUTTON_THUMBL,KeyEvent.KEYCODE_BUTTON_THUMBR);
            mappingRows(panel, panel.content, common);
            mappingRows(panel,panel.content,java.util.Arrays.asList(KeyEvent.KEYCODE_BUTTON_MODE));
            mappingRows(panel,panel.content,pad.profile()==PadMapper.SWITCH?
                java.util.Arrays.asList(KeyEvent.KEYCODE_BACK,pad.captureButton()):java.util.Arrays.asList(KeyEvent.KEYCODE_BACK));
            panel.content.addView(panel.label(mActivity.getString(R.string.map_reserved_hint),12));
            java.util.List<Integer> extra = new java.util.ArrayList<>();
            for (int code : sources) if (!common.contains(code) && code != KeyEvent.KEYCODE_BUTTON_MODE && code != KeyEvent.KEYCODE_BACK && code != pad.captureButton() && code < 1000 &&
                (code < KeyEvent.KEYCODE_DPAD_UP || code > KeyEvent.KEYCODE_DPAD_RIGHT) && pad.available(code)) extra.add(code);
            if(!extra.isEmpty()) mappingRows(panel,panel.content,extra);
        }
        panel.content.addView(panel.button(mActivity.getString(mMappingTab==2?R.string.map_add:R.string.map_detect_input),this::captureMapping));
        panel.content.addView(panel.button(mActivity.getString(R.string.rmxp_edit_reset), () -> {
            new AlertDialog.Builder(mActivity).setMessage(mMappingTab == 2 ? R.string.map_reset_keyboard : R.string.map_reset_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.rmxp_edit_reset, (d,w) -> {
                    if (mMappingTab == 2) pad.resetKeyboard(); else pad.reset();
                    controls.rebuild(); showInputMapping();
                }).show();
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
                lp.preferredRefreshRate = best.getRefreshRate();
                mActivity.getWindow().setAttributes(lp);
                mHz = Math.round(best.getRefreshRate());
            }
        }
        // "Igual a la pantalla": el juego corre a tantos fps como Hz tenga la pantalla
        int target = fpsTarget(pref("fps", 0), mHz);
        MainActivity.nativeSetFps(target);
        android.view.Surface surface = SDLActivity.getNativeSurface();
        if (surface != null && surface.isValid()) {
            if (Build.VERSION.SDK_INT >= 31)
                surface.setFrameRate(mHz, android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                    android.view.Surface.CHANGE_FRAME_RATE_ALWAYS);
            else if (Build.VERSION.SDK_INT >= 30)
                surface.setFrameRate(mHz, android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
        }
        if (Build.VERSION.SDK_INT >= 35) {
            try {
                View.class.getMethod("setRequestedFrameRate", float.class).invoke(mActivity.getWindow().getDecorView(), (float)mHz);
            } catch (ReflectiveOperationException e) {
                android.util.Log.w("RMXP-Pantalla", "View frame-rate request unavailable", e);
            }
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
        pad.releaseAll();
        applySpeed();
        mPanel.setTranslationX(mPanel.getWidth() > 0 ? mPanel.getWidth() : 1000);
        mPanel.animate().translationX(0).setDuration(180).start();
        mRoot.findViewById(R.id.rmxp_resume).requestFocus();
    }

    public void close()
    {
        if (!isOpen())
            return;
        PanelLateral.dismissOpen(mActivity);
        mPanel.animate().cancel();
        mPanel.setTranslationX(0);
        mRoot.setVisibility(View.GONE);
        applySpeed(); // quita la pausa
    }

    /** Al salir a otra app el juego se pausa (no dibuja sin pantalla); al volver sigue. */
    public void onBackground(boolean background)
    {
        mBackground = background;
        if (background) {
            controls.releasePressed(); pad.releaseAll();
        }
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
