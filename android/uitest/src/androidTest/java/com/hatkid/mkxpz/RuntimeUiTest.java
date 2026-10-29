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
            instrumentation.getTargetContext(),UiActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
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
        ui(() -> { assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,
            activity.checkSelfPermission(android.Manifest.permission.VIBRATE));activity.finish(); });
    }
}
