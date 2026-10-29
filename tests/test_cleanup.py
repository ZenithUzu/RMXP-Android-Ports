import os
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
import rmxp_port as port
import portemon_clean as clean


class CleanupTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.home = Path(self.temp.name) / 'user'
        self.android = Path(self.temp.name) / 'android'
        self.addCleanup(patch.stopall)
        patch.object(port, 'HOME', str(self.home)).start()
        patch.object(port, 'ANDROID', str(self.android)).start()
        patch.object(port, 'MOTOR_VERSION', 'motor-v13').start()
        self.messages = []

    def file(self, relative, data=b'keep', android=False):
        p = (self.android if android else self.home) / relative
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(data)
        return p

    def fixtures(self):
        old = self.file('apks/Relic v1.apk', b'old')
        latest = self.file('apks/Relic v2.apk', b'new')
        os.utime(old, (100, 100)); os.utime(latest, (200, 200))
        part = self.file('apks/Relic v3.apk.part')
        engine = self.file('motor/motor-v12/lib/old.so')
        kept = [latest, self.file('motor/motor-v13/lib/current.so'),
                self.file('juegos/relic/firma.jks'), self.file('juegos/relic/juego.json'),
                self.file('logs/android.txt'), self.file('zips/relic/Data/game.dat'),
                self.file('trabajo/relic/game.dat'), self.file('motor/custom/lib.so'),
                self.file('apks/notes.txt'), self.file('apks/manual.apk')]
        return old, part, engine, kept

    def test_preview_is_non_destructive_and_apply_keeps_protected_data(self):
        old, part, engine, kept = self.fixtures()
        build = self.file('app/build/output.tmp', android=True)
        self.assertEqual(port.limpiar(self.messages.append), 0)
        self.assertTrue(all(p.exists() for p in [old, part, engine, build] + kept))
        freed = port.limpiar(self.messages.append, confirm=lambda: True)
        self.assertEqual(freed, 11)
        self.assertFalse(any(p.exists() for p in [old, part, engine]))
        self.assertTrue(all(p.exists() for p in kept + [build]))
        port.limpiar(self.messages.append, confirm=lambda: True, include_build=True)
        self.assertFalse(build.exists())
        self.assertTrue(all(p.exists() for p in kept))

    def test_cancellation_and_failure_are_reported_without_false_success(self):
        self.fixtures()
        self.assertEqual(port.limpiar(self.messages.append, confirm=lambda: False), 0)
        candidate = clean.plan(str(self.home), str(self.android), 'motor-v13')[0]
        with patch.object(clean.os, 'remove', side_effect=PermissionError('in use')):
            freed, failures = clean.execute([candidate], self.messages.append)
        self.assertEqual((freed, failures), (0, 1))
        self.assertTrue(Path(candidate.path).exists())
        self.assertIn('No se pudo', self.messages[-1])

    def test_links_are_not_followed(self):
        outside = self.file('valuable.apk')
        folder = self.home / 'apks'
        folder.mkdir()
        try:
            (folder / 'Relic v1.apk').symlink_to(outside)
        except OSError:
            self.skipTest('Symlinks not available to this Windows account')
        self.file('apks/Relic v2.apk')
        self.assertEqual(clean.plan(str(self.home), str(self.android), 'motor-v13'), [])
        self.assertTrue(outside.exists())

    def test_build_lock_blocks_another_process_and_releases_after_exit(self):
        self.fixtures()
        program = ('import sys; import rmxp_port as p; p.HOME=sys.argv[1]; '
                   '\ntry:\n with p.workspace_guard(): pass\n'
                   'except p.PortError:\n sys.exit(7)\n')
        env = dict(os.environ, PYTHONPATH=str(Path(port.__file__).parent))
        with port.workspace_guard():
            with port.workspace_guard():  # ZIP extraction inside a build remains possible.
                result = subprocess.run([sys.executable, '-c', program, str(self.home)], env=env,
                                        capture_output=True, timeout=15)
                self.assertEqual(result.returncode, 7, result.stderr)
        result = subprocess.run([sys.executable, '-c', program, str(self.home)], env=env,
                                capture_output=True, timeout=15)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_build_lock_blocks_cleanup_in_another_thread(self):
        ready, finish = threading.Event(), threading.Event()
        def building():
            with port.workspace_guard():
                ready.set()
                finish.wait(5)
        worker = threading.Thread(target=building)
        worker.start()
        try:
            self.assertTrue(ready.wait(3))
            with self.assertRaises(port.PortError):
                port.limpiar(self.messages.append, confirm=lambda: True)
        finally:
            finish.set()
            worker.join(5)


if __name__ == '__main__':
    unittest.main()
