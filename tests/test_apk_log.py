"""Captura ADB: aislamiento por juego, persistencia y cierre del proceso."""
import os
from pathlib import Path
import sys
import tempfile
import threading
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import rmxp_port


class ApkLogTests(unittest.TestCase):
    def test_device_selection(self):
        with patch.object(rmxp_port, "run", return_value=(0, "List of devices attached\nphone\tdevice\n")):
            self.assertEqual(rmxp_port.adb_device("sdk", lambda _: None)[-2:], ["-s", "phone"])
        for output in ("phone\tunauthorized\n", "one\tdevice\ntwo\tdevice\n"):
            with patch.object(rmxp_port, "run", return_value=(0, output)):
                with self.assertRaises(rmxp_port.PortError):
                    rmxp_port.adb_device("sdk", lambda _: None)

    def test_capture_starts_before_launch_and_install_keeps_data(self):
        calls = []
        def run(cmd, log):
            calls.append(cmd)
            return (0, "Success") if "install" in cmd else (0, "Events injected: 1")
        with patch.object(rmxp_port, "adb_device", return_value=["adb", "-s", "phone"]), \
             patch.object(rmxp_port, "run", side_effect=run):
            rmxp_port.adb_install("a.apk", {"id": "com.test.game"}, "sdk", lambda _: None,
                                  lambda adb, package: calls.append(["capture", package]))
        self.assertEqual(calls[0], ["adb", "-s", "phone", "install", "-r", "a.apk"])
        self.assertEqual(calls[1], ["capture", "com.test.game"])
        self.assertIn("monkey", calls[2])
        self.assertFalse(any("uninstall" in cmd for cmd in calls))

    def test_capture_saves_all_lines_and_stops(self):
        with tempfile.TemporaryDirectory() as tmp:
            fake = Path(tmp) / "adb.py"
            fake.write_text('''import sys, time
assert "--uid=12345" in sys.argv
assert "-T" in sys.argv and "crash" in sys.argv
for n in range(12000):
    print("line %d" % n)
print("Fatal signal from game", flush=True)
time.sleep(30)
''')
            ready = threading.Event()
            def visible(line):
                if "Fatal signal" in line:
                    ready.set()
            with patch.object(rmxp_port, "HOME", tmp), \
                 patch.object(rmxp_port, "run", return_value=(0, "package:com.test.game uid:12345\n")):
                capture = rmxp_port.ApkLog([sys.executable, str(fake)], "com.test.game", visible, lambda _: None)
                try:
                    self.assertTrue(ready.wait(5), "No llego la salida del proceso")
                finally:
                    capture.stop()
                self.assertIsNotNone(capture.process.poll())
                self.assertFalse(capture.thread.is_alive())
                self.assertTrue(capture.output.closed)
                text = Path(capture.path).read_text()
                self.assertIn("line 0\n", text)
                self.assertIn("line 11999\n", text)
                self.assertIn("Fatal signal from game", text)

    def test_uid_must_match_exact_package(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(rmxp_port, "HOME", tmp), \
             patch.object(rmxp_port, "run", return_value=(0, "package:com.test.game.other uid:999\n")):
            with self.assertRaises(rmxp_port.PortError):
                rmxp_port.ApkLog(["adb"], "com.test.game", lambda _: None, lambda _: None)
            self.assertFalse(os.path.exists(os.path.join(tmp, "registros")))

    def test_rejects_invalid_package_before_shell(self):
        with patch.object(rmxp_port, "run") as run:
            with self.assertRaises(rmxp_port.PortError):
                rmxp_port.adb_launch(["adb"], "com.test; command", lambda _: None)
            with self.assertRaises(rmxp_port.PortError):
                rmxp_port.ApkLog(["adb"], "com.test; command", lambda _: None, lambda _: None)
            run.assert_not_called()


if __name__ == "__main__":
    unittest.main()
