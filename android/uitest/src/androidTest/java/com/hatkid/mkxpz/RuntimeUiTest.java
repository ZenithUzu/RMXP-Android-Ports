package com.hatkid.mkxpz;

import android.view.*;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

public class RuntimeUiTest {
    private UiActivity activity;
    private android.app.Instrumentation instrumentation;
    private void ui(Runnable action) { instrumentation.runOnMainSync(action); instrumentation.waitForIdleSync(); }
    private View text(View view, String value) {
        if (view instanceof TextView && ((TextView)view).getText().toString().equals(value)) return view;
        if (view instanceof ViewGroup) for (int i=((ViewGroup)view).getChildCount()-1;i>=0;i--) {
            View found=text(((ViewGroup)view).getChildAt(i),value); if(found!=null)return found;
        }
        return null;
    }
    private void click(int string) {
        View v=text(activity.getWindow().getDecorView(),activity.getString(string));
        assertNotNull(v); v.performClick();
    }
    private void screenshot(String name) throws Exception {
        instrumentation.waitForIdleSync();
        android.os.SystemClock.sleep(250); // Wait for RenderThread, not only the UI message queue.
        android.graphics.Bitmap b=instrumentation.getUiAutomation().takeScreenshot();
        android.content.ContentValues values=new android.content.ContentValues();
        values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,name+".png");
        values.put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");
        values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/rmxp-ui");
        android.net.Uri uri=activity.getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
        try(java.io.OutputStream out=activity.getContentResolver().openOutputStream(uri)) {
            b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);
        }
    }
    @Test public void menuEditorKeyboardAndFps() throws Exception {
        instrumentation=InstrumentationRegistry.getInstrumentation();
        activity=(UiActivity)instrumentation.startActivitySync(new android.content.Intent(
            instrumentation.getTargetContext(),UiActivity.class).putExtra("clear",true).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        ui(() -> {
            activity.menu.open();
            Switch pause=activity.findViewById(R.id.rmxp_keep_running);
            assertTrue(pause.isShown());pause.setChecked(true);assertEquals(0,MainActivity.speed);
            RadioGroup fps=activity.findViewById(R.id.rmxp_fps);
            fps.check(fps.getChildAt(3).getId());assertEquals(120,MainActivity.fps);
            assertEquals(120,OptionsMenu.fpsTarget(3,60));
            assertEquals(60,OptionsMenu.fpsTarget(2,120));
        });
        screenshot("01-menu");
        ui(() -> {
            RadioGroup sections=activity.findViewById(R.id.rmxp_sections);sections.check(sections.getChildAt(1).getId());
            activity.findViewById(R.id.rmxp_pad_edit).performClick();
            assertTrue(activity.menu.controls.isEditing());assertEquals(0,MainActivity.speed);
            ViewGroup controls=activity.findViewById(android.R.id.content).findViewWithTag("rmxp_touch_controls");
            View control=controls.getChildAt(0);
            long now=android.os.SystemClock.uptimeMillis();
            control.onTouchEvent(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,40,40,0));
            control.onTouchEvent(MotionEvent.obtain(now,now,MotionEvent.ACTION_UP,40,40,0));
        });
        ui(() -> {
            activity.menu.controls.rebuild();
        });
        ui(() -> {
            ViewGroup controls=activity.findViewById(android.R.id.content).findViewWithTag("rmxp_touch_controls");
            View dock=controls.findViewWithTag("rmxp_selected_control");
            assertNotNull(dock);assertTrue(dock.getX()>0);assertTrue(dock.getY()>0);
            assertTrue(dock.getX()+dock.getWidth()<=controls.getWidth());
        });
        screenshot("02-editor");
        ui(() -> { click(R.string.rmxp_edit_more); assertNotNull(activity.findViewById(android.R.id.content).findViewWithTag("rmxp_side_panel")); });
        screenshot("03-editor-settings");
        ui(() -> { click(R.string.rmxp_edit_done); click(R.string.rmxp_edit_done); activity.menu.teclado.show();
            assertTrue(activity.menu.teclado.shown());
            View controls=activity.findViewById(android.R.id.content).findViewWithTag("rmxp_touch_controls");
            assertEquals(View.GONE,controls.getVisibility());
        });
        screenshot("04-keyboard");
        ui(() -> {
            View functions=text(activity.getWindow().getDecorView(),"F1–F12"); assertNotNull(functions);functions.performClick();
            assertNotNull(text(activity.getWindow().getDecorView(),"F12"));
            assertNotNull(text(activity.getWindow().getDecorView(),"↑"));
            View ctrl=text(activity.getWindow().getDecorView(),"Ctrl");
            long now=android.os.SystemClock.uptimeMillis();
            ctrl.dispatchTouchEvent(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,5,5,0));
            ctrl.dispatchTouchEvent(MotionEvent.obtain(now,now,MotionEvent.ACTION_UP,5,5,0));
            assertTrue(org.libsdl.app.SDLActivity.held.contains(KeyEvent.KEYCODE_CTRL_LEFT));
            assertTrue(activity.menu.teclado.onOutsideTouch(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,1,1,0)));
            assertTrue(activity.menu.teclado.onOutsideTouch(MotionEvent.obtain(now,now,MotionEvent.ACTION_UP,1,1,0)));
            assertFalse(activity.menu.teclado.shown());
            assertTrue(org.libsdl.app.SDLActivity.held.isEmpty());
            View controls=activity.findViewById(android.R.id.content).findViewWithTag("rmxp_touch_controls");
            assertEquals(View.VISIBLE,controls.getVisibility());
            activity.menu.open();
            RadioGroup sections=activity.findViewById(R.id.rmxp_sections);sections.check(sections.getChildAt(1).getId());
            activity.findViewById(R.id.rmxp_input).performClick();
        });
        screenshot("05-controller");
        ui(() -> {
            assertTrue(PanelLateral.dismissOpen(activity));
            assertTrue(activity.menu.isOpen());
            assertEquals(0,MainActivity.speed);
        });
        ui(() -> { assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,
            activity.checkSelfPermission(android.Manifest.permission.VIBRATE));activity.finish(); });
    }
    @Test public void performancePanelPersistsAppearanceAndMoves() throws Exception {
        instrumentation=InstrumentationRegistry.getInstrumentation();
        activity=(UiActivity)instrumentation.startActivitySync(new android.content.Intent(
            instrumentation.getTargetContext(),UiActivity.class).putExtra("clear",true).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        ui(()->{
            activity.menu.open();activity.findViewById(R.id.rmxp_meter).performClick();
            assertNotNull(activity.findViewById(android.R.id.content).findViewWithTag("rmxp_side_panel"));
            Switch fps=(Switch)text(activity.getWindow().getDecorView(),activity.getString(R.string.rmxp_meter_fps));
            fps.setChecked(true);
            ViewGroup panel=activity.findViewById(android.R.id.content).findViewWithTag("rmxp_side_panel");
            SeekBar opacity=(SeekBar)description(panel,activity.getString(R.string.ap_background_opacity));
            opacity.setKeyProgressIncrement(1);opacity.setProgress(36);
            opacity.onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT,new KeyEvent(0,KeyEvent.KEYCODE_DPAD_LEFT));
            assertEquals(35,activity.getSharedPreferences("rmxp_opciones",0).getInt("med_background",-1));
            Spinner position=(Spinner)description(panel,activity.getString(R.string.ap_position));
            position.setSelection(3);
        });
        screenshot("06-performance-panel");
        ui(()->{
            assertEquals(3,activity.getSharedPreferences("rmxp_opciones",0).getInt("med_anchor",-1));
            click(R.string.ap_move_meter);
            assertFalse(activity.menu.isOpen());
            assertNull(activity.findViewById(android.R.id.content).findViewWithTag("rmxp_side_panel"));
            activity.finish();
        });
    }
    private View description(View v,String value) {
        if(v==null)return null;
        if(value.equals(v.getContentDescription()))return v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++) {
            View found=description(((ViewGroup)v).getChildAt(i),value);if(found!=null)return found;
        }
        return null;
    }

    @Test public void bindExtraButtonAndKeyboardShortcutThroughSearch() throws Exception {
        instrumentation=InstrumentationRegistry.getInstrumentation();
        activity=(UiActivity)instrumentation.startActivitySync(new android.content.Intent(
            instrumentation.getTargetContext(),UiActivity.class).putExtra("clear",true).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        ui(()->{activity.menu.open();activity.findViewById(R.id.rmxp_input).performClick();click(R.string.map_add);});
        ui(()->activity.menu.captured(KeyEvent.KEYCODE_BUTTON_16));
        ui(()->{
            View turbo=description(activity.findViewById(android.R.id.content).findViewWithTag("rmxp_side_panel"),com.hatkid.mkxpz.controles.Teclas.nombre(com.hatkid.mkxpz.controles.Control.TURBO));
            assertNotNull(turbo);assertTrue(turbo instanceof Button);turbo.performClick();
            assertEquals(com.hatkid.mkxpz.controles.Control.TURBO,activity.menu.pad.keyFor(KeyEvent.KEYCODE_BUTTON_16));
        });
        screenshot("07-controller-binding");
        ui(()->click(R.string.map_add));
        ui(()->activity.menu.captured(com.hatkid.mkxpz.controles.PadMapper.KEYBOARD+KeyEvent.KEYCODE_F11));
        ui(()->{
            EditText search=(EditText)description(activity.findViewById(android.R.id.content).findViewWithTag("rmxp_side_panel"),activity.getString(R.string.map_search));
            search.setText("F12");
            View f12=description(activity.findViewById(android.R.id.content).findViewWithTag("rmxp_side_panel"),"F12");assertNotNull(f12);f12.performClick();
            assertEquals(KeyEvent.KEYCODE_F12,activity.menu.pad.keyFor(com.hatkid.mkxpz.controles.PadMapper.KEYBOARD+KeyEvent.KEYCODE_F11));
        });
        screenshot("08-keyboard-binding");
        ui(()->{
            click(R.string.map_add);assertTrue(PanelLateral.dismissOpen(activity));
            activity.menu.captured(KeyEvent.KEYCODE_BUTTON_A);
            assertNull(description(activity.findViewById(android.R.id.content).findViewWithTag("rmxp_side_panel"),activity.getString(R.string.map_search)));
            activity.finish();
        });
    }

    @Test public void appearanceUsesRealPreviewAndAlignedTabs() throws Exception {
        instrumentation=InstrumentationRegistry.getInstrumentation();
        activity=(UiActivity)instrumentation.startActivitySync(new android.content.Intent(
            instrumentation.getTargetContext(),UiActivity.class).putExtra("clear",true).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        ui(()->{activity.menu.open(); activity.findViewById(R.id.rmxp_input).performClick();});
        ui(()->{
            View a=text(activity.getWindow().getDecorView(),activity.getString(R.string.rmxp_pad_buttons));
            View b=text(activity.getWindow().getDecorView(),activity.getString(R.string.map_axes));
            View c=text(activity.getWindow().getDecorView(),activity.getString(R.string.map_keyboard));
            assertEquals(a.getTop(),b.getTop()); assertEquals(b.getTop(),c.getTop());
            assertEquals(a.getHeight(),b.getHeight()); assertEquals(b.getHeight(),c.getHeight());
            PanelLateral.dismissOpen(activity);
            RadioGroup fps=activity.findViewById(R.id.rmxp_fps); fps.check(fps.getChildAt(3).getId());
            activity.menu.refreshDisplay(); assertEquals(120,MainActivity.fps);
            activity.menu.controls.startEditing(()->{}); click(R.string.rmxp_edit_more); click(R.string.ap_color_opacity);
        });
        screenshot("09-appearance");
        ui(()->{
            ViewGroup panel=activity.findViewById(android.R.id.content).findViewWithTag("rmxp_side_panel");
            panel.findViewWithTag("rmxp_color_picker").performClick();
        });
        screenshot("10-color-picker");
        ui(()->{
            View panel=activity.findViewById(android.R.id.content).findViewWithTag("rmxp_side_panel");
            SeekBar opacity=(SeekBar)description(panel,activity.getString(R.string.ap_button_opacity));
            opacity.setKeyProgressIncrement(1); opacity.setProgress(38);
            opacity.onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT,new KeyEvent(0,KeyEvent.KEYCODE_DPAD_LEFT));
            assertEquals(37,activity.getSharedPreferences("rmxp_opciones",0).getInt("opac_global",-1));
            ViewGroup root=(ViewGroup)panel;
            View color=description(root,activity.getString(R.string.ap_hex)); assertNotNull(color);
            ((EditText)color).setText("#12ABEF");
        });
        ui(()->activity.finish());
    }

}
