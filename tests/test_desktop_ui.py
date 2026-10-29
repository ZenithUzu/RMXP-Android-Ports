"""Check the real Tk layout at two window sizes; no SDK, game or USB device required."""
import os
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
import rmxp_port as port


class DesktopUiTest(unittest.TestCase):
    @unittest.skipUnless(os.environ.get('DISPLAY'), 'Run with xvfb-run for Tk layout checks')
    def test_project_form_scrolls_and_build_action_and_logs_stay_accessible(self):
        import tkinter as tk
        from tkinter import ttk
        real_tk = tk.Tk
        errors = []

        def widgets(widget):
            yield widget
            for child in widget.winfo_children():
                yield from widgets(child)

        def create():
            root = real_tk()
            def inspect():
                try:
                    root.update_idletasks()
                    self.assertEqual(root.title(), 'Portémon · PC')
                    all_widgets = list(widgets(root))
                    go = next(w for w in all_widgets if isinstance(w, tk.Button) and w.cget('text') == 'Crear APK')
                    self.assertEqual(len([w for w in all_widgets if isinstance(w, tk.Entry)]), 3)
                    self.assertEqual(len([w for w in all_widgets if isinstance(w, tk.Text)]), 2)
                    notebook = next(w for w in all_widgets if isinstance(w, ttk.Notebook))
                    self.assertEqual(len(notebook.tabs()), 2)
                    canvas = next(w for w in all_widgets if isinstance(w, tk.Canvas))
                    for width, height in [(1200, 840), (1000, 700)]:
                        root.geometry(f'{width}x{height}')
                        root.update()
                        self.assertGreater(go.winfo_height(), 30)
                        self.assertLessEqual(go.winfo_rooty() + go.winfo_height(), root.winfo_rooty() + root.winfo_height())
                        self.assertLess(canvas.yview()[1], 1.0, 'Long form must have scrollable content')
                        self.screenshot(root, f'pc-{width}')
                    canvas.yview_moveto(1)
                    notebook.select(notebook.tabs()[1])
                    root.update()
                    self.assertGreater(canvas.yview()[0], 0)
                    self.screenshot(root, 'pc-android-log')
                except BaseException as error:
                    errors.append(error)
                finally:
                    root.destroy()
            root.after(600, inspect)
            return root

        with patch.dict(sys.modules, {'tkinterdnd2': None}), patch.object(tk, 'Tk', create), patch.object(port.threading.Thread, 'start'):
            port.gui()
        if errors:
            raise errors[0]

    def screenshot(self, root, name):
        if os.environ.get('PORTEMON_UI_PREVIEW'):
            from PIL import ImageGrab
            target = Path(os.environ['PORTEMON_UI_PREVIEW'])
            target.mkdir(parents=True, exist_ok=True)
            x, y = root.winfo_rootx(), root.winfo_rooty()
            ImageGrab.grab(bbox=(x, y, x + root.winfo_width(), y + root.winfo_height())).save(target / f'{name}.png')


if __name__ == '__main__':
    unittest.main()
