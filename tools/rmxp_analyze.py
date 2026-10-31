#!/usr/bin/env python3
"""
rmxp_analyze.py - Revisa un juego de RPG Maker XP/VX/VX Ace (por ejemplo un
fangame de Pokemon Essentials) y avisa que cosas pueden fallar al portarlo a
Android con mkxp-z.

No cambia nada del juego: solo lee y genera un reporte.

Uso:
    python tools/rmxp_analyze.py "C:/Juegos/Mi Fangame"
    python tools/rmxp_analyze.py carpeta_del_juego --json   (reporte en JSON)

Solo usa la libreria estandar de Python 3.8+ (no hay que instalar nada).
"""
import configparser
import json
import os
import re
import struct
import sys
import zlib
from collections import Counter

# --------------------------------------------------------------------------
# 1) Lector minimo del formato Marshal de Ruby.
#    Scripts.rxdata es un Array de [id, "nombre", "codigo comprimido con zlib"].
#    Solo soportamos lo que aparece en ese archivo.
# --------------------------------------------------------------------------


class Marshal:
    def __init__(self, data):
        self.d, self.i = data, 2  # los 2 primeros bytes son la version (4.8)
        self.symbols, self.objects = [], []

    def byte(self):
        b = self.d[self.i]
        self.i += 1
        return b

    def long(self):
        c = struct.unpack("b", bytes([self.byte()]))[0]
        if c == 0:
            return 0
        if 4 < c < 128:
            return c - 5
        if -129 < c < -4:
            return c + 5
        n = 0
        for k in range(abs(c)):
            n |= self.byte() << (8 * k)
        if c < 0:
            n -= 1 << (8 * -c)
        return n

    def raw(self):
        n = self.long()
        s = self.d[self.i:self.i + n]
        self.i += n
        return s

    def read(self):
        t = chr(self.byte())
        if t == "0":
            return None
        if t == "T":
            return True
        if t == "F":
            return False
        if t == "i":
            return self.long()
        if t == ":":
            s = self.raw().decode("utf-8", "replace")
            self.symbols.append(s)
            return s
        if t == ";":
            return self.symbols[self.long()]
        if t == "@":
            return self.objects[self.long()]
        if t == '"':
            s = self.raw()
            self.objects.append(s)
            return s
        if t == "I":  # objeto con variables extra (ej. la codificacion del texto)
            obj = self.read()
            for _ in range(self.long()):
                self.read()
                self.read()
            return obj
        if t == "[":
            arr = []
            self.objects.append(arr)
            for _ in range(self.long()):
                arr.append(self.read())
            return arr
        raise ValueError("Tipo Marshal no soportado: %r" % t)


def read_scripts(data):
    """Devuelve una lista de (nombre, codigo) a partir de Scripts.rxdata."""
    out = []
    for entry in Marshal(data).read():
        name = entry[1].decode("utf-8", "replace") if entry[1] else ""
        try:
            code = zlib.decompress(entry[2]).decode("utf-8", "replace")
        except Exception:
            code = ""
        out.append((name, code))
    return out


# --------------------------------------------------------------------------
# 2) Lector del archivo cifrado Game.rgssad / .rgss2a (XP y VX).
#    Solo sacamos Scripts para analizarlo; mkxp-z lee el archivo tal cual.
# --------------------------------------------------------------------------


def rgssad_find(path, wanted):
    """Read just the requested entry from RGSSAD v1/v3; leave the archive untouched."""
    total = os.path.getsize(path)
    with open(path, "rb") as stream:
        def read(count):
            if count < 0 or count > total - stream.tell():
                raise ValueError("Archivo RGSSAD truncado")
            value = stream.read(count)
            if len(value) != count:
                raise ValueError("Archivo RGSSAD truncado")
            return value

        def uint():
            return struct.unpack("<I", read(4))[0]

        def advance(key):
            return (key * 7 + 3) & 0xFFFFFFFF

        def content(offset, size, key):
            if offset > total or size > total - offset or size > 256 * 1024 * 1024:
                raise ValueError("Entrada de scripts RGSSAD inválida o demasiado grande")
            stream.seek(offset)
            chunk = read(size)
            out = bytearray()
            for pos in range(0, size, 4):
                out.extend(a ^ b for a, b in zip(chunk[pos:pos + 4], struct.pack("<I", key)))
                key = advance(key)
            return bytes(out)

        header = read(8)
        if header[:7] != b"RGSSAD\0" or header[7] not in (1, 3):
            raise ValueError("Cabecera o versión RGSSAD inválida")
        version = header[7]
        key = ((uint() * 9 + 3) & 0xFFFFFFFF) if version == 3 else 0xDEADCAFE
        while stream.tell() < total:
            if version == 3:
                offset = uint() ^ key
                if offset == 0:
                    return None
                size, filekey, length = uint() ^ key, uint() ^ key, uint() ^ key
                if length > 65536:
                    raise ValueError("Nombre RGSSAD demasiado largo")
                mask = struct.pack("<I", key)
                name = bytes(value ^ mask[i % 4] for i, value in enumerate(read(length)))
            else:
                length = uint() ^ key
                key = advance(key)
                if length > 65536:
                    raise ValueError("Nombre RGSSAD demasiado largo")
                name = bytearray()
                for value in read(length):
                    name.append(value ^ (key & 0xFF))
                    key = advance(key)
                size = uint() ^ key
                key = advance(key)
                offset, filekey = stream.tell(), key
            if offset > total or size > total - offset:
                raise ValueError("Entrada RGSSAD fuera del archivo")
            if name.decode("utf-8", "replace").replace("\\", "/").lower() == wanted.lower():
                return content(offset, size, filekey)
            if version == 1:
                stream.seek(offset + size)
        return None


# --------------------------------------------------------------------------
# 3) El analisis
# --------------------------------------------------------------------------

RGSS = {  # extension de datos -> (version, archivo cifrado)
    "rxdata": ("RGSS1 (RPG Maker XP)", "Game.rgssad"),
    "rvdata": ("RGSS2 (RPG Maker VX)", "Game.rgss2a"),
    "rvdata2": ("RGSS3 (RPG Maker VX Ace)", "Game.rgss3a"),
}

WIN32API_RE = re.compile(r"Win32API\.new\(\s*['\"]([^'\"]+)['\"]\s*,\s*['\"]([^'\"]+)['\"]")
OTHER_NATIVE_RE = re.compile(r"\b(DL\.dlopen|Fiddle|WIN32OLE|require\s+['\"]win32)")
ESS_VERSION_RE = re.compile(r"ESSENTIALS_VERSION\s*=\s*['\"]([^'\"]+)['\"]")


def find(root, rel):
    """Busca un archivo sin importar mayusculas/minusculas (Android si distingue)."""
    cur = root
    for part in rel.split("/"):
        if not os.path.isdir(cur):
            return None
        match = [e for e in os.listdir(cur) if e.lower() == part.lower()]
        if not match:
            return None
        cur = os.path.join(cur, match[0])
    return cur


ENGINES = {"xp": (1, "RPG Maker XP", "rxdata", "Game.rgssad"),
           "vx": (2, "RPG Maker VX", "rvdata", "Game.rgss2a"),
           "ace": (3, "RPG Maker VX Ace", "rvdata2", "Game.rgss3a")}


def engine_info(root, choice="auto"):
    """Resolve one supported RGSS runtime and a usable script source, without changing files."""
    # MV Windows exports keep the web game in www/; web exports use the root.
    for prefix in ("", "www/"):
        if all(os.path.isfile(os.path.join(root, prefix + name)) for name in
               ("index.html", "js/rpg_core.js", "js/rpg_managers.js", "data/System.json")):
            if choice in ("auto", "mv"):
                return {"id": "mv", "nombre": "RPG Maker MV (experimental)",
                        "rgssVersion": 0, "scripts": "", "archivo": None, "webRoot": prefix}
    if choice == "mv":
        raise ValueError("MV necesita index.html, js/rpg_core.js, js/rpg_managers.js y data/System.json en la raíz o www/.")
    if choice != "auto" and choice not in ENGINES:
        raise ValueError("Motor desconocido: " + str(choice))
    ini = find(root, "Game.ini")
    if not ini:
        raise ValueError("Falta Game.ini. Se admiten RPG Maker XP, VX y VX Ace.")
    cp = configparser.ConfigParser(strict=False, interpolation=None)
    try:
        with open(ini, "rb") as stream:
            cp.read_string(stream.read().decode("utf-8", "replace").lstrip("\ufeff"))
    except configparser.Error as error:
        raise ValueError("Game.ini inválido: " + str(error)) from error
    section = next((name for name in cp.sections() if name.lower() == "game"), None)
    game = cp[section] if section else {}
    declared = game.get("scripts", "").strip().strip('"').replace("\\", "/")
    while declared.startswith("./"):
        declared = declared[2:]
    if declared.startswith("/") or ":" in declared or ".." in declared.split("/"):
        raise ValueError("Game.ini contiene una ruta Scripts fuera de la carpeta del juego.")
    script_kind = next((key for key, value in ENGINES.items() if declared.lower().endswith("." + value[2])), None)
    library = game.get("library", "").strip().strip('"').replace("\\", "/").rsplit("/", 1)[-1].lower()
    library_kind = "ace" if library.startswith("rgss3") else "vx" if library.startswith("rgss2") else "xp" if library.startswith("rgss1") else None
    present = {key for key, value in ENGINES.items()
               if find(root, "Data/Scripts." + value[2]) or find(root, value[3])}
    if declared and find(root, declared):
        if script_kind:
            present.add(script_kind)
        elif library_kind:
            present.add(library_kind)
    selected = choice
    if selected == "auto":
        if script_kind in present:
            selected = script_kind
        elif len(present) == 1:
            selected = next(iter(present))
        elif library_kind in present:
            selected = library_kind
        elif present:
            raise ValueError("Hay datos de varios motores. Elige XP, VX o VX Ace en Motor del juego.")
        else:
            raise ValueError("No se encontraron scripts ni archivos RGSS de XP, VX o VX Ace.")
    version, name, extension, archive = ENGINES[selected]
    scripts = declared if declared and (script_kind is None or script_kind == selected) else "Data/Scripts." + extension
    loose = find(root, scripts)
    if not loose:
        standard = find(root, "Data/Scripts." + extension)
        if standard:
            loose = standard
    packed = find(root, archive)
    if loose and not os.path.isfile(loose):
        raise ValueError("La ruta de scripts es una carpeta, no un archivo: " + scripts)
    if packed and not os.path.isfile(packed):
        raise ValueError("El archivo cifrado es una carpeta: " + archive)
    if not loose and not packed:
        raise ValueError("%s necesita %s o %s; no se encontraron." % (name, scripts, archive))
    if loose:
        scripts = os.path.relpath(loose, root).replace(os.sep, "/")
    return {"id": selected, "nombre": name, "rgssVersion": version, "scripts": scripts,
            "archivo": archive if packed else None}


def analyze(root, game_engine="auto"):
    r = {"carpeta": os.path.abspath(root), "problemas": [], "avisos": [], "info": {}}
    P, A, I = r["problemas"].append, r["avisos"].append, r["info"]

    try:
        engine = engine_info(root, game_engine)
        if engine["id"] == "mv":
            I["motor"] = "mv"
            I["runtime"] = "Android System WebView"
            A("MV experimental: requiere Android System WebView actualizado. Plugins de Node.js/NW.js y funciones en línea no están soportados.")
            r["veredicto"] = "RPG Maker MV · experimental"
            return r
    except (ValueError, OSError) as error:
        P(str(error))
        r["veredicto"] = "No se pudo detectar el motor"
        return r
    ini = find(root, "Game.ini")
    if not ini:
        P("No hay Game.ini: esta no parece la carpeta raiz del juego.")
        return r
    cp = configparser.ConfigParser(strict=False, interpolation=None)
    try:
        with open(ini, "rb") as f:
            cp.read_string(f.read().decode("utf-8", "replace").lstrip("\ufeff"))
    except (configparser.Error, OSError) as error:
        P("Game.ini inválido: " + str(error))
        r["veredicto"] = "No se pudo analizar el juego"
        return r
    section = next((name for name in cp.sections() if name.lower() == "game"), None)
    game = cp[section] if section else {}
    I["titulo"] = game.get("Title", "?")
    I["libreria_rgss"] = game.get("Library", "?")

    scripts, origin = None, None
    try:
        engine = engine_info(root, game_engine)
        I["rgss"] = "RGSS%d (%s)" % (engine["rgssVersion"], engine["nombre"])
        I["motor"] = engine["id"]
        loose = find(root, engine["scripts"])
        if loose:
            with open(loose, "rb") as stream:
                scripts, origin = stream.read(), engine["scripts"]
        elif engine["archivo"]:
            I["archivo_cifrado"] = engine["archivo"]
            origin = engine["archivo"]
            scripts = rgssad_find(find(root, origin), engine["scripts"])
            if scripts is None:
                P("No se encontró %s dentro de %s." % (engine["scripts"], origin))
    except (ValueError, OSError, configparser.Error) as error:
        P(str(error))

    # Scripts: Win32API, version de Essentials, etc.
    if scripts:
        try:
            lst = read_scripts(scripts)
        except Exception as e:
            lst = []
            P("No se pudo leer %s (%s)." % (origin, e))
        I["scripts"] = len(lst)
        calls, natives, ess = Counter(), Counter(), None
        for name, code in lst:
            for dll, fn in WIN32API_RE.findall(code):
                calls["%s!%s" % (dll.lower().replace(".dll", ""), fn)] += 1
            for m in OTHER_NATIVE_RE.findall(code):
                natives[m] += 1
            ess = ess or (ESS_VERSION_RE.search(code) or [None, None])[1]
        names = {n for n, _ in lst}
        if ess:
            I["essentials"] = ess
        elif any(n.startswith(("PokeBattle_", "PField_", "PSystem_")) for n in names):
            I["essentials"] = "antiguo (v18 o anterior)"
        if calls:
            I["win32api"] = dict(calls.most_common())
            P("Usa Win32API (%d llamadas distintas). En Android no hay DLLs de Windows: "
              "cada una necesita un reemplazo (parche .rb) o el juego dara error." % len(calls))
        if natives:
            P("Usa otras librerias nativas de Windows: %s." % ", ".join(natives))
        if I.get("essentials", "").startswith("antiguo"):
            A("Essentials antiguo: suele leer el teclado con Win32API y no funciona en "
              "mkxp-z sin parches. Desde Essentials v20 el motor oficial ya es mkxp-z.")

    # Archivos que Android/mkxp-z no reproduce.
    exts, size = Counter(), 0
    dlls = []
    for d, _, files in os.walk(root):
        for fn in files:
            e = os.path.splitext(fn)[1].lower()
            exts[e] += 1
            size += os.path.getsize(os.path.join(d, fn))
            low = fn.lower()
            if e == ".dll" and "ruby" in low:
                I["motor_pc"] = "mkxp-z (buena señal: es el mismo motor que usa la app)"
            elif e == ".dll" and not low.startswith("rgss") and low != "zlib1.dll":
                dlls.append(fn)
    I["tamano_mb"] = round(size / 1048576, 1)
    if exts[".sf2"]:
        I["soundfont"] = "el juego trae su propio SoundFont: la música MIDI va a sonar"
    elif exts[".mid"] or exts[".midi"]:
        A("Tiene %d archivos MIDI: hay que poner un SoundFont (.sf2) y configurar "
          "\"midiSoundFont\" en mkxp.json, si no, no habra musica." % (exts[".mid"] + exts[".midi"]))
    for bad, why in ((".wma", "audio WMA"), (".avi", "video AVI"), (".wmv", "video WMV"), (".mpg", "video MPG")):
        if exts[bad]:
            P("Tiene %d archivo(s) %s, que mkxp-z no reproduce (convertir a .ogg/.ogv)." % (exts[bad], why))
    if dlls:
        A("DLLs extra de Windows (no se pueden usar en Android): %s" % ", ".join(sorted(set(dlls))))
    if not find(root, "Fonts"):
        A("No hay carpeta Fonts: Android no trae las fuentes de Windows; "
          "copia las .ttf que use el juego a Fonts/.")
    if size > 1.9 * 1024 ** 3:
        P("El juego pesa mas de ~2 GB: no cabe en un APK normal (hay que usar OBB).")
    elif size > 150 * 1048576:
        A("Pesa %.0f MB: Google Play no acepta APKs de mas de 150 MB (para instalar a mano no hay problema)." % (size / 1048576))

    r["veredicto"] = ("Probablemente NO funciona sin parches" if r["problemas"]
                      else "Probablemente funciona" if not r["avisos"]
                      else "Probablemente funciona, revisar avisos")
    return r


def print_report(r):
    print("Juego: %s" % r["info"].get("titulo", "?"))
    print("Carpeta: %s" % r["carpeta"])
    for k, v in r["info"].items():
        if k not in ("titulo", "win32api"):
            print("  %-16s %s" % (k + ":", v))
    if "win32api" in r["info"]:
        print("  Llamadas Win32API (dll!funcion: veces):")
        for k, v in r["info"]["win32api"].items():
            print("    %-40s %d" % (k, v))
    for title, key in (("PROBLEMAS", "problemas"), ("AVISOS", "avisos")):
        if r[key]:
            print("\n%s:" % title)
            for x in r[key]:
                print("  - " + x)
    print("\nVeredicto: %s" % r.get("veredicto", "no se pudo analizar"))


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if a != "--json"]
    if len(args) != 1:
        print(__doc__)
        sys.exit(1)
    rep = analyze(args[0])
    if "--json" in sys.argv:
        print(json.dumps(rep, ensure_ascii=False, indent=2))
    else:
        print_report(rep)
    sys.exit(1 if rep["problemas"] else 0)
