"""A blocked SDL quit handshake must not leave the game process alive indefinitely."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ShutdownTest(unittest.TestCase):
    def test_native_quit_timeout(self):
        source = (ROOT / 'android/app/src/main/java/com/hatkid/mkxpz/MainActivity.java').read_text()
        method = source.split('    protected void onDestroy()', 1)[1].split('    @Override', 1)[0]
        harness = '''
class DestroyCheck extends Parent {
 static final java.util.concurrent.CountDownLatch killed = new java.util.concurrent.CountDownLatch(1);
 static boolean exited;
 static class System {static void exit(int status){exited=true;}}
 static class android {static class os {
  static class SystemClock {static void sleep(long n){try{Thread.sleep(n);}catch(InterruptedException e){throw new AssertionError(e);}}}
  static class Process {
   static int myPid(){return 42;}
   static void killProcess(int pid){if(pid!=42)throw new AssertionError("wrong process");killed.countDown();}
  }
 }}
 protected void onDestroy() METHOD
 public static void main(String[] args) {
  long start=java.lang.System.nanoTime();
  new DestroyCheck().onDestroy();
  long ms=(java.lang.System.nanoTime()-start)/1000000;
  if(!exited || killed.getCount()!=0 || ms<1500 || ms>4000)throw new AssertionError("quit watchdog failed: "+ms);
 }
}
class Parent {
 protected void onDestroy(){try{
  if(!DestroyCheck.killed.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("SDL stayed blocked");
 }catch(InterruptedException e){throw new AssertionError(e);}}
}
'''.replace('METHOD', method)
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'DestroyCheck.java'
            path.write_text(harness)
            result = subprocess.run(['java', str(path)], capture_output=True, text=True, timeout=10)
            self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == '__main__':
    unittest.main()
