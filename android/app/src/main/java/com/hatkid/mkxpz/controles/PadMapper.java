package com.hatkid.mkxpz.controles;

import android.content.SharedPreferences;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import java.util.*;

/** Physical controller inputs are tracked separately, then merged into game keys. */
public class PadMapper {
    public static final int XBOX=1, SWITCH=2, KEYBOARD=2000;
    public static final int LS_UP=1001, LS_DOWN=1002, LS_LEFT=1003, LS_RIGHT=1004;
    public static final int RS_UP=1011, RS_DOWN=1012, RS_LEFT=1013, RS_RIGHT=1014;
    public static final Map<Integer,String> BOTONES=new LinkedHashMap<>();
    private static final Map<Integer,Integer> DEFAULTS=new LinkedHashMap<>();
    static {
        def(KeyEvent.KEYCODE_BUTTON_A,"A",KeyEvent.KEYCODE_ENTER);
        def(KeyEvent.KEYCODE_BUTTON_B,"B",KeyEvent.KEYCODE_X);
        def(KeyEvent.KEYCODE_BUTTON_X,"X",KeyEvent.KEYCODE_Z);
        def(KeyEvent.KEYCODE_BUTTON_Y,"Y",KeyEvent.KEYCODE_D);
        def(KeyEvent.KEYCODE_BUTTON_C,"C",0);def(KeyEvent.KEYCODE_BUTTON_Z,"Z",0);
        def(KeyEvent.KEYCODE_BUTTON_MODE,"Guide",Control.MENU);
        def(KeyEvent.KEYCODE_BACK,"Back",Control.MENU);
        for(int i=0;i<16;i++)def(KeyEvent.KEYCODE_BUTTON_1+i,"Button "+(i+1),0);
        def(KeyEvent.KEYCODE_BUTTON_L1,"LB",KeyEvent.KEYCODE_A);
        def(KeyEvent.KEYCODE_BUTTON_R1,"RB",KeyEvent.KEYCODE_S);
        def(KeyEvent.KEYCODE_BUTTON_L2,"LT",KeyEvent.KEYCODE_Q);
        def(KeyEvent.KEYCODE_BUTTON_R2,"RT",KeyEvent.KEYCODE_W);
        def(KeyEvent.KEYCODE_BUTTON_START,"Start",KeyEvent.KEYCODE_ESCAPE);
        def(KeyEvent.KEYCODE_BUTTON_SELECT,"Select",Control.MENU);
        def(KeyEvent.KEYCODE_BUTTON_THUMBL,"L3",KeyEvent.KEYCODE_SHIFT_LEFT);
        def(KeyEvent.KEYCODE_BUTTON_THUMBR,"R3",KeyEvent.KEYCODE_F5);
        def(KeyEvent.KEYCODE_DPAD_UP,"D ↑",KeyEvent.KEYCODE_DPAD_UP);
        def(KeyEvent.KEYCODE_DPAD_DOWN,"D ↓",KeyEvent.KEYCODE_DPAD_DOWN);
        def(KeyEvent.KEYCODE_DPAD_LEFT,"D ←",KeyEvent.KEYCODE_DPAD_LEFT);
        def(KeyEvent.KEYCODE_DPAD_RIGHT,"D →",KeyEvent.KEYCODE_DPAD_RIGHT);
        def(LS_UP,"L ↑",KeyEvent.KEYCODE_DPAD_UP);def(LS_DOWN,"L ↓",KeyEvent.KEYCODE_DPAD_DOWN);
        def(LS_LEFT,"L ←",KeyEvent.KEYCODE_DPAD_LEFT);def(LS_RIGHT,"L →",KeyEvent.KEYCODE_DPAD_RIGHT);
        def(RS_UP,"R ↑",KeyEvent.KEYCODE_A);def(RS_DOWN,"R ↓",KeyEvent.KEYCODE_S);
        def(RS_LEFT,"R ←",KeyEvent.KEYCODE_Q);def(RS_RIGHT,"R →",KeyEvent.KEYCODE_W);
    }
    private static void def(int code,String name,int key){BOTONES.put(code,name);DEFAULTS.put(code,key);}
    private final SharedPreferences prefs;
    private final TouchControls.Actions actions;
    private int layout, deviceId=-1;
    private String deviceKey;
    private final Set<Integer> digital=new HashSet<>(), axes=new HashSet<>(), keyboardHeld=new HashSet<>();
    private final Map<Integer,Integer> active=new HashMap<>(), counts=new HashMap<>();

    public PadMapper(SharedPreferences prefs,TouchControls.Actions actions){
        this.prefs=prefs;this.actions=actions;layout=prefs.getInt("mando_layout",XBOX);
    }
    public int profile(){return layout;}
    public String name(int code){
        if(code>=KEYBOARD)return Teclas.nombre(code-KEYBOARD);
        if(code>=1100 && code<1228)return MotionEvent.axisToString((code-1100)/2).replace("AXIS_","") + (code%2==0?" −":" +");
        if(layout!=SWITCH)return BOTONES.getOrDefault(code,Teclas.nombre(code));
        if(code==KeyEvent.KEYCODE_BUTTON_A)return "B";
        if(code==KeyEvent.KEYCODE_BUTTON_B)return "A";
        if(code==KeyEvent.KEYCODE_BUTTON_X)return "Y";
        if(code==KeyEvent.KEYCODE_BUTTON_Y)return "X";
        if(code==KeyEvent.KEYCODE_BUTTON_L1)return "L";
        if(code==KeyEvent.KEYCODE_BUTTON_R1)return "R";
        if(code==KeyEvent.KEYCODE_BUTTON_L2)return "ZL";
        if(code==KeyEvent.KEYCODE_BUTTON_R2)return "ZR";
        if(code==KeyEvent.KEYCODE_BUTTON_START)return "+";
        if(code==KeyEvent.KEYCODE_BUTTON_SELECT)return "−";
        return BOTONES.getOrDefault(code,Teclas.nombre(code));
    }
    private int letterCode(int code){
        if(layout!=SWITCH)return code;
        if(code==KeyEvent.KEYCODE_BUTTON_A)return KeyEvent.KEYCODE_BUTTON_B;
        if(code==KeyEvent.KEYCODE_BUTTON_B)return KeyEvent.KEYCODE_BUTTON_A;
        if(code==KeyEvent.KEYCODE_BUTTON_X)return KeyEvent.KEYCODE_BUTTON_Y;
        if(code==KeyEvent.KEYCODE_BUTTON_Y)return KeyEvent.KEYCODE_BUTTON_X;
        return code;
    }
    public int keyFor(int code){
        if(code>=KEYBOARD)return prefs.getInt("teclado_mapa_"+(code-KEYBOARD),code-KEYBOARD);
        int fallback=DEFAULTS.containsKey(letterCode(code))?DEFAULTS.get(letterCode(code)):0;
        // Keep explicitly saved mappings from older versions; each new layout has its own overrides.
        return prefs.getInt("mando_"+layout+"_"+code,layout==XBOX?prefs.getInt("mando_"+code,fallback):fallback);
    }
    public int keyForScreen(int namedButton){return keyFor(letterCode(namedButton));}
    public void setScreenKey(int namedButton,int key){setKey(letterCode(namedButton),key);}
    public void setKey(int code,int key){
        if(!validSource(code) || key<Control.OCULTAR || key>KeyEvent.getMaxKeyCode())
            throw new IllegalArgumentException("Invalid input mapping");
        releaseAll();
        prefs.edit().putInt(code>=KEYBOARD?"teclado_mapa_"+(code-KEYBOARD):"mando_"+layout+"_"+code,key).apply();
    }
    public static boolean validSource(int code){
        int key=code>=KEYBOARD?code-KEYBOARD:code;
        return BOTONES.containsKey(code)||(code>=1100 && code<1228)||(key>0 && key<=KeyEvent.getMaxKeyCode());
    }
    public List<Integer> sources(boolean keyboard){
        Set<Integer> codes=new LinkedHashSet<>();
        if(!keyboard)codes.addAll(BOTONES.keySet());
        String prefix=keyboard?"teclado_mapa_":"mando_"+layout+"_";
        for(String name:prefs.getAll().keySet())if(name.startsWith(prefix))try{
            int code=Integer.parseInt(name.substring(prefix.length()))+(keyboard?KEYBOARD:0);
            if(validSource(code))codes.add(code);
        }catch(NumberFormatException ignored){}
        return new ArrayList<>(codes);
    }
    public void resetKeyboard(){
        releaseAll();SharedPreferences.Editor edit=prefs.edit();
        for(String key:prefs.getAll().keySet())if(key.startsWith("teclado_mapa_"))edit.remove(key);
        edit.apply();
    }
    public static boolean isKeyboard(InputDevice device){
        return device!=null && !device.isVirtual() && !isGamepad(device) &&
            (device.getSources()&InputDevice.SOURCE_KEYBOARD)==InputDevice.SOURCE_KEYBOARD;
    }
    public int sourceForKey(KeyEvent event){
        if(isGamepad(event.getDevice()))return event.getKeyCode();
        return isKeyboard(event.getDevice())?KEYBOARD+event.getKeyCode():0;
    }
    public void onSource(int code,boolean down){
        if(!validSource(code))return;
        if(down)digital.add(code);else digital.remove(code);
        reconcile(code);
    }
    public void selectProfile(int selected){
        releaseAll();layout=selected==SWITCH?SWITCH:XBOX;
        SharedPreferences.Editor e=prefs.edit().putInt("mando_layout",layout);
        if(deviceKey!=null)e.putInt(deviceKey,layout);
        e.apply();
    }
    public void reset(){
        releaseAll();SharedPreferences.Editor e=prefs.edit();
        for(String key:prefs.getAll().keySet())if(key.startsWith("mando_"+layout+"_"))e.remove(key);
        for(int code:BOTONES.keySet()){e.remove("mando_"+layout+"_"+code);if(layout==XBOX)e.remove("mando_"+code);}
        e.apply();
    }
    public static boolean isGamepad(InputDevice device){
        if(device==null)return false;
        int s=device.getSources();
        return (s&InputDevice.SOURCE_GAMEPAD)==InputDevice.SOURCE_GAMEPAD || (s&InputDevice.SOURCE_JOYSTICK)==InputDevice.SOURCE_JOYSTICK;
    }
    public boolean connected(InputDevice device){
        if(!isGamepad(device))return false;
        String key="mando_dispositivo_"+device.getDescriptor();
        boolean fresh=deviceId!=device.getId() || !key.equals(deviceKey);
        if(fresh){ releaseAll();deviceId=device.getId();deviceKey=key; }
        if(prefs.contains(deviceKey)){
            int saved=prefs.getInt(deviceKey,XBOX);
            if(layout!=saved){releaseAll();layout=saved;prefs.edit().putInt("mando_layout",layout).apply();}
            if(fresh)actions.controllerConnected(device);
            return true;
        }
        prefs.edit().putInt(deviceKey,layout).apply();
        if(fresh)actions.controllerConnected(device);
        return true;
    }
    public void disconnected(int id){
        if(id==deviceId){releaseAll();deviceId=-1;deviceKey=null;}
    }
    public int cancelButton(){return layout==SWITCH?KeyEvent.KEYCODE_BUTTON_A:KeyEvent.KEYCODE_BUTTON_B;}
    public boolean onKey(KeyEvent event){
        int code=sourceForKey(event);
        if(code==0)return false;
        if(code>=KEYBOARD){
            if(!prefs.contains("teclado_mapa_"+(code-KEYBOARD)))return observeKeyboard(code-KEYBOARD,event.getAction());
        }else{
            if(!connected(event.getDevice()))return true;
            if(!BOTONES.containsKey(code)&&!prefs.contains("mando_"+layout+"_"+code))return false;
        }
        if(event.getRepeatCount()>0)return true;
        if(event.getAction()==KeyEvent.ACTION_DOWN)onSource(code,true);
        else if(event.getAction()==KeyEvent.ACTION_UP)onSource(code,false);
        return true;
    }
    boolean observeKeyboard(int key,int action){
        if(action==KeyEvent.ACTION_DOWN)keyboardHeld.add(key);
        else if(action==KeyEvent.ACTION_UP){
            keyboardHeld.remove(key);
            return counts.containsKey(key); // a controller still owns the same game key
        }
        return false; // preserve SDL text/modifier handling for unmodified keyboard events
    }
    private void reconcile(int code){
        boolean down=digital.contains(code)||axes.contains(code);
        if(down && !active.containsKey(code)){
            int key=keyFor(code);active.put(code,key);
            if(key>0){int n=counts.containsKey(key)?counts.get(key):0;counts.put(key,n+1);if(n==0&&!keyboardHeld.contains(key))actions.keyDown(key);}
            else if(key<0)actions.action(key);
        }else if(!down && active.containsKey(code)){
            int key=active.remove(code);
            if(key>0){int n=counts.get(key)-1;if(n==0){counts.remove(key);if(!keyboardHeld.contains(key))actions.keyUp(key);}else counts.put(key,n);}
        }
    }
    private void axis(int code,float value){
        boolean down=value>(axes.contains(code)?.35f:.55f);
        if(down)axes.add(code);else axes.remove(code);
        reconcile(code);
    }
    /** Normalised axes: diagonal directions and simultaneous sticks remain independent. */
    public void updateAxes(float lx,float ly,float rx,float ry,float hx,float hy,float lt,float rt){
        axis(LS_UP,-ly);axis(LS_DOWN,ly);axis(LS_LEFT,-lx);axis(LS_RIGHT,lx);
        axis(RS_UP,-ry);axis(RS_DOWN,ry);axis(RS_LEFT,-rx);axis(RS_RIGHT,rx);
        axis(KeyEvent.KEYCODE_DPAD_UP,-hy);axis(KeyEvent.KEYCODE_DPAD_DOWN,hy);
        axis(KeyEvent.KEYCODE_DPAD_LEFT,-hx);axis(KeyEvent.KEYCODE_DPAD_RIGHT,hx);
        axis(KeyEvent.KEYCODE_BUTTON_L2,lt);axis(KeyEvent.KEYCODE_BUTTON_R2,rt);
    }
    private float value(MotionEvent e,int axis){
        InputDevice.MotionRange range=e.getDevice().getMotionRange(axis,e.getSource());
        float v=e.getAxisValue(axis);
        return range!=null && Math.abs(v)<=range.getFlat()?0:v;
    }
    private float trigger(MotionEvent e,int axis){
        InputDevice.MotionRange range=e.getDevice().getMotionRange(axis,e.getSource());
        float v=e.getAxisValue(axis);
        if(range!=null && range.getMin()<0 && range.getMax()>range.getMin())
            return (v-range.getMin())/(range.getMax()-range.getMin());
        return v;
    }
    public int sourceForMotion(MotionEvent e){
        if(!isGamepad(e.getDevice())||e.getActionMasked()!=MotionEvent.ACTION_MOVE)return 0;
        InputDevice d=e.getDevice();
        int rx=d.getMotionRange(MotionEvent.AXIS_Z,e.getSource())!=null?MotionEvent.AXIS_Z:MotionEvent.AXIS_RX;
        int ry=d.getMotionRange(MotionEvent.AXIS_RZ,e.getSource())!=null?MotionEvent.AXIS_RZ:MotionEvent.AXIS_RY;
        float[] values={value(e,MotionEvent.AXIS_X),value(e,MotionEvent.AXIS_Y),value(e,rx),value(e,ry),
            value(e,MotionEvent.AXIS_HAT_X),value(e,MotionEvent.AXIS_HAT_Y)};
        int[] negative={LS_LEFT,LS_UP,RS_LEFT,RS_UP,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_UP};
        int[] positive={LS_RIGHT,LS_DOWN,RS_RIGHT,RS_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_DOWN};
        for(int i=0;i<values.length;i++)if(Math.abs(values[i])>.65f)return values[i]<0?negative[i]:positive[i];
        if(Math.max(trigger(e,MotionEvent.AXIS_LTRIGGER),trigger(e,MotionEvent.AXIS_BRAKE))>.65f)return KeyEvent.KEYCODE_BUTTON_L2;
        if(Math.max(trigger(e,MotionEvent.AXIS_RTRIGGER),trigger(e,MotionEvent.AXIS_GAS))>.65f)return KeyEvent.KEYCODE_BUTTON_R2;
        for(InputDevice.MotionRange range:d.getMotionRanges()) {
            int axis=range.getAxis();
            if(axis<0 || axis>=64)continue;
            float v=value(e,axis);
            if(Math.abs(v)>.65f)return 1100+axis*2+(v>0?1:0);
        }
        return 0;
    }
    public boolean onMotion(MotionEvent e){
        if(!isGamepad(e.getDevice())||e.getActionMasked()!=MotionEvent.ACTION_MOVE)return false;
        if(!connected(e.getDevice()))return true;
        InputDevice d=e.getDevice();
        int rx=d.getMotionRange(MotionEvent.AXIS_Z,e.getSource())!=null?MotionEvent.AXIS_Z:MotionEvent.AXIS_RX;
        int ry=d.getMotionRange(MotionEvent.AXIS_RZ,e.getSource())!=null?MotionEvent.AXIS_RZ:MotionEvent.AXIS_RY;
        updateAxes(value(e,MotionEvent.AXIS_X),value(e,MotionEvent.AXIS_Y),value(e,rx),value(e,ry),
            value(e,MotionEvent.AXIS_HAT_X),value(e,MotionEvent.AXIS_HAT_Y),
            Math.max(trigger(e,MotionEvent.AXIS_LTRIGGER),trigger(e,MotionEvent.AXIS_BRAKE)),
            Math.max(trigger(e,MotionEvent.AXIS_RTRIGGER),trigger(e,MotionEvent.AXIS_GAS)));
        for(int source:sources(false))if(source>=1100 && source<1228){
            float v=value(e,(source-1100)/2);
            axis(source,source%2==0?-v:v);
        }
        return true;
    }
    public void releaseAll(){
        Set<Integer> held=new HashSet<>(counts.keySet());held.addAll(keyboardHeld);
        for(int key:held)actions.keyUp(key);
        counts.clear();active.clear();digital.clear();axes.clear();keyboardHeld.clear();
    }
}
