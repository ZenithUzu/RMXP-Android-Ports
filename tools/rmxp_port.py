#!/usr/bin/env python3
"""
rmxp_port.py - Convierte un juego de RPG Maker XP/VX/VX Ace (fangames de Pokémon
Essentials, por ejemplo) en un APK de Android nativo, con la app de este repo.

Uso normal (Windows): arrastra la carpeta del juego sobre Portar.bat, o abre
Portar.bat y elige la carpeta.

Sin interfaz (para pruebas):
    python tools/rmxp_port.py "C:/Juegos/Mi Fangame" --sin-interfaz [--instalar]

Qué hace:
 1. Revisa el juego (tools/rmxp_analyze.py) y lee el autor y la versión del Léeme.
 2. Copia el juego a una carpeta de trabajo (nunca modifica el original), quita
    los .exe/.dll y agrega los parches de android/patches.
 3. Crea (solo la primera vez) una firma propia del juego a nombre de su autor y
    la guarda en %USERPROFILE%/RMXP-Android-Ports/juegos/. Con la misma firma,
    cada versión nueva se instala encima de la anterior SIN borrar partidas.
 4. Compila el APK con Gradle (Android Studio) usando el motor ya compilado.
 5. Si se pide, lo instala con adb. Nunca desinstala nada.

Necesita Python 3.8+ y Android Studio (trae el SDK, Java y adb).
"""
import argparse
import json
import os
import queue
import re
import secrets
import shutil
import subprocess
import sys
import threading
import time
import unicodedata
import urllib.request
import zipfile
from contextlib import contextmanager
from functools import wraps

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rmxp_analyze  # noqa: E402

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ANDROID = os.path.join(REPO, "android")
HOME = os.path.join(os.path.expanduser("~"), "RMXP-Android-Ports")
# Motor ya compilado (librerías .so). Se publica con .github/workflows/motor.yml.
MOTOR_VERSION = "motor-v13"
MOTOR_URL = "https://github.com/ZenithUzu/RMXP-Android-Ports/releases/download/%s/motor-android.zip" % MOTOR_VERSION
WINDOWS = os.name == "nt"


class PortError(Exception):
    """Error con un mensaje para la persona, en español."""


# --------------------------------------------------------------------------
# Datos del juego: título, autor (del Léeme) y versión
# --------------------------------------------------------------------------

README_RE = re.compile(r"^(read ?me|l[eé]e ?me|l[eé]ame|cr[eé]ditos|credits|info|instrucciones|leia-me"
                       r"|change ?log|cambios|novedades|versi[oó]n|notas)", re.I)
# Líneas que hablan del motor y no del juego ("Hecho con Essentials v20.1"): no dan autor ni versión
ENGINE_RE = re.compile(r"essentials|rpg ?maker|mkxp|joiplay|kirin|\brgss|\bruby\b", re.I)
# Carpetas del juego donde no hay textos (no se revisan)
GAME_DIRS = {"graphics", "audio", "data", "fonts", "plugins", "save", "saves"}
AUTHOR_RES = [
    re.compile(r"^\W*(?:(?:an?|un)\s+(?:game|juego|fangame)\s+)?(?:hecho|creado|desarrollado|programado|made|created|developed|programmed)\s+(?:por|by)\s*[:\-]?\s*(.+)$", re.I),
    re.compile(r"^\W*(?:autor(?:es|a)?|author(?:s)?|creador(?:es|a)?|creator(?:s)?|desarrollador(?:es|a)?|developer(?:s)?|dev)\s*[:\-]\s*(.+)$", re.I),
    re.compile(r"(?:©|\(c\)|copyright)\s*(?:\d{4}(?:\s*-\s*\d{4})?\s*)?(?:by\s+)?([^\d].+)$", re.I),
]
VERSION_RE = re.compile(r"\b(?:v|ver\.?|versi[oó]n|version)\s*([0-9]+(?:\.[0-9]+){0,3})", re.I)


def read_text(path):
    with open(path, "rb") as f:
        raw = f.read(200000)
    for enc in ("utf-8-sig", "cp1252", "latin-1"):
        try:
            text = raw.decode(enc)
            break
        except UnicodeDecodeError:
            continue
    if text.lstrip().startswith("{\\rtf"):  # quita los códigos de formato de un .rtf
        text = re.sub(r"\\par[d]?", "\n", text)
        text = re.sub(r"\\'[0-9a-f]{2}|\\[a-z]+-?\d* ?|[{}]", "", text)
    return text


def game_info(root):
    """Devuelve dict con title, author, version leídos del juego."""
    info = {"title": os.path.basename(os.path.normpath(root)), "author": "", "version": ""}
    ini = rmxp_analyze.find(root, "Game.ini")
    if ini:
        m = re.search(r"^\s*Title\s*=\s*(.+?)\s*$", read_text(ini), re.M | re.I)
        if m:
            info["title"] = m.group(1)
    for path in readme_files(root):
        text = read_text(path)
        for line in text.splitlines():
            line = line.strip()
            if ENGINE_RE.search(line):
                continue
            if not info["author"]:
                for rx in AUTHOR_RES:
                    m = rx.search(line)
                    if m:
                        author = re.split(r"\s{2,}|\s[-|]\s|[.;]$", m.group(1).strip())[0].strip(" .,:;-")
                        if 1 < len(author) <= 60:
                            info["author"] = author
                            break
            if not info["version"]:
                m = VERSION_RE.search(line)
                if m:
                    info["version"] = m.group(1)
    if not info["version"]:
        m = VERSION_RE.search(info["title"])
        info["version"] = m.group(1) if m else "1.0"
    return info


def readme_files(root):
    """Léeme, créditos, cambios... en la carpeta del juego y un nivel más adentro (ej. Docs/)."""
    found = []
    for name in sorted(os.listdir(root)):
        path = os.path.join(root, name)
        if os.path.isfile(path) and README_RE.match(name):
            found.append(path)
    for name in sorted(os.listdir(root)):
        path = os.path.join(root, name)
        if os.path.isdir(path) and name.lower() not in GAME_DIRS:
            found += [os.path.join(path, f) for f in sorted(os.listdir(path))
                      if README_RE.match(f) and os.path.isfile(os.path.join(path, f))]
    return found


def slug(text, default):
    ascii_text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode()  # é -> e
    s = re.sub(r"[^a-z0-9]+", "", ascii_text.lower())
    if not s:
        return default
    return s if s[0].isalpha() else "j" + s


# --------------------------------------------------------------------------
# Herramientas: SDK de Android, Java, adb
# --------------------------------------------------------------------------

def find_sdk():
    for p in (os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT"),
              os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk"),
              os.path.join(os.path.expanduser("~"), "Android", "Sdk"),
              os.path.join(os.path.expanduser("~"), "Library", "Android", "sdk")):
        if p and os.path.isdir(os.path.join(p, "platforms")):
            return p
    raise PortError("No encontré el SDK de Android. Instala Android Studio, ábrelo una vez "
                    "para que descargue el SDK, y vuelve a intentar.")


def find_java():
    """Java 17+ para Gradle: JAVA_HOME o el que trae Android Studio (jbr)."""
    cands = [os.environ.get("JAVA_HOME")]
    for base in (os.environ.get("ProgramFiles", r"C:\Program Files"), os.environ.get("LOCALAPPDATA", "")):
        cands += [os.path.join(base, "Android", "Android Studio", "jbr"),
                  os.path.join(base, "Programs", "Android Studio", "jbr")]
    cands += ["/opt/android-studio/jbr", "/Applications/Android Studio.app/Contents/jbr/Contents/Home"]
    exe = "java.exe" if WINDOWS else "java"
    for c in cands:
        if c and os.path.isfile(os.path.join(c, "bin", exe)):
            return c
    if shutil.which("java"):
        return None  # ya está en el PATH
    raise PortError("No encontré Java. Instala Android Studio (trae Java) o define JAVA_HOME.")


def tool(java_home, name):
    exe = name + (".exe" if WINDOWS else "")
    if java_home:
        return os.path.join(java_home, "bin", exe)
    return shutil.which(name) or exe


def run(cmd, log, cwd=None, env=None):
    """Ejecuta un comando mostrando su salida; devuelve (código, salida)."""
    log("> " + " ".join(cmd if len(cmd) < 8 else cmd[:4] + ["..."]))
    p = subprocess.Popen(cmd, cwd=cwd, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                         text=True, encoding="utf-8", errors="replace",
                         creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    out = []
    for line in p.stdout:
        out.append(line)
        line = line.rstrip()
        if line and not line.startswith(("> Task", "\tat ")):
            log("  " + line)
    return p.wait(), "".join(out)


# --------------------------------------------------------------------------
# Pasos
# --------------------------------------------------------------------------

def load_project(info, log):
    """Datos que deben ser iguales en cada versión del juego: id y firma."""
    key = slug(info["title"], "juego")
    folder = os.path.join(HOME, "juegos", key)
    os.makedirs(folder, exist_ok=True)
    path = os.path.join(folder, "juego.json")
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            project = json.load(f)
        log("Juego conocido: se usa la misma firma de antes (%s)." % project["id"])
    else:
        project = {"id": "com.%s.%s" % (slug(info["author"], "fangame"), key),
                   "password": secrets.token_urlsafe(18), "author": info["author"]}
    project["folder"] = folder
    project["keystore"] = os.path.join(folder, "firma.jks")
    return project, path


def save_project(project, path):
    data = {k: v for k, v in project.items() if k not in ("folder", "keystore")}
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def make_keystore(project, info, java_home, log):
    if os.path.exists(project["keystore"]):
        return

    def dn(value):  # comas y signos especiales deben ir escapados en el nombre del certificado
        return re.sub(r'([,+"\\<>;=])', r"\\\1", value or "Desconocido")

    log("Creando la firma del juego a nombre de: %s" % (info["author"] or "Desconocido"))
    code, out = run([tool(java_home, "keytool"), "-genkeypair", "-keystore", project["keystore"],
                     "-storetype", "PKCS12", "-alias", "juego", "-keyalg", "RSA", "-keysize", "2048",
                     "-validity", "36500", "-storepass", project["password"], "-keypass", project["password"],
                     "-dname", "CN=%s, O=%s, OU=Fangame" % (dn(info["author"]), dn(info["title"]))], log)
    if code != 0:
        raise PortError("No se pudo crear la firma (keytool). Revisa el registro de arriba.")


def get_motor(log, motor_dir=None):
    """Carpeta lib/ con las librerías del motor por ABI."""
    if motor_dir:
        return motor_dir
    dest = os.path.join(HOME, "motor", MOTOR_VERSION)
    lib = os.path.join(dest, "lib")
    if os.path.isdir(os.path.join(lib, "arm64-v8a")):
        return lib
    log("Descargando el motor (%s), solo la primera vez..." % MOTOR_VERSION)
    os.makedirs(dest, exist_ok=True)
    tmp = os.path.join(dest, "motor.zip.part")
    try:
        urllib.request.urlretrieve(MOTOR_URL, tmp)
        with zipfile.ZipFile(tmp) as z:
            z.extractall(dest)
    except Exception as e:
        raise PortError("No se pudo descargar el motor desde %s (%s). Revisa tu internet, "
                        "o usa --motor con una carpeta lib/ ya compilada." % (MOTOR_URL, e))
    finally:
        if os.path.exists(tmp):
            os.remove(tmp)
    return lib


def strip_json_comments(text):
    """mkxp.json admite comentarios //; los quitamos sin tocar textos entre comillas."""
    out, i, in_str = [], 0, False
    while i < len(text):
        c = text[i]
        if in_str:
            out.append(c)
            if c == "\\":
                out.append(text[i + 1:i + 2])
                i += 1
            elif c == '"':
                in_str = False
        elif c == '"':
            in_str = True
            out.append(c)
        elif text.startswith("//", i):
            while i < len(text) and text[i] != "\n":
                i += 1
            continue
        elif text.startswith("/*", i):
            end = text.find("*/", i + 2)
            i = len(text) if end < 0 else end + 2
            out.append(" ")
            continue
        else:
            out.append(c)
        i += 1
    text, out, i, in_str = "".join(out), [], 0, False
    while i < len(text):
        c = text[i]
        if in_str:
            out.append(c)
            if c == "\\":
                out.append(text[i + 1:i + 2])
                i += 1
            elif c == '"':
                in_str = False
        elif c == '"':
            in_str = True
            out.append(c)
        elif c == ',':
            j = i + 1
            while j < len(text) and text[j].isspace(): j += 1
            if j == len(text) or text[j] not in "}]": out.append(c)
        else:
            out.append(c)
        i += 1
    return "".join(out)


def find_soundfont(game):
    """Primer .sf2 dentro del juego (el de la carpeta principal primero), como ruta relativa."""
    for d, _, files in sorted(os.walk(game), key=lambda w: (w[0].count(os.sep), w[0])):
        for f in sorted(files):
            if f.lower().endswith(".sf2"):
                return os.path.relpath(os.path.join(d, f), game).replace(os.sep, "/")
    return None


def largo(path):
    """Windows no deja pasar de 260 caracteres en una ruta, salvo con un prefijo especial.
    Los juegos con carpetas de scripts profundas (Data/Scripts/...) llegan a ese límite."""
    if os.name == "nt" and not path.startswith("\\\\?\\"):
        return "\\\\?\\" + os.path.abspath(path)
    return path


def stage_game(root, work, info, soundfont, log, ruby_lib=None, ligero=False):
    """Copia el juego a work/assets/game, sin .exe/.dll, con los parches y mkxp.json."""
    assets = os.path.join(work, "assets")
    game = os.path.join(assets, "game")
    if os.path.exists(assets):
        shutil.rmtree(largo(assets))
    log("Copiando el juego (el original no se toca)...")
    shutil.copytree(largo(root), largo(game), ignore=shutil.ignore_patterns(
        "*.exe", "*.dll", "*.EXE", "*.DLL", "desktop.ini", "Thumbs.db", "*.url", "*.URL", "*.lnk"))

    patches = sorted(f for f in os.listdir(os.path.join(ANDROID, "patches")) if f.endswith(".rb"))
    for f in patches:
        shutil.copy2(os.path.join(ANDROID, "patches", f), game)

    conf = {}
    existing = rmxp_analyze.find(game, "mkxp.json")
    if existing:
        try:
            conf = json.loads(strip_json_comments(read_text(existing)))
        except ValueError:
            log("Aviso: el mkxp.json del juego tiene errores; se usa uno nuevo.")
        os.remove(existing)
    conf.setdefault("windowTitle", info["title"])
    conf.setdefault("subImageFix", True)
    conf.update({
        "fullscreen": True,
        # En RPG Maker, más fps = juego más rápido. Nunca seguir los Hz de la pantalla.
        "syncToRefreshrate": False,
        "preloadScript": patches + [p for p in conf.get("preloadScript", []) if p not in patches],
    })
    if ruby_lib and os.path.isdir(ruby_lib):
        # Biblioteca de Ruby (net/http, socket...) para juegos con funciones en línea
        shutil.copytree(ruby_lib, os.path.join(game, "rmxp_ruby"))
        # "." = la carpeta del juego: el motor deja de buscar ahí si hay rutas propias
        extra = [p for p in conf.get("rubyLoadpath", []) if p != "rmxp_ruby"] or ["."]
        conf["rubyLoadpath"] = ["rmxp_ruby"] + extra
    if soundfont:
        shutil.copy2(soundfont, os.path.join(game, "soundfont.sf2"))
        conf["midiSoundFont"] = "soundfont.sf2"
    elif not conf.get("midiSoundFont"):
        own = find_soundfont(game)
        if own:
            conf["midiSoundFont"] = own
            log("Música MIDI: se usa el SoundFont del juego (%s)." % own)
    with open(os.path.join(game, "mkxp.json"), "w", encoding="utf-8") as f:
        # Solo ASCII: así el motor no intenta adivinar la codificación del archivo (se caía con algunas)
        json.dump(conf, f, ensure_ascii=True, indent=2)
    if ligero:
        write_ligero(game, log)
    return assets


def write_ligero(game, log):
    """Ahorrar espacio: lista de Graphics/ y Audio/ que se quedan dentro del APK (el motor
    los lee de ahí). Solo nombres ASCII: algunos scripts abren los de otros nombres con File.open."""
    names = []
    for top in os.listdir(game):
        if top.lower() not in ("graphics", "audio") or not os.path.isdir(os.path.join(game, top)):
            continue
        for d, _, files in os.walk(largo(os.path.join(game, top))):
            for f in files:
                rel = os.path.relpath(os.path.join(d, f), largo(game)).replace(os.sep, "/")
                # El SoundFont lo abre el motor por su ruta real: se queda fuera del APK
                if rel.isascii() and "\n" not in rel and not rel.lower().endswith(".sf2"):
                    names.append(rel)
    with open(os.path.join(game, "rmxp_ligero.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(sorted(names)) + "\n")
    log("Ahorrar espacio: %d archivos de Graphics y Audio se leerán directo del APK." % len(names))


# Fondo del icono (lo que se ve alrededor del dibujo en la pantalla de inicio)
FONDOS_ICONO = {"gris": (0x5F, 0x63, 0x68), "negro": (0, 0, 0), "blanco": (0xFF, 0xFF, 0xFF)}


def png_color(path, rgb, size=108):
    """PNG de un solo color, sin librerías extra."""
    import struct
    import zlib

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    row = b"\x00" + bytes(rgb) * size
    data = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(row * size)) + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(data)


def make_icon_res(icon, work, fondo="gris"):
    """Recursos con el icono elegido (PNG) y su fondo para la variante release."""
    res = os.path.join(work, "res")
    if os.path.exists(res):
        shutil.rmtree(res)
    if not icon:
        return None
    os.makedirs(os.path.join(res, "drawable-nodpi"))
    shutil.copy2(icon, os.path.join(res, "drawable-nodpi", "rmxp_game_icon.png"))
    # El fondo es un PNG (no un color): así la app del teléfono lo puede cambiar igual
    png_color(os.path.join(res, "drawable-nodpi", "rmxp_icon_fondo.png"), FONDOS_ICONO.get(fondo, FONDOS_ICONO["gris"]))
    for d in ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"):
        os.makedirs(os.path.join(res, "mipmap-" + d))
        shutil.copy2(icon, os.path.join(res, "mipmap-" + d, "ic_launcher.png"))
    os.makedirs(os.path.join(res, "mipmap-anydpi-v26"))
    with open(os.path.join(res, "mipmap-anydpi-v26", "ic_launcher.xml"), "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n'
                '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                '    <background android:drawable="@drawable/rmxp_icon_fondo" />\n'
                '    <foreground><inset android:drawable="@drawable/rmxp_game_icon" android:inset="20%" /></foreground>\n'
                '</adaptive-icon>\n')
    return res


def gradle_build(project, info, assets, res, motor, sdk, java_home, log):
    with open(os.path.join(ANDROID, "local.properties"), "w", encoding="utf-8") as f:
        f.write("sdk.dir=%s\n" % sdk.replace("\\", "\\\\").replace(":", "\\:"))
    env = dict(os.environ)
    if java_home:
        env["JAVA_HOME"] = java_home
    file_name = slug(info["title"], "juego")
    # El número de versión siempre crece: minutos desde 2020. Android exige que no baje.
    version_code = int((time.time() - 1577836800) // 60)
    args = ["-PgameId=" + project["id"], "-PgameName=" + info["title"],
            "-PgameVersionName=" + info["version"], "-PgameVersionCode=%d" % version_code,
            "-PgameFile=" + file_name, "-PgameAssets=" + assets, "-PprebuiltLibs=" + motor,
            "-PtargetAbis=" + ",".join(sorted(os.listdir(motor))), "-PkeyStore=" + project["keystore"],
            "-PkeyPassword=" + project["password"], "-PkeyAlias=juego"]
    if res:
        args.append("-PgameRes=" + res)
    gradlew = os.path.join(ANDROID, "gradlew.bat" if WINDOWS else "gradlew")
    log("Compilando el APK con Gradle (la primera vez descarga cosas y tarda unos minutos)...")
    code, out = run([gradlew, "assembleRelease", "--console=plain"] + args, log, cwd=ANDROID, env=env)
    if code != 0:
        raise PortError("Gradle no pudo compilar el APK. Revisa el registro de arriba.")
    outputs = os.path.join(ANDROID, "app", "build", "outputs", "apk", "release")
    # Con varias arquitecturas sale un APK "universal" que sirve en cualquier teléfono
    apks = sorted((f for f in os.listdir(outputs) if f.startswith(file_name + "-") and f.endswith("-release.apk")),
                  key=lambda f: "-universal-" not in f)
    if not apks:
        raise PortError("Gradle terminó pero no encontré el APK en %s." % outputs)
    apk = os.path.join(outputs, apks[0])
    outdir = os.path.join(HOME, "apks")
    os.makedirs(outdir, exist_ok=True)
    final = os.path.join(outdir, "%s v%s.apk" % (re.sub(r'[\\/:*?"<>|]', "", info["title"]), info["version"]))
    shutil.move(apk, final + ".part")  # mover, no copiar: así no quedan dos APK
    os.replace(final + ".part", final)  # el APK anterior solo se reemplaza cuando el nuevo está completo
    return final


def adb_device(sdk, log):
    adb = os.path.join(sdk, "platform-tools", "adb.exe" if WINDOWS else "adb")
    if not os.path.exists(adb):
        adb = shutil.which("adb") or adb
    code, out = run([adb, "devices"], log)
    devices = [l.split()[0] for l in out.splitlines() if len(l.split()) == 2 and l.split()[1] == "device"]
    if code != 0 or not devices:
        raise PortError("No hay teléfono conectado por adb. Conéctalo por USB con la depuración USB "
                        "activada y acepta el aviso en el teléfono.")
    if len(devices) != 1:
        raise PortError("Hay varios dispositivos conectados. Deja conectado solo el teléfono que quieres probar.")
    return [adb, "-s", devices[0]]


class ApkLog:
    """Captura el UID del juego, incluso si el proceso se cierra o reinicia."""

    def __init__(self, adb, package, on_line, log):
        if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+", package):
            raise PortError("El identificador del juego no es un nombre de paquete Android válido.")
        code, out = run(adb + ["shell", "cmd", "package", "list", "packages", "-U", package], log)
        match = re.search(r"^package:" + re.escape(package) + r"\s+uid:(\d+)\s*$", out, re.M)
        if code or not match:
            raise PortError("No pude consultar el juego instalado para capturar su registro.")
        code, clock = run(adb + ["shell", "date '+%m-%d %H:%M:%S.000'"], log)
        clock = clock.strip()
        # La hora del telefono evita perder el arranque si logcat tarda en conectarse.
        since = clock if code == 0 and re.fullmatch(r"\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}", clock) else "2000"
        folder = os.path.join(HOME, "registros")
        os.makedirs(folder, exist_ok=True)
        self.path = os.path.join(folder, "%s-%s-%s.txt" % (package, time.strftime("%Y%m%d-%H%M%S"), time.time_ns()))
        self.output = open(self.path, "w", encoding="utf-8", buffering=1)
        self.output.write("Juego: %s\nDispositivo: %s\n\n" % (package, adb[-1]))
        self.on_line = on_line
        try:
            self.process = subprocess.Popen(
                adb + ["logcat", "--uid=" + match[1], "-b", "main", "-b", "system", "-b", "crash",
                       "-v", "threadtime", "-T", since],
                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace",
                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        except OSError:
            self.output.close()
            raise
        self.thread = threading.Thread(target=self._read, daemon=True)
        self.thread.start()

    def _read(self):
        try:
            for line in self.process.stdout:
                self.output.write(line)
                self.on_line(line.rstrip("\r\n"))
        except OSError as e:
            self.on_line("Error al guardar el log: " + str(e))
        finally:
            self.process.stdout.close()
            if self.process.poll() is None:
                self.process.terminate()
            try:
                code = self.process.wait(timeout=2)
            except subprocess.TimeoutExpired:
                self.process.kill()
                code = self.process.wait()
            try:
                self.output.write("\nCaptura terminada (codigo %s).\n" % code)
            except OSError:
                pass
            finally:
                self.output.close()
            self.on_line("Captura terminada. Registro guardado en: " + self.path)

    def stop(self):
        if self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(timeout=2)
            except subprocess.TimeoutExpired:
                self.process.kill()
        self.thread.join(timeout=2)


def adb_launch(adb, package, log, on_launch=None):
    if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+", package):
        raise PortError("El identificador del juego no es un nombre de paquete Android válido.")
    if on_launch:
        on_launch(adb, package)  # antes de abrir: incluye los fallos durante el arranque
    code, out = run(adb + ["shell", "monkey", "-p", package, "-c", "android.intent.category.LAUNCHER", "1"], log)
    if code or "No activities found" in out or "monkey aborted" in out.lower():
        raise PortError("El juego está instalado, pero Android no pudo abrirlo. Revisa el log del juego.")
    log("Juego abierto en el teléfono.")


def adb_install(apk, project, sdk, log, on_launch=None):
    adb = adb_device(sdk, log)
    # -r = reinstalar conservando los datos (partidas). Nunca desinstalamos.
    code, out = run(adb + ["install", "-r", apk], log)
    if "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in out:
        raise PortError("El teléfono ya tiene este juego instalado con OTRA firma. Para no borrar tus "
                        "partidas no desinstalé nada. Copia tus partidas antes y desinstálalo tú, o "
                        "usa la carpeta de firma original en " + project["folder"])
    if code != 0 or "Success" not in out:
        raise PortError("adb no pudo instalar el APK. Revisa el registro de arriba.")
    adb_launch(adb, project["id"], log, on_launch)


_workspace_mutex = threading.RLock()
_workspace_depth = threading.local()


@contextmanager
def workspace_guard():
    """Compilar, extraer ZIP y limpiar no pueden tocar el mismo espacio simultáneamente."""
    if not _workspace_mutex.acquire(blocking=False):
        raise PortError("Portémon está trabajando. Espera a que termine antes de limpiar o crear otro APK.")
    lock = None
    acquired = False
    nested = getattr(_workspace_depth, "active", False)
    try:
        if not nested:
            os.makedirs(HOME, exist_ok=True)
            lock = open(os.path.join(HOME, "portemon.lock"), "a+b")
            if os.path.getsize(lock.name) == 0:
                lock.write(b"0")
                lock.flush()
            lock.seek(0)
            try:
                if WINDOWS:
                    import msvcrt
                    msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
                else:
                    import fcntl
                    fcntl.flock(lock.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
                acquired = True
            except OSError as error:
                raise PortError("Otra ventana de Portémon está trabajando. Espera a que termine.") from error
            _workspace_depth.active = True
        yield
    finally:
        if lock is not None:
            if acquired:
                lock.seek(0)
                if WINDOWS:
                    import msvcrt
                    msvcrt.locking(lock.fileno(), msvcrt.LK_UNLCK, 1)
                else:
                    import fcntl
                    fcntl.flock(lock.fileno(), fcntl.LOCK_UN)
                _workspace_depth.active = False
            lock.close()
        _workspace_mutex.release()


def workspace_operation(function):
    @wraps(function)
    def guarded(*args, **kwargs):
        with workspace_guard():
            return function(*args, **kwargs)
    return guarded


@workspace_operation
def limpiar(log=print, confirm=None, include_build=False):
    """Muestra el plan; solo elimina después de una confirmación explícita."""
    import portemon_clean
    candidates = portemon_clean.plan(HOME, ANDROID, MOTOR_VERSION, include_build)
    log("Portémon · Limpieza de archivos")
    log("Se conservan juegos, ZIP extraídos, firmas, partidas, logs y el motor actual.")
    if not candidates:
        log("No hay archivos para limpiar.")
        return 0
    total = sum(c.size for c in candidates)
    for item in candidates:
        log("[%s] %.1f MB  %s" % (item.category, item.size / 1048576, item.path))
    log("Total previsto: %.1f MB en %d elementos." % (total / 1048576, len(candidates)))
    if confirm is None or not confirm():
        log("Vista previa terminada. No se eliminó ningún archivo.")
        return 0
    freed, failures = portemon_clean.execute(candidates, log)
    log("Limpieza terminada: %.1f MB liberados; %d elementos sin eliminar." % (freed / 1048576, failures))
    if failures:
        raise PortError("Algunos archivos no se pudieron eliminar. Revisa los mensajes anteriores.")
    return freed


@workspace_operation

def port(root, author=None, version=None, title=None, icon=None, soundfont=None,
         install=False, motor_dir=None, log=print, ligero=False, fondo="gris", on_launch=None):
    """Proceso completo. Devuelve la ruta del APK."""
    if root.lower().endswith(".zip") and os.path.isfile(root):
        root = abrir_zip(root, log)
    if not rmxp_analyze.find(root, "Game.ini"):
        raise PortError("Esa carpeta no tiene Game.ini. Elige la carpeta principal del juego.")
    report = rmxp_analyze.analyze(root)
    log("Revisión: " + report.get("veredicto", "?"))
    for p in report["problemas"]:
        log("  Problema: " + p)
    for a in report["avisos"]:
        log("  Aviso: " + a)

    info = game_info(root)
    info.update({k: v for k, v in (("author", author), ("version", version), ("title", title)) if v})
    log("Juego: %s  |  Autor: %s  |  Versión: %s" % (info["title"], info["author"] or "?", info["version"]))

    sdk = find_sdk()
    java_home = find_java()
    project, project_path = load_project(info, log)
    make_keystore(project, info, java_home, log)
    save_project(project, project_path)
    log("Guarda una copia de %s: sin esa firma, las próximas versiones no se podrán instalar "
        "encima de esta." % project["folder"])

    motor = get_motor(log, motor_dir)
    work = os.path.join(HOME, "trabajo", slug(info["title"], "juego"))
    try:
        assets = stage_game(root, work, info, soundfont, log, os.path.join(os.path.dirname(motor), "ruby"), ligero)
        res = make_icon_res(icon, work, fondo)
        apk = gradle_build(project, info, assets, res, motor, sdk, java_home, log)
    finally:
        # La copia del juego y los archivos intermedios de Gradle ocupan casi lo mismo que el
        # juego cada uno; solo se necesitan mientras se compila.
        shutil.rmtree(largo(work), ignore_errors=True)
        shutil.rmtree(largo(os.path.join(ANDROID, "app", "build")), ignore_errors=True)
    log("APK listo: " + apk)
    if install:
        adb_install(apk, project, sdk, log, on_launch)
    return apk


@workspace_operation
def abrir_zip(path, log=print):
    """Descomprime el .zip del juego en una carpeta de trabajo y devuelve la que tiene Game.ini."""
    import zipfile
    dest = os.path.join(HOME, "zips", slug(os.path.splitext(os.path.basename(path))[0], "juego"))
    shutil.rmtree(largo(dest), ignore_errors=True)
    log("Descomprimiendo %s..." % os.path.basename(path))
    with zipfile.ZipFile(path) as z:
        z.extractall(largo(dest))
    # El juego suele venir dentro de una carpeta del zip: se busca Game.ini hasta 3 niveles abajo
    for d, dirs, files in os.walk(dest):
        if d[len(dest):].count(os.sep) > 3:
            dirs[:] = []
            continue
        if any(f.lower() == "game.ini" for f in files):
            return d
    raise PortError("Ese .zip no tiene Game.ini. Elige el .zip del juego (o su carpeta).")


@workspace_operation
def plantilla(motor_dir=None, log=print):
    """APK sin juego para la app del teléfono (RPGMK-AndroidPort-Apk): el motor, los parches
    en assets/rmxp/patches y la biblioteca de Ruby en assets/rmxp/rmxp_ruby. El teléfono le
    mete el juego, le cambia nombre, icono e id, y lo firma."""
    sdk, java_home = find_sdk(), find_java()
    motor = get_motor(log, motor_dir)
    work = os.path.join(HOME, "trabajo", "plantilla")
    shutil.rmtree(largo(work), ignore_errors=True)
    assets = os.path.join(work, "assets")
    shutil.copytree(os.path.join(ANDROID, "patches"), os.path.join(assets, "rmxp", "patches"))
    ruby = os.path.join(os.path.dirname(motor), "ruby")
    if os.path.isdir(ruby):
        shutil.copytree(ruby, os.path.join(assets, "rmxp", "rmxp_ruby"))
    # Icono genérico, en los mismos archivos que usa un juego con icono propio (el teléfono los cambia)
    icon = os.path.join(ANDROID, "app", "src", "main", "res", "mipmap-xxxhdpi", "ic_launcher.png")
    res = make_icon_res(icon, work, "gris")
    info = {"title": "Plantilla", "version": "1"}
    project = {"id": "com.rmxp.plantilla", "keystore": os.path.join(work, "plantilla.jks"),
               "password": "plantilla"}
    make_keystore(project, {"title": "Plantilla", "author": "RMXP-Android-Ports"}, java_home, log)
    try:
        return gradle_build(project, info, assets, res, motor, sdk, java_home, log)
    finally:
        shutil.rmtree(largo(work), ignore_errors=True)


# --------------------------------------------------------------------------
# Interfaz (tkinter, viene con Python). Arrastrar y soltar si está tkinterdnd2.
# --------------------------------------------------------------------------

COLORS = {"tinta": "#10151F", "pizarra": "#1B2433", "musgo": "#33445C",
          "niebla": "#EDF3FA", "liquen": "#A6B4C8", "ambar": "#67DFC0", "coral": "#FF958D"}


def gui(initial=None):
    import tkinter as tk
    from tkinter import filedialog, font as tkfont, ttk
    try:
        from tkinterdnd2 import DND_FILES, TkinterDnD
        win = TkinterDnD.Tk()
    except Exception:
        DND_FILES, win = None, tk.Tk()

    C = COLORS
    win.title("Portémon · PC")
    win.configure(bg=C["tinta"])
    win.geometry("1200x840")
    win.minsize(1000, 700)
    base = tkfont.nametofont("TkDefaultFont").actual()["family"]
    f_title, f_body, f_small = (base, 23, "bold"), (base, 11), (base, 10)
    events = queue.Queue()
    apk_events = queue.Queue(maxsize=2000)
    state = {"root": None, "icon": None, "sf2": None, "busy": False,
             "capture": None, "package": None, "closed": False, "apk": None}

    def label(parent, text, fg=C["niebla"], font=f_body, **kw):
        return tk.Label(parent, text=text, bg=parent["bg"], fg=fg, font=font, anchor="w", justify="left", **kw)

    def button(parent, text, cmd, bg=C["pizarra"], fg=C["niebla"]):
        return tk.Button(parent, text=text, command=cmd, bg=bg, fg=fg, activebackground=C["musgo"],
                         activeforeground=C["niebla"], relief="flat", font=f_body, padx=14, pady=9,
                         cursor="hand2", highlightthickness=0, bd=0)

    header = tk.Frame(win, bg=C["tinta"])
    header.pack(fill="x", padx=28, pady=(20, 14))
    mark = label(header, "P →", fg=C["tinta"], font=(base, 21, "bold"), padx=14, pady=8)
    mark.configure(bg=C["ambar"])
    mark.pack(side="left", padx=(0, 16))
    brand = tk.Frame(header, bg=C["tinta"])
    brand.pack(side="left")
    label(brand, "Portémon", font=f_title).pack(anchor="w")
    label(brand, "TU JUEGO. SU PROPIA APP DE ANDROID.", fg=C["liquen"], font=(base, 9, "bold")).pack(anchor="w")
    label(header, "PC  /  ESPACIO DE TRABAJO", fg=C["ambar"], font=f_small).pack(side="right")

    workspace = tk.PanedWindow(win, orient="horizontal", bg=C["tinta"], bd=0, sashwidth=14, sashrelief="flat")
    workspace.pack(fill="both", expand=True, padx=28, pady=(0, 20))
    left = tk.Frame(workspace, bg=C["tinta"], width=420)
    right = tk.Frame(workspace, bg=C["tinta"])
    workspace.add(left, minsize=380, width=430)
    workspace.add(right, minsize=500)

    style = ttk.Style(win)
    style.theme_use("clam")
    style.configure("TNotebook", background=C["tinta"], borderwidth=0)
    style.configure("TNotebook.Tab", background=C["pizarra"], foreground=C["liquen"], padding=(18, 11))
    style.map("TNotebook.Tab", background=[("selected", C["musgo"])], foreground=[("selected", C["niebla"])])
    style.configure("TProgressbar", background=C["ambar"], troughcolor=C["pizarra"], borderwidth=0)
    style.configure("Vertical.TScrollbar", background=C["musgo"], troughcolor=C["tinta"], borderwidth=0, arrowsize=10)

    viewport = tk.Frame(left, bg=C["tinta"])
    viewport.pack(fill="both", expand=True)
    canvas = tk.Canvas(viewport, bg=C["tinta"], highlightthickness=0, bd=0)
    scroll = ttk.Scrollbar(viewport, orient="vertical", command=canvas.yview)
    scroll.pack(side="right", fill="y")
    canvas.pack(side="left", fill="both", expand=True)
    canvas.configure(yscrollcommand=scroll.set)
    content = tk.Frame(canvas, bg=C["tinta"])
    content_id = canvas.create_window((0, 0), window=content, anchor="nw")
    content.bind("<Configure>", lambda e: canvas.configure(scrollregion=canvas.bbox("all")))
    canvas.bind("<Configure>", lambda e: canvas.itemconfigure(content_id, width=e.width))

    def scroll_form(event):
        widget = event.widget
        while widget is not None and widget != canvas:
            widget = getattr(widget, "master", None)
        if widget == canvas:
            direction = -1 if getattr(event, "num", None) == 4 or event.delta > 0 else 1
            canvas.yview_scroll(direction * 3, "units")
            return "break"
    canvas.bind_all("<MouseWheel>", scroll_form, add="+")
    canvas.bind_all("<Button-4>", scroll_form, add="+")
    canvas.bind_all("<Button-5>", scroll_form, add="+")

    def section(title, subtitle):
        card = tk.Frame(content, bg=C["pizarra"], padx=18, pady=16)
        card.pack(fill="x", pady=(0, 12), padx=(0, 8))
        label(card, title, fg=C["ambar"], font=(base, 10, "bold")).pack(fill="x")
        label(card, subtitle, fg=C["liquen"], font=f_small, wraplength=345).pack(fill="x", pady=(5, 12))
        return card

    drop = section("01  /  ORIGEN", "Elige la carpeta del juego o su archivo ZIP.")
    drop_text = label(drop, "Arrastra tu juego aquí" if DND_FILES else "Sin juego seleccionado", font=(base, 13, "bold"), wraplength=340)
    drop_text.pack(fill="x", pady=(0, 10))
    choices = tk.Frame(drop, bg=C["pizarra"])
    choices.pack(fill="x")
    button(choices, "Carpeta…", lambda: choose(filedialog.askdirectory(title="Carpeta del juego")), bg=C["musgo"]).pack(side="left", padx=(0, 8))
    button(choices, "Archivo ZIP…", lambda: choose(filedialog.askopenfilename(title="Juego en .zip", filetypes=[("Zip", "*.zip")])), bg=C["musgo"]).pack(side="left")

    form = section("02  /  IDENTIDAD DEL JUEGO", "Revisa los datos antes de crear el APK.")
    fields = {}
    for key, text in (("title", "Nombre de la app"), ("author", "Autor (para la firma)"), ("version", "Versión")):
        label(form, text, fg=C["liquen"], font=f_small).pack(fill="x", pady=(5, 4))
        entry = tk.Entry(form, bg=C["tinta"], fg=C["niebla"], insertbackground=C["niebla"], relief="flat",
                         font=f_body, highlightthickness=1, highlightbackground=C["musgo"], highlightcolor=C["ambar"])
        entry.pack(fill="x", ipady=7, pady=(0, 5))
        fields[key] = entry

    extras = section("03  /  RECURSOS", "Icono y música MIDI opcionales.")
    icon_lbl = label(extras, "Icono de la plantilla", fg=C["liquen"], font=f_small, wraplength=340)
    sf2_lbl = label(extras, "Sin SoundFont externo", fg=C["liquen"], font=f_small, wraplength=340)

    def pick(key, lbl, prefix, types):
        path = filedialog.askopenfilename(filetypes=types)
        if path:
            state[key] = path
            lbl.config(text=prefix + os.path.basename(path), fg=C["niebla"])

    button(extras, "Elegir icono PNG…", lambda: pick("icon", icon_lbl, "Icono: ", [("PNG", "*.png")]), bg=C["musgo"]).pack(fill="x")
    icon_lbl.pack(fill="x", pady=(5, 12))
    button(extras, "Elegir SoundFont…", lambda: pick("sf2", sf2_lbl, "MIDI: ", [("SoundFont", "*.sf2")]), bg=C["musgo"]).pack(fill="x")
    sf2_lbl.pack(fill="x", pady=(5, 12))
    fondo = tk.StringVar(value="gris")
    label(extras, "Fondo del icono", fg=C["liquen"], font=f_small).pack(fill="x")
    fondos = tk.Frame(extras, bg=C["pizarra"])
    fondos.pack(fill="x", pady=(4, 0))
    for nombre in ("gris", "negro", "blanco"):
        tk.Radiobutton(fondos, text=nombre.capitalize(), value=nombre, variable=fondo, bg=C["pizarra"], fg=C["niebla"],
                       selectcolor=C["tinta"], activebackground=C["pizarra"], font=f_small,
                       highlightthickness=0, bd=0).pack(side="left", padx=(0, 12))

    options = section("04  /  AL TERMINAR", "La carpeta original, las firmas y las partidas se conservan.")
    install, ligero, capture = tk.BooleanVar(value=True), tk.BooleanVar(value=False), tk.BooleanVar(value=True)
    for text, variable in (("Instalar y abrir por USB", install), ("Capturar log del juego al abrirlo", capture),
                           ("Ahorrar espacio (experimental)", ligero)):
        tk.Checkbutton(options, text=text, variable=variable, bg=C["pizarra"], fg=C["niebla"], selectcolor=C["tinta"],
                       activebackground=C["pizarra"], activeforeground=C["niebla"], font=f_small,
                       highlightthickness=0, bd=0).pack(anchor="w", pady=4)

    action = tk.Frame(left, bg=C["tinta"], pady=8)
    action.pack(fill="x")
    go = button(action, "Crear APK", lambda: start(), bg=C["ambar"], fg=C["tinta"])
    go.config(font=(base, 13, "bold"), pady=12)
    go.pack(fill="x", pady=(0, 10))
    progress = ttk.Progressbar(action, mode="indeterminate")
    progress.pack(fill="x")
    status = label(action, "Listo para elegir un juego.", fg=C["liquen"], font=f_small, wraplength=370)
    status.pack(fill="x", pady=(8, 0))

    heading = tk.Frame(right, bg=C["tinta"])
    heading.pack(fill="x", pady=(0, 12))
    label(heading, "Actividad del proyecto", font=(base, 16, "bold")).pack(side="left")

    def open_output():
        path = state["apk"]
        if path and os.path.isfile(path):
            if WINDOWS:
                subprocess.Popen(["explorer", "/select,", path])
            else:
                subprocess.Popen(["open" if sys.platform == "darwin" else "xdg-open", os.path.dirname(path)])
    output_button = button(heading, "Ver APK…", open_output, bg=C["musgo"])
    output_button.pack(side="right")
    output_button.config(state="disabled")
    label(right, "Construcción y registro de Android, siempre a mano.", fg=C["liquen"], font=f_small).pack(fill="x", pady=(0, 12))
    tabs = ttk.Notebook(right)
    tabs.pack(fill="both", expand=True)
    build_page, apk_page = tk.Frame(tabs, bg=C["tinta"]), tk.Frame(tabs, bg=C["tinta"])
    tabs.add(build_page, text="Construcción")
    tabs.add(apk_page, text="Android en vivo")
    controls = tk.Frame(apk_page, bg=C["tinta"])
    controls.pack(fill="x", pady=10)
    open_game = button(controls, "Abrir y capturar", lambda: launch(), bg=C["musgo"])
    open_game.pack(side="left")
    open_game.config(state="disabled")
    stop_log = button(controls, "Detener", lambda: stop_capture())
    stop_log.pack(side="left", padx=5)
    button(controls, "Guardar copia…", lambda: save_log()).pack(side="left")
    capture_status = label(apk_page, "Conecta tu teléfono por USB para ver el registro del juego.", fg=C["liquen"], font=f_small, wraplength=520)
    capture_status.pack(fill="x", pady=(0, 8))

    def text_area(parent):
        frame = tk.Frame(parent, bg=C["tinta"])
        frame.pack(fill="both", expand=True)
        box = tk.Text(frame, bg="#0B1018", fg=C["liquen"], font=("Consolas", 10), relief="flat",
                      wrap="word", highlightthickness=1, highlightbackground=C["pizarra"], padx=16, pady=14)
        scroll = ttk.Scrollbar(frame, command=box.yview)
        scroll.pack(side="right", fill="y")
        box.configure(yscrollcommand=scroll.set)
        box.pack(fill="both", expand=True)
        return box

    logbox, apkbox = text_area(build_page), text_area(apk_page)
    logbox.tag_config("ok", foreground=C["ambar"])
    logbox.tag_config("error", foreground=C["coral"])

    def apk_line(line):
        try:
            apk_events.put_nowait(line)
        except queue.Full:
            pass  # solo se omiten lineas de la vista; ApkLog ya las guardo en el archivo

    def capture_started(adb, package, enabled=True):
        if state["closed"]:
            return
        events.put((package, "package"))
        if state["capture"]:
            state["capture"].stop()
            state["capture"] = None
        if enabled:
            try:
                session = ApkLog(adb, package, apk_line, lambda t: events.put((t, None)))
                state["capture"] = session
                if state["closed"]:
                    session.stop()
                else:
                    events.put((session.path, "capture"))
            except (OSError, PortError) as e:
                events.put(("El APK está instalado, pero no se pudo capturar el log: " + str(e), "error"))

    def launch():
        if state["busy"] or not state["package"]:
            return
        state["busy"] = True
        open_game.config(state="disabled")
        def worker():
            try:
                log = lambda t: events.put((t, None))
                adb_launch(adb_device(find_sdk(), log), state["package"], log, capture_started)
            except (OSError, PortError) as e:
                events.put((str(e), "error"))
            finally:
                events.put((None, "fin"))
        threading.Thread(target=worker, daemon=True).start()

    def stop_capture():
        session = state["capture"]
        if session:
            threading.Thread(target=session.stop, daemon=True).start()

    def save_log():
        session = state["capture"]
        if not session:
            capture_status.config(text="Todavía no hay un registro. Abre el juego con captura activada.")
            return
        path = filedialog.asksaveasfilename(defaultextension=".txt", initialfile=os.path.basename(session.path),
                                          filetypes=[("Registro de texto", "*.txt")])
        if path:
            try:
                if os.path.abspath(path) != os.path.abspath(session.path):
                    shutil.copyfile(session.path, path)
                capture_status.config(text="Copia guardada: " + path)
            except OSError as e:
                capture_status.config(text="No se pudo guardar: " + str(e))

    def choose(path):
        if not path or state["busy"]:
            return
        path = path.strip("{}")
        if path.lower().endswith(".zip") and os.path.isfile(path):
            write("Descomprimiendo %s..." % os.path.basename(path))
            win.update()
            try:
                path = abrir_zip(path, lambda t: None)
            except (PortError, OSError) as e:
                write(str(e), "error")
                return
        elif not os.path.isdir(path):
            path = os.path.dirname(path)
        state["root"] = path
        info = game_info(path)
        for k in fields:
            fields[k].delete(0, "end")
            fields[k].insert(0, info[k])
        drop_text.config(text=info["title"], fg=C["ambar"])
        status.config(text="Juego revisado. Comprueba sus datos y crea el APK.")
        state["sf2"] = None  # el del juego se usa solo (stage_game); el botón es para cambiarlo
        own = find_soundfont(path)
        sf2_lbl.config(text="Música MIDI: la del juego (%s)" % own if own else "Música MIDI: sin SoundFont",
                       fg=C["niebla"] if own else C["liquen"])
        logbox.delete("1.0", "end")
        report = rmxp_analyze.analyze(path)
        write("Revisión: %s" % report.get("veredicto", "?"), "ok" if not report["problemas"] else "error")
        for p in report["problemas"]:
            write("Problema: " + p, "error")
        for a in report["avisos"]:
            write("Aviso: " + a)
        if not info["author"]:
            write("No encontré el autor en un Léeme/Readme. Escríbelo arriba para la firma.", "error")

    def write(text, tag=None):
        logbox.insert("end", text + "\n", tag)
        logbox.see("end")

    def start():
        if state["busy"]:
            return
        if not state["root"]:
            write("Primero elige la carpeta del juego.", "error")
            return
        state["busy"] = True
        go.config(text="Trabajando…", state="disabled")
        open_game.config(state="disabled")
        progress.start()
        status.config(text="Creando el APK…")
        tabs.select(build_page)
        want_log = capture.get()
        args = dict(root=state["root"], icon=state["icon"], soundfont=state["sf2"], install=install.get(), ligero=ligero.get(), fondo=fondo.get(),
                    on_launch=lambda a, p: capture_started(a, p, want_log),
                    **{k: e.get().strip() for k, e in fields.items()})

        def worker():
            try:
                apk = port(log=lambda t: events.put((t, None)), **args)
                events.put(("Listo. APK en: " + apk, "ok"))
                events.put((apk, "apk"))
            except PortError as e:
                events.put((str(e), "error"))
            except Exception as e:  # noqa: BLE001 - mostrar cualquier fallo en la ventana
                events.put(("Error inesperado: %r" % e, "error"))
            events.put((None, "fin"))

        threading.Thread(target=worker, daemon=True).start()

    def pump():
        for _ in range(200):
            if events.empty():
                break
            text, tag = events.get()
            if tag == "fin":
                state["busy"] = False
                go.config(text="Crear APK", state="normal")
                open_game.config(state="normal" if state["package"] else "disabled")
                progress.stop()
                status.config(text="Proceso terminado. Revisa el resultado en el registro.")
            elif tag == "apk":
                state["apk"] = text
                output_button.config(state="normal")
            elif tag == "package":
                state["package"] = text
            elif tag == "capture":
                apkbox.delete("1.0", "end")
                capture_status.config(text="Guardando automáticamente en: " + text)
                tabs.select(apk_page)
            else:
                write(text, tag)
        lines = []
        for _ in range(500):
            try:
                lines.append(apk_events.get_nowait())
            except queue.Empty:
                break
        if lines:
            apkbox.insert("end", "\n".join(lines) + "\n")
            count = int(apkbox.index("end-1c").split(".")[0])
            if count > 10000:
                apkbox.delete("1.0", "%d.0" % (count - 10000))
            apkbox.see("end")
        session = state["capture"]
        stop_log.config(state="normal" if session and session.process.poll() is None else "disabled")
        win.after(50, pump)

    def lote(paths):
        """Varios juegos a la vez: uno tras otro, con los datos que encuentre en cada uno."""
        if state["busy"]:
            return
        state["busy"] = True
        go.config(text="Trabajando…", state="disabled")
        open_game.config(state="disabled")
        progress.start()
        status.config(text="Creando los APK de la cola…")
        write("%d juegos en cola." % len(paths), "ok")
        want_log = capture.get()
        opts = dict(install=install.get(), ligero=ligero.get(), fondo=fondo.get(),
                    on_launch=lambda a, p: capture_started(a, p, want_log))

        def worker():
            for i, p in enumerate(paths, 1):
                events.put(("\n[%d/%d] %s" % (i, len(paths), os.path.basename(p)), "ok"))
                try:
                    apk = port(p, log=lambda t: events.put((t, None)), **opts)
                    events.put(("Listo: " + apk, "ok"))
                    events.put((apk, "apk"))
                except Exception as e:  # noqa: BLE001 - seguir con los demás
                    events.put((str(e), "error"))
            events.put((None, "fin"))
        threading.Thread(target=worker, daemon=True).start()

    def soltar(paths):
        paths = [p for p in paths if p]
        lote(paths) if len(paths) > 1 else choose(paths[0] if paths else None)

    def novedades():
        """Avisa si en GitHub hay cambios o un motor más nuevo que el de esta copia."""
        try:
            import urllib.request as u
            api = "https://api.github.com/repos/ZenithUzu/RMXP-Android-Ports/"
            rel = json.load(u.urlopen(api + "releases?per_page=30", timeout=8))
            nums = [int(r["tag_name"][7:]) for r in rel if re.fullmatch(r"motor-v\d+", r["tag_name"])]
            if nums and max(nums) > int(MOTOR_VERSION[7:]):
                events.put(("Hay un motor nuevo (motor-v%d). Haz Pull en GitHub Desktop." % max(nums), "ok"))
            head = os.path.join(REPO, ".git", "HEAD")
            ref = open(head).read().split()[-1]
            local = open(os.path.join(os.path.dirname(head), *ref.split("/"))).read().strip() if "/" in ref else ref
            remote = json.load(u.urlopen(api + "commits/main", timeout=8))["sha"]
            if local != remote:
                events.put(("Hay una versión nueva de la herramienta en GitHub: haz Pull en GitHub Desktop.", "ok"))
        except Exception:  # noqa: BLE001 - sin internet o sin .git: no se avisa nada
            pass

    if DND_FILES:
        win.drop_target_register(DND_FILES)
        win.dnd_bind("<<Drop>>", lambda e: soltar(list(win.tk.splitlist(e.data))))
    if initial:
        win.after(200, lambda: soltar(initial if isinstance(initial, list) else [initial]))
    threading.Thread(target=novedades, daemon=True).start()
    def close():
        state["closed"] = True
        if state["capture"]:
            state["capture"].stop()
        win.destroy()
    win.protocol("WM_DELETE_WINDOW", close)
    pump()
    win.mainloop()


def main():
    ap = argparse.ArgumentParser(description="Convierte un juego de RPG Maker en APK de Android.")
    ap.add_argument("carpeta", nargs="*", help="carpeta del juego (la que tiene Game.ini) o su .zip; varias = en cola")
    ap.add_argument("--sin-interfaz", action="store_true", help="no abrir ventana")
    ap.add_argument("--instalar", action="store_true", help="instalar con adb al terminar")
    ap.add_argument("--autor"), ap.add_argument("--version"), ap.add_argument("--nombre")
    ap.add_argument("--icono", help="PNG para el icono"), ap.add_argument("--soundfont", help=".sf2 para MIDI")
    ap.add_argument("--motor", help="carpeta lib/ con el motor compilado (en vez de descargarlo)")
    ap.add_argument("--ligero", action="store_true", help="ahorrar espacio: Graphics/Audio se leen del APK")
    ap.add_argument("--fondo-icono", choices=sorted(FONDOS_ICONO), default="gris", help="color detrás del icono")
    ap.add_argument("--plantilla", action="store_true", help="APK sin juego para la app del teléfono")
    ap.add_argument("--limpiar", action="store_true", help="revisar y limpiar APKs y motores anteriores")
    ap.add_argument("--ver-limpieza", action="store_true", help="mostrar la limpieza sin eliminar archivos")
    ap.add_argument("--incluir-compilacion", action="store_true", help="incluir archivos regenerables de Gradle")
    a = ap.parse_args()
    if a.limpiar or a.ver_limpieza:
        try:
            return limpiar(confirm=None if a.ver_limpieza else
                           lambda: input("¿Eliminar solo estos elementos? Escribe SI: ").strip().upper() == "SI",
                           include_build=a.incluir_compilacion)
        except (PortError, OSError, EOFError, KeyboardInterrupt) as error:
            print("Limpieza detenida: " + str(error))
            sys.exit(1)
    if a.plantilla:
        print("Plantilla lista: " + plantilla(a.motor))
        return
    if not a.sin_interfaz:
        return gui(a.carpeta or None)
    if not a.carpeta:
        ap.error("falta la carpeta del juego")
    fallos = 0
    for carpeta in a.carpeta:
        try:
            port(carpeta, a.autor, a.version, a.nombre, a.icono, a.soundfont, a.instalar, a.motor, ligero=a.ligero, fondo=a.fondo_icono)
        except PortError as e:
            print("ERROR: " + str(e))
            fallos += 1
    sys.exit(1 if fallos else 0)


if __name__ == "__main__":
    main()
