"""Execute the real dispatch method before a simulated SDL surface consumes axes."""
from pathlib import Path
import subprocess,tempfile,unittest
ROOT=Path(__file__).resolve().parents[1]
class ControllerRoutingTest(unittest.TestCase):
 def test_axes_reach_mapper_before_sdl(self):
  s=(ROOT/'android/app/src/main/java/com/hatkid/mkxpz/MainActivity.java').read_text()
  method=s.split('public boolean dispatchGenericMotionEvent(MotionEvent evt)',1)[1].split('    /**',1)[0]
  harness="""
class Base {int sdl;public boolean dispatchGenericMotionEvent(MotionEvent e){sdl++;return true;}public static void main(String[] a){Check.main(a);}}
class MotionEvent {boolean gamepad=true;Object getDevice(){return gamepad;}}
class com {static class hatkid {static class mkxpz {static class controles {static class PadMapper {static boolean isGamepad(Object d){return (Boolean)d;}}}}}}
class Check extends Base {
 static class Controls {boolean editing;int physical;boolean isEditing(){return editing;}void onPhysicalInput(){physical++;}}
 static class Pad {int motion;boolean onMotion(MotionEvent e){motion++;return true;}}
 static class Menu {boolean open;Controls controls=new Controls();Pad pad=new Pad();boolean isOpen(){return open;}}
 Menu mMenu=new Menu();
 public boolean dispatchGenericMotionEvent(MotionEvent evt) METHOD
 public static void main(String[] args){Check c=new Check();MotionEvent e=new MotionEvent();
  c.dispatchGenericMotionEvent(e);if(c.sdl!=0||c.mMenu.pad.motion!=1||c.mMenu.controls.physical!=1)throw new AssertionError("SDL intercepted controller");
  c.mMenu.open=true;c.dispatchGenericMotionEvent(e);c.mMenu.open=false;c.mMenu.controls.editing=true;c.dispatchGenericMotionEvent(e);
  if(c.sdl!=0||c.mMenu.pad.motion!=1)throw new AssertionError("Axes leaked behind overlay");
  e.gamepad=false;c.dispatchGenericMotionEvent(e);if(c.sdl!=1)throw new AssertionError("Non-controller input lost");
  c.mMenu=null;e.gamepad=true;c.dispatchGenericMotionEvent(e);if(c.sdl!=2)throw new AssertionError("Startup input lost");
 }
}
""".replace('METHOD',method)
  with tempfile.TemporaryDirectory() as tmp:
   p=Path(tmp)/'Check.java';p.write_text(harness)
   result=subprocess.run(['java',str(p)],capture_output=True,text=True)
   self.assertEqual(0,result.returncode,result.stderr)
