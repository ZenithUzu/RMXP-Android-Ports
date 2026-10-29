"""Run the real menu speed policy against lifecycle/editor transitions."""
import pathlib
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]


class MenuPauseTest(unittest.TestCase):
    def test_menu_editor_background_and_turbo(self):
        source = (ROOT / 'android/app/src/main/java/com/hatkid/mkxpz/OptionsMenu.java').read_text()
        method = source.split('    private void applySpeed()', 1)[1].split('    private void applyVideo()', 1)[0]
        harness = '''
class MenuPauseCheck {
    static class R { static class id { static int rmxp_status; }
        static class string { static int rmxp_paused=1, rmxp_running=2; } }
    static class TextView { int text; void setText(String s) {} void setText(int i) {text=i;} }
    static class Root { TextView status = new TextView(); Object findViewById(int id) {return status;} }
    static class Controls { boolean editing; boolean isEditing(){return editing;} void refreshTurbo(){} }
    static class MainActivity { static int speed; static void nativeSetSpeed(int i){speed=i;} }
    Root mRoot = new Root(); TextView mSpeedValue = new TextView(); Controls controls = new Controls();
    boolean mBackground, mTurbo, open; int running=1;
    int pref(String key,int def){return key.equals("seguir_en_menu") ? running : def;}
    boolean isOpen(){return open;}
    private void applySpeed() METHOD
    void check(int expected) {
        applySpeed();
        if (MainActivity.speed != expected) throw new AssertionError("speed="+MainActivity.speed+", expected="+expected);
        if (mRoot.status.text != (expected==0 ? 1:2)) throw new AssertionError("wrong status");
    }
    public static void main(String[] args){
        MenuPauseCheck m = new MenuPauseCheck();
        m.check(1); m.open=true; m.check(1);
        m.running=0; m.check(0);
        m.open=false; m.controls.editing=true; m.check(0);
        m.running=1; m.check(1);
        m.mTurbo=true; m.check(5);
        m.mBackground=true; m.check(0);
        m.mBackground=false; m.check(5);
        m.running=0; m.check(0);
        m.controls.editing=false; m.check(5);
        m.mTurbo=false; m.check(1);
    }
}
'''.replace('METHOD', method)
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / 'MenuPauseCheck.java'
            path.write_text(harness)
            subprocess.run(['java', str(path)], check=True, capture_output=True, text=True)


if __name__ == '__main__':
    unittest.main()
