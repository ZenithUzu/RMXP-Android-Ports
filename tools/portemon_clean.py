"""Plan explícito de limpieza. Nunca incluye juegos, ZIP extraídos, firmas ni logs."""
import os
import re
import shutil
from dataclasses import dataclass


@dataclass(frozen=True)
class Candidate:
    path: str
    category: str
    size: int


def linked(path):
    return os.path.islink(path) or getattr(os.path, "isjunction", lambda p: False)(path)


def size(path):
    if linked(path):
        return 0
    if os.path.isfile(path):
        return os.path.getsize(path)
    total = 0
    for root, dirs, files in os.walk(path, followlinks=False):
        dirs[:] = [d for d in dirs if not linked(os.path.join(root, d))]
        for name in files:
            p = os.path.join(root, name)
            if not linked(p):
                total += os.path.getsize(p)
    return total


def plan(home, android, motor_version, include_build=False):
    result = []

    def add(path, category):
        if os.path.exists(path) and not linked(path):
            result.append(Candidate(path, category, size(path)))

    apks = os.path.join(home, "apks")
    games = {}
    if os.path.isdir(apks) and not linked(apks):
        for name in sorted(os.listdir(apks)):
            path = os.path.join(apks, name)
            if not os.path.isfile(path) or linked(path):
                continue
            if name.lower().endswith(".apk.part"):
                add(path, "Descargas o APK incompletos")
                continue
            match = re.fullmatch(r"(.+) v[^ ]+\.apk", name, re.I)
            if match:
                games.setdefault(match[1].casefold(), []).append(path)
        for paths in games.values():
            paths.sort(key=lambda p: (os.path.getmtime(p), p), reverse=True)
            for path in paths[1:]:
                add(path, "APK anteriores (se conserva el más reciente)")
    engines = os.path.join(home, "motor")
    if os.path.isdir(engines) and not linked(engines):
        for name in sorted(os.listdir(engines)):
            if re.fullmatch(r"motor-v\d+", name) and name != motor_version:
                add(os.path.join(engines, name), "Motores anteriores")
    if include_build:
        add(os.path.join(android, "app", "build"), "Archivos regenerables de Gradle")
    return result


def execute(candidates, log):
    freed = 0
    failures = 0
    for item in candidates:
        try:
            if linked(item.path):
                raise OSError("ahora es un enlace; se omite")
            if not os.path.exists(item.path):
                continue
            before = size(item.path)
            if os.path.isdir(item.path):
                shutil.rmtree(item.path)
            else:
                os.remove(item.path)
            freed += before
            log("Eliminado: " + item.path)
        except OSError as error:
            failures += 1
            log("No se pudo eliminar %s: %s" % (item.path, error))
    return freed, failures
