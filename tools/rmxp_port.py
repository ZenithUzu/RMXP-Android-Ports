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

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rmxp_analyze  # noqa: E402

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ANDROID = os.path.join(REPO, "android")
HOME = os.path.join(os.path.expanduser("~"), "RMXP-Android-Ports")
# Motor ya compilado (librerías .so). Se publica con .github/workflows/motor.yml.
MOTOR_VERSION = "motor-v9"
MOTOR_URL = "https://github.com/ZenithUzu/RMXP-Android-Ports/releases/download/%s/motor-android.zip" % MOTOR_VERSION
WINDOWS = os.name == "nt"


class PortError(Exception):
    """Error con un mensaje para la persona, en español."""


# --------------------------------------------------------------------------
# Datos del juego: título, autor (del Léeme) y versión
# --------------------------------------------------------------------------

README_RE = re.compile(r"^(read ?me|l[eé]e ?me|l[eé]ame|cr[eé]ditos|credits|info|instrucciones|leia-me)", re.I)
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
    for name in sorted(os.listdir(root)):
        path = os.path.join(root, name)
        if not (os.path.isfile(path) and README_RE.match(name)):
            continue
        text = read_text(path)
        for line in text.splitlines():
            line = line.strip()
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
            continue
        else:
            out.append(c)
        i += 1
    return re.sub(r",(\s*[}\]])", r"\1", "".join(out))  # comas sobrantes


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


def adb_install(apk, project, sdk, log):
    adb = os.path.join(sdk, "platform-tools", "adb.exe" if WINDOWS else "adb")
    if not os.path.exists(adb):
        adb = shutil.which("adb") or adb
    code, out = run([adb, "devices"], log)
    devices = [l for l in out.splitlines()[1:] if l.strip().endswith("device")]
    if code != 0 or not devices:
        raise PortError("No hay teléfono conectado por adb. Conéctalo por USB con la depuración USB "
                        "activada y acepta el aviso en el teléfono. El APK ya está listo en:\n" + apk)
    # -r = reinstalar conservando los datos (partidas). Nunca desinstalamos.
    code, out = run([adb, "install", "-r", apk], log)
    if "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in out:
        raise PortError("El teléfono ya tiene este juego instalado con OTRA firma. Para no borrar tus "
                        "partidas no desinstalé nada. Copia tus partidas antes y desinstálalo tú, o "
                        "usa la carpeta de firma original en " + project["folder"])
    if code != 0 or "Success" not in out:
        raise PortError("adb no pudo instalar el APK. Revisa el registro de arriba.")
    run([adb, "shell", "monkey", "-p", project["id"], "-c", "android.intent.category.LAUNCHER", "1"], log)
    log("Instalado y abierto en el teléfono.")


def limpiar(log=print):
    """Borra lo que ya no sirve: APKs viejos (deja el más nuevo de cada juego), restos de
    compilación y motores de versiones anteriores. Nunca toca las firmas (juegos/)."""
    freed = 0

    def size(path):
        if os.path.isfile(path):
            return os.path.getsize(path)
        return sum(os.path.getsize(os.path.join(d, f)) for d, _, fs in os.walk(largo(path)) for f in fs)

    def remove(path):
        nonlocal freed
        if os.path.exists(path):
            freed += size(path)
            shutil.rmtree(largo(path), ignore_errors=True) if os.path.isdir(path) else os.remove(path)
            log("Borrado: " + path)

    apks = os.path.join(HOME, "apks")
    if os.path.isdir(apks):
        newest = {}
        for f in sorted(os.listdir(apks), key=lambda f: os.path.getmtime(os.path.join(apks, f)), reverse=True):
            game = re.sub(r" v[^ ]*\.apk(\.part)?$", "", f)
            if game in newest or f.endswith(".part"):
                remove(os.path.join(apks, f))
            else:
                newest[game] = f
    remove(os.path.join(HOME, "trabajo"))
    remove(os.path.join(HOME, "zips"))
    remove(os.path.join(ANDROID, "app", "build"))
    motores = os.path.join(HOME, "motor")
    if os.path.isdir(motores):
        for v in os.listdir(motores):
            if v != MOTOR_VERSION:
                remove(os.path.join(motores, v))
    log("Listo. Espacio liberado: %.0f MB" % (freed / 1048576))
    return freed


def port(root, author=None, version=None, title=None, icon=None, soundfont=None,
         install=False, motor_dir=None, log=print, ligero=False, fondo="gris"):
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
        adb_install(apk, project, sdk, log)
    return apk


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

COLORS = {"tinta": "#0F2629", "pizarra": "#1A3A3E", "musgo": "#2E5B5F",
          "niebla": "#E9F3F0", "liquen": "#8DB1AA", "ambar": "#F2B544", "coral": "#E46F5E"}


def gui(initial=None):
    import tkinter as tk
    from tkinter import filedialog, font as tkfont
    try:
        from tkinterdnd2 import DND_FILES, TkinterDnD
        win = TkinterDnD.Tk()
    except Exception:
        DND_FILES, win = None, tk.Tk()

    C = COLORS
    win.title("RMXP-Android-Ports")
    win.configure(bg=C["tinta"])
    win.geometry("700x760")
    win.minsize(600, 640)
    base = tkfont.nametofont("TkDefaultFont").actual()["family"]
    f_title = (base, 20, "bold")
    f_body = (base, 11)
    f_small = (base, 10)
    events = queue.Queue()
    state = {"root": None, "icon": None, "sf2": None, "busy": False}

    def label(parent, text, fg=C["niebla"], font=f_body, **kw):
        return tk.Label(parent, text=text, bg=parent["bg"], fg=fg, font=font, anchor="w", justify="left", **kw)

    def button(parent, text, cmd, bg=C["pizarra"], fg=C["niebla"]):
        return tk.Button(parent, text=text, command=cmd, bg=bg, fg=fg, activebackground=C["musgo"],
                         activeforeground=C["niebla"], relief="flat", font=f_body, padx=14, pady=6,
                         cursor="hand2", highlightthickness=0, bd=0)

    pad = dict(padx=24)
    label(win, "Convierte un fangame de RPG Maker en una app de Android", font=f_title,
          wraplength=640).pack(fill="x", pady=(20, 4), **pad)
    label(win, "Tu carpeta original no se modifica. Las partidas del teléfono se conservan al actualizar.",
          fg=C["liquen"], font=f_small).pack(fill="x", **pad)

    drop = tk.Frame(win, bg=C["pizarra"], highlightbackground=C["musgo"], highlightthickness=2)
    drop.pack(fill="x", pady=16, **pad)
    drop_text = label(drop, "Arrastra aquí la carpeta del juego" if DND_FILES else
                      "Elige la carpeta del juego (o arrástrala sobre Portar.bat)", font=(base, 13))
    drop_text.pack(side="left", padx=16, pady=18)
    button(drop, "Elegir carpeta", lambda: choose(filedialog.askdirectory(title="Carpeta del juego"))
           ).pack(side="right", padx=12)
    button(drop, "Elegir .zip", lambda: choose(filedialog.askopenfilename(title="Juego en .zip", filetypes=[("Zip", "*.zip")]))
           ).pack(side="right")

    form = tk.Frame(win, bg=C["tinta"])
    form.pack(fill="x", **pad)
    fields = {}
    for row, (key, text) in enumerate((("title", "Nombre de la app"), ("author", "Autor (para la firma)"),
                                       ("version", "Versión"))):
        label(form, text, fg=C["liquen"]).grid(row=row, column=0, sticky="w", pady=3)
        e = tk.Entry(form, bg=C["pizarra"], fg=C["niebla"], insertbackground=C["niebla"], relief="flat",
                     font=f_body, highlightthickness=1, highlightbackground=C["musgo"], highlightcolor=C["ambar"])
        e.grid(row=row, column=1, sticky="ew", padx=(12, 0), pady=3, ipady=4)
        fields[key] = e
    form.columnconfigure(1, weight=1)

    extras = tk.Frame(win, bg=C["tinta"])
    extras.pack(fill="x", pady=(10, 0), **pad)
    icon_lbl = label(extras, "Icono: el de la app base", fg=C["liquen"], font=f_small)
    sf2_lbl = label(extras, "Música MIDI: sin SoundFont", fg=C["liquen"], font=f_small)

    def pick(key, lbl, prefix, types):
        path = filedialog.askopenfilename(filetypes=types)
        if path:
            state[key] = path
            lbl.config(text=prefix + os.path.basename(path), fg=C["niebla"])

    button(extras, "Icono (PNG)…", lambda: pick("icon", icon_lbl, "Icono: ", [("PNG", "*.png")])).grid(row=0, column=0, sticky="w")
    icon_lbl.grid(row=0, column=1, sticky="w", padx=12)
    button(extras, "SoundFont (.sf2)…", lambda: pick("sf2", sf2_lbl, "Música MIDI: ", [("SoundFont", "*.sf2")])).grid(row=1, column=0, sticky="w", pady=6)
    sf2_lbl.grid(row=1, column=1, sticky="w", padx=12)
    fondo = tk.StringVar(value="gris")
    label(extras, "Fondo del icono", fg=C["liquen"], font=f_small).grid(row=2, column=0, sticky="w")
    fondos = tk.Frame(extras, bg=C["tinta"])
    fondos.grid(row=2, column=1, sticky="w", padx=12)
    for nombre in ("gris", "negro", "blanco"):
        tk.Radiobutton(fondos, text=nombre.capitalize(), value=nombre, variable=fondo, bg=C["tinta"], fg=C["niebla"],
                       selectcolor=C["pizarra"], activebackground=C["tinta"], font=f_small,
                       highlightthickness=0, bd=0).pack(side="left", padx=(0, 10))

    install = tk.BooleanVar(value=True)
    tk.Checkbutton(win, text="Instalar en el teléfono con adb al terminar", variable=install,
                   bg=C["tinta"], fg=C["niebla"], selectcolor=C["pizarra"], activebackground=C["tinta"],
                   activeforeground=C["niebla"], font=f_body, highlightthickness=0, bd=0
                   ).pack(anchor="w", pady=(12, 0), **pad)
    ligero = tk.BooleanVar(value=False)
    tk.Checkbutton(win, text="Ahorrar espacio en el teléfono (experimental)", variable=ligero,
                   bg=C["tinta"], fg=C["niebla"], selectcolor=C["pizarra"], activebackground=C["tinta"],
                   activeforeground=C["niebla"], font=f_body, highlightthickness=0, bd=0
                   ).pack(anchor="w", **pad)

    go = button(win, "Crear APK", lambda: start(), bg=C["ambar"], fg=C["tinta"])
    go.config(font=(base, 13, "bold"), pady=10)
    go.pack(fill="x", pady=14, **pad)

    logbox = tk.Text(win, bg="#0A1C1E", fg=C["liquen"], font=("Consolas", 9), relief="flat", height=12,
                     wrap="word", highlightthickness=0, padx=10, pady=8)
    logbox.pack(fill="both", expand=True, pady=(0, 20), **pad)
    logbox.tag_config("ok", foreground=C["ambar"])
    logbox.tag_config("error", foreground=C["coral"])

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
        drop_text.config(text=os.path.basename(path), fg=C["ambar"])
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
        args = dict(root=state["root"], icon=state["icon"], soundfont=state["sf2"], install=install.get(), ligero=ligero.get(), fondo=fondo.get(),
                    **{k: e.get().strip() for k, e in fields.items()})

        def worker():
            try:
                apk = port(log=lambda t: events.put((t, None)), **args)
                events.put(("Listo. APK en: " + apk, "ok"))
                if WINDOWS:
                    subprocess.Popen(["explorer", "/select,", apk])
            except PortError as e:
                events.put((str(e), "error"))
            except Exception as e:  # noqa: BLE001 - mostrar cualquier fallo en la ventana
                events.put(("Error inesperado: %r" % e, "error"))
            events.put((None, "fin"))

        threading.Thread(target=worker, daemon=True).start()

    def pump():
        while not events.empty():
            text, tag = events.get()
            if tag == "fin":
                state["busy"] = False
                go.config(text="Crear APK", state="normal")
            else:
                write(text, tag)
        win.after(100, pump)

    if DND_FILES:
        win.drop_target_register(DND_FILES)
        win.dnd_bind("<<Drop>>", lambda e: choose(win.tk.splitlist(e.data)[0]))
    if initial:
        win.after(200, lambda: choose(initial))
    pump()
    win.mainloop()


def main():
    ap = argparse.ArgumentParser(description="Convierte un juego de RPG Maker en APK de Android.")
    ap.add_argument("carpeta", nargs="?", help="carpeta del juego (la que tiene Game.ini) o su .zip")
    ap.add_argument("--sin-interfaz", action="store_true", help="no abrir ventana")
    ap.add_argument("--instalar", action="store_true", help="instalar con adb al terminar")
    ap.add_argument("--autor"), ap.add_argument("--version"), ap.add_argument("--nombre")
    ap.add_argument("--icono", help="PNG para el icono"), ap.add_argument("--soundfont", help=".sf2 para MIDI")
    ap.add_argument("--motor", help="carpeta lib/ con el motor compilado (en vez de descargarlo)")
    ap.add_argument("--ligero", action="store_true", help="ahorrar espacio: Graphics/Audio se leen del APK")
    ap.add_argument("--fondo-icono", choices=sorted(FONDOS_ICONO), default="gris", help="color detrás del icono")
    ap.add_argument("--plantilla", action="store_true", help="APK sin juego para la app del teléfono")
    ap.add_argument("--limpiar", action="store_true", help="borrar APKs viejos y restos de compilación")
    a = ap.parse_args()
    if a.limpiar:
        return limpiar()
    if a.plantilla:
        print("Plantilla lista: " + plantilla(a.motor))
        return
    if not a.sin_interfaz:
        return gui(a.carpeta)
    if not a.carpeta:
        ap.error("falta la carpeta del juego")
    try:
        port(a.carpeta, a.autor, a.version, a.nombre, a.icono, a.soundfont, a.instalar, a.motor, ligero=a.ligero, fondo=a.fondo_icono)
    except PortError as e:
        print("ERROR: " + str(e))
        sys.exit(1)


if __name__ == "__main__":
    main()
