"""Pruebas rápidas de la herramienta. Ejecutar: python -m unittest discover tests"""
import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "tools"))
import rmxp_port  # noqa: E402


def make_game(files):
    root = tempfile.mkdtemp()
    for name, text in files.items():
        with open(os.path.join(root, name), "w", encoding="utf-8") as f:
            f.write(text)
    return root


class GameInfo(unittest.TestCase):
    def check(self, readme, author, version="1.0"):
        root = make_game({"Game.ini": "[Game]\nTitle=Pokémon Ejemplo\n", "Léeme.txt": readme})
        info = rmxp_port.game_info(root)
        self.assertEqual(info["title"], "Pokémon Ejemplo")
        self.assertEqual(info["author"], author)
        self.assertEqual(info["version"], version)

    def test_formatos_de_autor(self):
        self.check("Creado por: Juan Pérez\n", "Juan Pérez")
        self.check("Autor - Ana\nVersión 2.1.3\n", "Ana", "2.1.3")
        self.check("A game made by TeamRocket.\nv1.4\n", "TeamRocket", "1.4")
        self.check("Developers: Foo Studio   (contacto: x@y.z)\n", "Foo Studio")
        self.check("(c) 2021 Bar Games\n", "Bar Games")
        self.check("Solo instrucciones de instalación.\n", "")

    def test_sin_leeme_usa_titulo(self):
        root = make_game({"Game.ini": "[Game]\nTitle=Mi Juego v3.0\n"})
        info = rmxp_port.game_info(root)
        self.assertEqual((info["author"], info["version"]), ("", "3.0"))


class Utilidades(unittest.TestCase):
    def test_slug(self):
        self.assertEqual(rmxp_port.slug("Pokémon Uranium!", "x"), "pokemonuranium")
        self.assertEqual(rmxp_port.slug("123 Juego", "x"), "j123juego")
        self.assertEqual(rmxp_port.slug("???", "fangame"), "fangame")

    def test_mkxp_json_con_comentarios(self):
        text = '{\n  // comentario\n  "url": "http://a//b", /* otro */\n  "lista": [1, 2,],\n}\n'
        self.assertEqual(json.loads(rmxp_port.strip_json_comments(text)),
                         {"url": "http://a//b", "lista": [1, 2]})

    def test_stage_conserva_preload_del_juego(self):
        root = make_game({"Game.ini": "[Game]\n", "Game.exe": "x",
                          "mkxp.json": '{"preloadScript": ["suyo.rb"], // nota\n "syncToRefreshrate": true}'})
        work = tempfile.mkdtemp()
        assets = rmxp_port.stage_game(root, work, {"title": "T"}, None, lambda t: None)
        game = os.path.join(assets, "game")
        conf = json.load(open(os.path.join(game, "mkxp.json"), encoding="utf-8"))
        self.assertFalse(os.path.exists(os.path.join(game, "Game.exe")))
        self.assertFalse(conf["syncToRefreshrate"])
        self.assertEqual(conf["preloadScript"][-1], "suyo.rb")
        self.assertIn("rmxp-internet.rb", conf["preloadScript"])
        self.assertTrue(os.path.exists(os.path.join(root, "Game.exe")))  # el original no se toca

    def test_mkxp_json_solo_ascii(self):
        root = make_game({"Game.ini": "[Game]\n", "mkxp.json": '{"windowTitle": "Pokémon Ñandú"}'})
        assets = rmxp_port.stage_game(root, tempfile.mkdtemp(), {"title": "T"}, None, lambda t: None)
        raw = open(os.path.join(assets, "game", "mkxp.json"), "rb").read()
        self.assertTrue(raw.isascii())
        self.assertEqual(json.loads(raw)["windowTitle"], "Pokémon Ñandú")

    def test_usa_soundfont_del_juego(self):
        root = make_game({"Game.ini": "[Game]\n"})
        os.makedirs(os.path.join(root, "Audio"))
        open(os.path.join(root, "Audio", "musica.sf2"), "w").close()
        assets = rmxp_port.stage_game(root, tempfile.mkdtemp(), {"title": "T"}, None, lambda t: None)
        conf = json.load(open(os.path.join(assets, "game", "mkxp.json"), encoding="utf-8"))
        self.assertEqual(conf["midiSoundFont"], "Audio/musica.sf2")


if __name__ == "__main__":
    unittest.main()
