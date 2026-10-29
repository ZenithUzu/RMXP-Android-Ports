from pathlib import Path
import shutil
import subprocess
import unittest
ROOT = Path(__file__).resolve().parents[1]

class RGSSLinkerTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('ruby'), 'Ruby is available in CI')
    def test_bootstrap_audio_forwarding_and_flat_maps(self):
        r = subprocess.run(['ruby', 'tests/rgss_linker_check.rb'], cwd=ROOT, capture_output=True, text=True)
        self.assertEqual(0, r.returncode, r.stdout + r.stderr)
