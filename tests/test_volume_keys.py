"""Volume must reach one music-stream adjustment before any menu/game mapping."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class VolumeKeysTest(unittest.TestCase):
    def test_volume_in_menu_and_editor(self):
        source = (ROOT / 'android/app/src/main/java/com/hatkid/mkxpz/MainActivity.java').read_text()
        method = source.split('    public boolean dispatchKeyEvent(KeyEvent evt)', 1)[1].split('    private static boolean isFromGamepad', 1)[0]
        harness = '''
class Base { public static void main(String[] args){VolumeCheck.main(args);} boolean dispatchKeyEvent(KeyEvent e){throw new AssertionError("SDL got volume");} }
class InputDevice {boolean isVirtual(){return false;}}
class KeyEvent {
 static int KEYCODE_VOLUME_UP=24,KEYCODE_VOLUME_DOWN=25,KEYCODE_VOLUME_MUTE=164,KEYCODE_BACK=4,ACTION_DOWN=0,ACTION_UP=1;
 int key,action,repeat;
 KeyEvent(int k,int a,int r){key=k;action=a;repeat=r;}
 int getKeyCode(){return key;} int getAction(){return action;}int getRepeatCount(){return repeat;}InputDevice getDevice(){return null;}
}
class VolumeCheck extends Base {
 static class PanelLateral {static boolean open;static boolean dismissOpen(Object activity){boolean was=open;open=false;return was;}}
 static class android {static class media {static class AudioManager {
  static int STREAM_MUSIC=3, ADJUST_RAISE=1, ADJUST_LOWER=-1, ADJUST_TOGGLE_MUTE=101, FLAG_SHOW_UI=1;
  int count,last;
  void adjustStreamVolume(int stream,int direction,int flags){
   if(stream!=3 || flags!=1)throw new AssertionError("wrong stream/UI");count++;last=direction;
  }
 }}}
 android.media.AudioManager audio=new android.media.AudioManager();
 String AUDIO_SERVICE="audio"; Object getSystemService(String s){return audio;}
 static class Controls {boolean isEditing(){return true;}void onPhysicalInput(){throw new AssertionError("hidden controls");}}
 static class Keyboard {boolean shown(){return false;}void hide(){}}
 static class Pad {boolean onKey(KeyEvent e){throw new AssertionError("game mapping got volume");}}
 static class Menu {int toggles;Controls controls=new Controls(); Keyboard teclado=new Keyboard();Pad pad=new Pad();boolean isOpen(){return true;}void toggle(){toggles++;}}
 Menu mMenu;
 boolean isFromGamepad(KeyEvent e){return false;}
 VolumeCheck getWindow(){return this;}boolean superDispatchKeyEvent(KeyEvent e){throw new AssertionError("menu got volume");}
 public boolean dispatchKeyEvent(KeyEvent evt) METHOD
 void check(int key,int action,int repeat,int expectedCount,int direction){
  if(!dispatchKeyEvent(new KeyEvent(key,action,repeat)))throw new AssertionError("unhandled volume");
  if(audio.count!=expectedCount || audio.last!=direction)throw new AssertionError("duplicate/wrong adjustment");
 }
 public static void main(String[] args){
  VolumeCheck v=new VolumeCheck();
  v.check(24,0,0,1,1);v.check(24,1,0,1,1);
  v.mMenu=new Menu();
  v.check(25,0,0,2,-1);v.check(25,0,1,3,-1);v.check(25,1,0,3,-1);
  v.check(164,0,0,4,101);v.check(164,0,1,4,101);v.check(164,1,0,4,101);
  PanelLateral.open=true;
  if(!v.dispatchKeyEvent(new KeyEvent(4,1,0)) || PanelLateral.open || v.mMenu.toggles!=0)
   throw new AssertionError("Back closed underlying menu instead of side panel");
 }
}
'''.replace('METHOD', method)
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'VolumeCheck.java'
            path.write_text(harness)
            result = subprocess.run(['java', str(path)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == '__main__':
    unittest.main()
