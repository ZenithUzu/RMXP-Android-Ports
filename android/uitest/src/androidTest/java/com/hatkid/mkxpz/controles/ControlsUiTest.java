package com.hatkid.mkxpz.controles;
import android.view.*;
import android.widget.*;
import android.graphics.*;
import com.hatkid.mkxpz.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
public class ControlsUiTest {
 private View find(View v,String s){
  if(v instanceof TextView && ((TextView)v).getText().toString().equals(s))return v;
  if(s.equals(v.getContentDescription()))return v;
  if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){
   View f=find(((ViewGroup)v).getChildAt(i),s);if(f!=null)return f;
  }return null;
 }
 private void tap(View v){
  assertTrue("Chip must accept touch input",v.isClickable());
  long t=android.os.SystemClock.uptimeMillis();float x=v.getWidth()/2f,y=v.getHeight()/2f;
  v.dispatchTouchEvent(MotionEvent.obtain(t,t,0,x,y,0));v.dispatchTouchEvent(MotionEvent.obtain(t,t+40,1,x,y,0));
 }
 private Bitmap render(View v){Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));return b;}
 @Test public void customizationChangesGeometryPixelsAndSurvivesReload(){
  android.app.Instrumentation ins=InstrumentationRegistry.getInstrumentation();
  UiActivity a=(UiActivity)ins.startActivitySync(new android.content.Intent(ins.getTargetContext(),UiActivity.class).putExtra("clear",true).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
  ins.waitForIdleSync();
  final ControlView[] target=new ControlView[1];final int[] before=new int[1];final Bitmap[] image=new Bitmap[1];
  ins.runOnMainSync(()->{
   a.getSharedPreferences("rmxp_opciones",0).edit().putBoolean("tam_fijo",false).putBoolean("opac_fija",false).commit();
   a.menu.open();RadioGroup sections=a.findViewById(R.id.rmxp_sections);sections.check(sections.getChildAt(1).getId());
   a.findViewById(R.id.rmxp_pad_edit).performClick();
  });
  ins.waitForIdleSync();
  ins.runOnMainSync(()->{
   ViewGroup layer=a.findViewById(android.R.id.content).findViewWithTag("rmxp_touch_controls");
   ControlView c=(ControlView)layer.getChildAt(0);target[0]=c;before[0]=c.getLayoutParams().width;image[0]=render(c);
   long t=android.os.SystemClock.uptimeMillis();
   for(int i=0;i<2;i++){c.onTouchEvent(MotionEvent.obtain(t,t,0,20,20,0));c.onTouchEvent(MotionEvent.obtain(t,t,1,20,20,0));}
  });
  ins.waitForIdleSync();
  ins.runOnMainSync(()->{
   ControlView c=target[0];
   View decor=a.getWindow().getDecorView();
   View large=find(decor,a.getString(R.string.rmxp_size_l));assertNotNull(large);tap(large);
   assertEquals(2,c.control.tam);assertTrue(c.getLayoutParams().width>before[0]);
   View opacity=find(decor,"25 %");assertNotNull(opacity);tap(opacity);
   assertEquals(.25f,c.bodyAlpha,.001f);
   View red=find(decor,"#E5394F");assertNotNull(red);red.performClick();assertEquals(0xffe5394f,c.control.color);
  });
  ins.waitForIdleSync();
  ins.runOnMainSync(()->{
   assertFalse(image[0].sameAs(render(target[0])));
   a.menu.controls.recargar();
   ViewGroup layer=a.findViewById(android.R.id.content).findViewWithTag("rmxp_touch_controls");ControlView c=(ControlView)layer.getChildAt(0);
   assertEquals(2,c.control.tam);assertEquals(.25f,c.bodyAlpha,.001f);assertEquals(0xffe5394f,c.control.color);
   a.finish();
  });
 }
}
