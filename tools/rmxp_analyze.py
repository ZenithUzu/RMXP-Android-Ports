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
    with open(path, "rb") as f:
        data = f.read()
    if data[:7] != b"RGSSAD\0":
        return None
    if data[7] != 1:
        raise ValueError("Archivo RGSSAD version %d (VX Ace) no soportado por el analizador" % data[7])
    key, i = 0xDEADCAFE, 8

    def nxt(k):
        return (k * 7 + 3) & 0xFFFFFFFF

    while i < len(data):
        n = struct.unpack_from("<I", data, i)[0] ^ key
        key = nxt(key)
        i += 4
        name = bytearray()
        for b in data[i:i + n]:
            name.append(b ^ (key & 0xFF))
            key = nxt(key)
        i += n
        size = struct.unpack_from("<I", data, i)[0] ^ key
        key = nxt(key)
        i += 4
        if name.decode("latin-1").replace("\\", "/").lower() == wanted.lower():
            chunk, tk, out = data[i:i + size], key, bytearray()
            for j in range(0, len(chunk), 4):
                word = chunk[j:j + 4]
                mask = struct.pack("<I", tk)
                out += bytes(a ^ b for a, b in zip(word, mask))
                tk = nxt(tk)
            return bytes(out)
        i += size
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


def analyze(root):
    r = {"carpeta": os.path.abspath(root), "problemas": [], "avisos": [], "info": {}}
    P, A, I = r["problemas"].append, r["avisos"].append, r["info"]

    ini = find(root, "Game.ini")
    if not ini:
        P("No hay Game.ini: esta no parece la carpeta raiz del juego.")
        return r
    cp = configparser.ConfigParser(strict=False, interpolation=None)
    with open(ini, "rb") as f:
        cp.read_string(f.read().decode("utf-8", "replace").lstrip("\ufeff"))
    game = cp["Game"] if cp.has_section("Game") else {}
    I["titulo"] = game.get("Title", "?")
    I["libreria_rgss"] = game.get("Library", "?")

    # Version de RGSS: miramos que archivos de datos existen.
    scripts, origin = None, None
    for ext, (version, archive) in RGSS.items():
        loose = find(root, "Data/Scripts." + ext)
        packed = find(root, archive)
        if loose or packed:
            I["rgss"] = version
            if loose:
                with open(loose, "rb") as f:
                    scripts, origin = f.read(), "Data/Scripts." + ext
            elif packed:
                I["archivo_cifrado"] = archive
                try:
                    scripts = rgssad_find(packed, "Data/Scripts." + ext)
                    origin = archive
                except ValueError as e:
                    A(str(e))
            break
    else:
        P("No se encontro Data/Scripts ni un archivo Game.rgssad/.rgss2a/.rgss3a.")

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
            if e == ".dll" and not fn.lower().startswith("rgss"):
                dlls.append(fn)
    I["tamano_mb"] = round(size / 1048576, 1)
    if exts[".mid"] or exts[".midi"]:
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
