package com.hatkid.mkxpz.controles;
import android.view.*;
import android.content.SharedPreferences;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class PadMapperTest {
 @Test public void profilesAxesHysteresisAndSharedKeys(){
  SharedPreferences prefs=InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("pad-test",0);prefs.edit().clear().commit();
  List<String> events=new ArrayList<>();
  TouchControls.Actions sink=new TouchControls.Actions(){
   public void keyDown(int k){events.add("d"+k);}public void keyUp(int k){events.add("u"+k);}
   public void action(int k){events.add("a"+k);}public boolean turboOn(){return false;}
   public void editingChanged(boolean e){}public void controllerConnected(InputDevice d){}
  };
  PadMapper p=new PadMapper(prefs,sink);
  assertEquals(KeyEvent.KEYCODE_ENTER,p.keyFor(KeyEvent.KEYCODE_BUTTON_A));
  p.selectProfile(PadMapper.SWITCH);
  assertEquals("A",p.name(KeyEvent.KEYCODE_BUTTON_B));
  assertEquals(KeyEvent.KEYCODE_ENTER,p.keyFor(KeyEvent.KEYCODE_BUTTON_B));
  assertEquals(KeyEvent.KEYCODE_X,p.keyFor(KeyEvent.KEYCODE_BUTTON_A));
  assertEquals(KeyEvent.KEYCODE_ENTER,p.keyForScreen(KeyEvent.KEYCODE_BUTTON_A));
  p.setKey(KeyEvent.KEYCODE_BUTTON_B,KeyEvent.KEYCODE_C);
  assertEquals(KeyEvent.KEYCODE_C,p.keyForScreen(KeyEvent.KEYCODE_BUTTON_A));
  p.selectProfile(PadMapper.XBOX);assertEquals(KeyEvent.KEYCODE_ENTER,p.keyFor(KeyEvent.KEYCODE_BUTTON_A));
  // Left stick and hat hold the same arrow: releasing one must not release the other.
  p.updateAxes(1,-1,0,-1,1,0,0,0);
  assertEquals(3,events.size());assertTrue(events.contains("d"+KeyEvent.KEYCODE_A));
  p.updateAxes(0,0,0,0,1,0,0,0);assertFalse(events.contains("u"+KeyEvent.KEYCODE_DPAD_RIGHT));
  p.updateAxes(0,0,0,0,0,0,0,0);assertTrue(events.contains("u"+KeyEvent.KEYCODE_DPAD_RIGHT));
  events.clear();p.updateAxes(.6f,0,0,0,0,0,0,0);p.updateAxes(.4f,0,0,0,0,0,0,0);assertEquals(1,events.size());
  p.updateAxes(.3f,0,0,0,0,0,0,0);assertEquals(2,events.size());
  // Actions fire once while an analog trigger remains held, then rearm after release.
  p.setKey(KeyEvent.KEYCODE_BUTTON_R2,Control.TURBO);events.clear();
  p.updateAxes(0,0,0,0,0,0,0,1);p.updateAxes(0,0,0,0,0,0,0,.8f);assertEquals(Arrays.asList("a"+Control.TURBO),events);
  p.releaseAll();p.updateAxes(0,0,0,0,0,0,0,1);assertEquals(2,events.size());p.releaseAll();
  p.selectProfile(PadMapper.SWITCH);assertEquals(KeyEvent.KEYCODE_C,p.keyFor(KeyEvent.KEYCODE_BUTTON_B));
  assertEquals(PadMapper.SWITCH,new PadMapper(prefs,sink).profile());
  prefs.edit().putInt("mando_"+KeyEvent.KEYCODE_BUTTON_A,KeyEvent.KEYCODE_F8).commit();
  p.reset();p.selectProfile(PadMapper.XBOX);
  assertEquals(KeyEvent.KEYCODE_F8,p.keyFor(KeyEvent.KEYCODE_BUTTON_A));
 }
}
