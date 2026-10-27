**Español** | [English](README.en.md)

# RMXP-Android-Ports

Herramientas para portar juegos de RPG Maker XP (sobre todo fangames de
Pokémon Essentials, como Pokémon Uranium) a Android como **APK propio**, sin
JoiPlay ni apps intermedias.

## Cómo funciona la idea

Un juego de RPG Maker XP no es código que Android Studio pueda "entender":
es un montón de **scripts en Ruby** (`Data/Scripts.rxdata`) más mapas,
imágenes y audio. Traducir todo eso a Java/Kotlin a mano sería rehacer el
juego (eso hace, por ejemplo, [Project Uranium Godot](https://github.com/acedogblast/Project-Uranium-Godot)).

El camino práctico es otro:

1. Usar **mkxp-z**, un motor libre que ejecuta juegos de RPG Maker
   (XP/VX/VX Ace) sin Windows. Pokémon Essentials v20+ ya lo usa como motor
   oficial en PC.
2. Compilar mkxp-z para Android (C++ con el NDK) y **meter el juego dentro
   del APK**, con su propio nombre, icono y controles táctiles.
3. Arreglar con **parches `.rb`** que se cargan antes del juego todo lo que
   dependía de Windows (por ejemplo `Win32API`).

## Lo que ya existe (no hay que empezar de cero)

| Proyecto | Qué aporta |
|---|---|
| [mkxp-z](https://github.com/mkxp-z/mkxp-z) | El motor (licencia GPL-2.0). |
| [thehatkid/mkxp-z-android](https://github.com/thehatkid/mkxp-z-android) | Primer port nativo de mkxp-z a Android. Lee el juego desde `/storage/emulated/0/mkxp-z`. |
| [BookerRues9/mkxp-z-android-reworked](https://github.com/BookerRues9/mkxp-z-android-reworked) | Versión con arreglos y guía de compilación (Ubuntu + NDK 23). |
| [vrcxz/mkxp-z-android-packager](https://github.com/vrcxz/mkxp-z-android-packager) | Ya mete un juego dentro del APK con un script (`tools/build.sh juego.zip`) e incluye parches para Essentials v21. |

Este repo se apoya en esos proyectos y añade lo que les falta.

## Herramienta 1: analizador de compatibilidad

`tools/rmxp_analyze.py` revisa un juego **antes** de compilar y te dice qué
va a fallar en Android. Solo lee, no cambia nada.

```
python tools/rmxp_analyze.py "C:\Juegos\Pokemon Uranium"
```

Revisa:

- Versión de RGSS (XP, VX o VX Ace) y versión de Essentials.
- Lee los scripts aunque estén dentro de `Game.rgssad` (cifrado de XP/VX).
- Lista cada llamada a `Win32API` (funciones de Windows que no existen en Android).
- Audio y video que mkxp-z no reproduce (WMA, AVI, WMV…) y MIDI sin SoundFont.
- DLLs extra, falta de carpeta `Fonts` y tamaño del juego.

Solo necesita Python 3.8 o más nuevo. Con `--json` da el reporte en JSON.

## Siguientes pasos

- [ ] Probar el analizador con juegos reales (Uranium, juegos con Essentials v16–v21).
- [ ] Parches `.rb` para las llamadas `Win32API` más comunes (teclado, ventana, `.ini`).
- [ ] Generar APK por juego: nombre, icono e id de paquete propios.
- [ ] Controles táctiles en pantalla y mapeo de mando.

## Aviso legal

La herramienta es libre, pero los juegos no: portar y repartir un fangame
necesita permiso de sus autores, y los fangames de Pokémon usan propiedad de
Nintendo (Uranium fue retirado por eso). Úsala con juegos que tengas
derecho a portar.
