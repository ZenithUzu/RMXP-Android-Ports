"""Fault-injection check of the Activity's config restore transaction."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ConfigRestoreTest(unittest.TestCase):
    def test_validation_and_write_failure_stop_startup(self):
        source = (ROOT / 'android/app/src/main/java/com/hatkid/mkxpz/MainActivity.java').read_text()
        method = source.split('    private boolean restoreConfig(File gameDir)', 1)[1].split('    /** Moves into dst', 1)[0]
        harness = '''
import java.io.*;
class RestoreCheck {
 static String ASSETS_GAME_DIR="game", TAG="test";
 static byte[] data="{}".getBytes();
 static boolean invalid, fail;
 static int started, finished, rolledBack;
 static class org { static class json { static class JSONObject {
  JSONObject(String s) throws IOException { if(invalid) throw new IOException("bad JSON"); }
 } } }
 static class android { static class util { static class AtomicFile {
  File file; AtomicFile(File f){file=f;}
  FileOutputStream startWrite() throws IOException {started++; return new FileOutputStream(file) {
   public void write(byte[] b) throws IOException { if(fail) throw new IOException("disk full"); super.write(b); }
  };}
  void finishWrite(FileOutputStream s) throws IOException {finished++;s.close();}
  void failWrite(FileOutputStream s) {rolledBack++;try{s.close();}catch(IOException e){}}
 } } }
 static class Log { static void i(String t,String s){} static void e(String t,String s,Exception e){} }
 static class Assets { InputStream open(String s){return new ByteArrayInputStream(data);} }
 Assets getAssets(){return new Assets();} int versionCode(){return 12;}
 private boolean restoreConfig(File gameDir) METHOD
 public static void main(String[] args) throws Exception {
  File dir=new File(args[0]);RestoreCheck r=new RestoreCheck();
  invalid=true;
  if(r.restoreConfig(dir) || started!=0)throw new AssertionError("invalid config touched destination");
  invalid=false;
  if(!r.restoreConfig(dir) || finished!=1)throw new AssertionError("valid config not committed");
  if(!new String(java.nio.file.Files.readAllBytes(new File(dir,"mkxp.json").toPath())).equals("{}"))throw new AssertionError("changed bytes");
  fail=true;
  if(r.restoreConfig(dir) || finished!=1 || rolledBack!=1)throw new AssertionError("failed write not rolled back");
 }
}
'''.replace('METHOD', method)
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'RestoreCheck.java'
            path.write_text(harness)
            subprocess.run(['java', str(path), tmp], check=True, capture_output=True, text=True)


if __name__ == '__main__':
    unittest.main()
