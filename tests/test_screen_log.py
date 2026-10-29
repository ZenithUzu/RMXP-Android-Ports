"""Exercise the Ruby observer with a simulated Graphics, without a game or GPU."""
import pathlib
import shutil
import subprocess
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]


class ScreenLogTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("ruby"), "Ruby is required (available on GitHub Actions)")
    def test_observer_preserves_calls_results_errors_and_limits_frame_logs(self):
        result = subprocess.run(["ruby", "tests/screen_log_check.rb"], cwd=ROOT,
                                text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
