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
  });
  ins.waitForIdleSync();
  ins.runOnMainSync(()->{
   ControlView c=target[0];View decor=a.getWindow().getDecorView();
   assertEquals(2,c.control.tam);assertTrue(c.getLayoutParams().width>before[0]);
   View open=find(decor,a.getString(R.string.ap_color_opacity));assertNotNull(open);open.performClick();
   SeekBar opacity=(SeekBar)find(decor,a.getString(R.string.ap_button_opacity));assertNotNull(opacity);
   opacity.setKeyProgressIncrement(1);opacity.setProgress(24);
   opacity.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT,new KeyEvent(0,KeyEvent.KEYCODE_DPAD_RIGHT));
  });
  ins.waitForIdleSync();
  ins.runOnMainSync(()->{
   ControlView c=target[0];View decor=a.getWindow().getDecorView();
   assertEquals(.25f,c.bodyAlpha,.001f);
   EditText hex=(EditText)find(decor,a.getString(R.string.ap_hex));assertNotNull(hex);
   hex.setText("#wrong");assertNotNull(hex.getError());assertEquals(0xffffffff,c.control.color);
   hex.setText("#E5394F");assertNull(hex.getError());assertEquals(0xffe5394f,c.control.color);
   assertFalse(c.control.systemColor);
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
 @Test public void controllerStartupBadgesAndToolbar(){
  android.app.Instrumentation ins=InstrumentationRegistry.getInstrumentation();
  UiActivity a=(UiActivity)ins.startActivitySync(new android.content.Intent(ins.getTargetContext(),UiActivity.class).putExtra("clear",true).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
  ins.waitForIdleSync();
  ins.runOnMainSync(()->a.menu.controllerConnected(null));
  ins.waitForIdleSync();
  ins.runOnMainSync(()->{
   assertFalse(a.menu.isOpen());
   assertNull(a.findViewById(android.R.id.content).findViewWithTag("rmxp_side_panel"));
   a.menu.open();
   a.findViewById(R.id.rmxp_pad_edit).performClick();
  });
  ins.waitForIdleSync();
  ins.runOnMainSync(()->{
   View handle=find(a.getWindow().getDecorView(),a.getString(R.string.rmxp_edit_move_toolbar));
   assertNotNull(handle);
   View bar=(View)handle.getParent();float y=bar.getY();
   long t=android.os.SystemClock.uptimeMillis();
   handle.dispatchTouchEvent(MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,10,10,0));
   handle.dispatchTouchEvent(MotionEvent.obtain(t,t+20,MotionEvent.ACTION_MOVE,10,80,0));
   handle.dispatchTouchEvent(MotionEvent.obtain(t,t+40,MotionEvent.ACTION_UP,10,80,0));
   assertTrue("Toolbar must move away from the top controls",bar.getY()>y);
   find(bar,a.getString(R.string.rmxp_edit_more)).performClick();
  });
  ins.waitForIdleSync();
  ins.runOnMainSync(()->{
   Switch badges=(Switch)find(a.getWindow().getDecorView(),a.getString(R.string.rmxp_badge_show));
   assertNotNull(badges);assertTrue(badges.isChecked());badges.setChecked(false);
   ViewGroup layer=a.findViewById(android.R.id.content).findViewWithTag("rmxp_touch_controls");
   for(int i=0;i<layer.getChildCount();i++)
    if(layer.getChildAt(i) instanceof ControlView)assertFalse(((ControlView)layer.getChildAt(i)).showBadge);
   PanelLateral.dismissOpen(a);
   find(layer,a.getString(R.string.rmxp_edit_done)).performClick();
   a.menu.controls.recargar();
   for(int i=0;i<layer.getChildCount();i++)
    if(layer.getChildAt(i) instanceof ControlView){
     ControlView c=(ControlView)layer.getChildAt(i);assertFalse(c.editando);assertFalse(c.showBadge);
    }
   a.getSharedPreferences("rmxp_opciones",0).edit().putBoolean("insig_mostrar",true).commit();
   a.menu.controls.recargar();
   for(int i=0;i<layer.getChildCount();i++)
    if(layer.getChildAt(i) instanceof ControlView)assertTrue(((ControlView)layer.getChildAt(i)).showBadge);
   a.finish();
  });
 }

 @Test public void symbolsRemainVisibleWithInvisibleBodyAndSurviveExport(){
  android.app.Instrumentation ins=InstrumentationRegistry.getInstrumentation();
  UiActivity a=(UiActivity)ins.startActivitySync(new android.content.Intent(ins.getTargetContext(),UiActivity.class)
    .putExtra("clear",true).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
  ins.waitForIdleSync();
  ins.runOnMainSync(()->{
   ControlView.Listener listener=new ControlView.Listener(){
    public void press(int key){} public void release(int key){}
    public boolean editTouch(ControlView v,MotionEvent e){return false;}
   };
   for(int key:new int[]{KeyEvent.KEYCODE_ESCAPE,Control.TECLADO,Control.TURBO,0}){
    Control c=Control.boton("test","Esc",key,.5f,.5f,1,false);c.cruceta=key==0;
    c.labelOwn=true;c.labelColor=Color.RED;c.labelOpacity=1;c.systemColor=false;c.color=Color.WHITE;c.opacidad=0;
    java.util.List<Control> list=new java.util.ArrayList<>();list.add(c);
    Control copy=Control.fromJson(Control.toJson(list)).get(0);
    assertTrue(copy.labelOwn);assertFalse(copy.systemColor);assertEquals(Color.WHITE,copy.color);
    assertEquals(Color.RED,copy.labelColor);assertEquals(0,copy.opacidad,.001f);
    ControlView view=new ControlView(a,copy,Color.BLUE,listener);
    view.layout(0,0,180,180);view.bodyAlpha=0;view.labelOwn=true;view.labelAlpha=1;view.labelColor=Color.RED;
    Bitmap b=render(view);boolean visible=false;
    for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++)visible|=Color.alpha(b.getPixel(x,y))>0;
    assertTrue("Labels and icons must be independent of the body",visible);
    view.labelAlpha=0;b=render(view);
    for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++)assertEquals(0,Color.alpha(b.getPixel(x,y)));
   }
   a.finish();
  });
 }

}
