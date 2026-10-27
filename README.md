**Español** | [English](README.en.md)

# RMXP-Android-Ports

Convierte un fangame de RPG Maker XP, VX o VX Ace (por ejemplo, uno hecho con
Pokémon Essentials) en una **app de Android propia**: con su nombre, su icono
y su firma. No hace falta JoiPlay ni otra app intermedia.

En Windows, arrastras la carpeta del juego sobre `Portar.bat`, revisas los
datos y presionas **Crear APK**. Si el teléfono está conectado, se instala
solo.

## Qué trae la app

| | |
|---|---|
| **Pantalla completa** | Sin barras de Android. *Original* respeta la forma del juego; *Llenar* estira hasta los bordes. |
| **Hz y FPS** | Elige los Hz de la pantalla (Auto, 60 o el máximo) y si el juego corre a sus propios FPS o a los de la pantalla (60 Hz = 60 fps, 120 Hz = 120 fps). Medidor de FPS opcional. |
| **Velocidad rápida hasta x10** | Botón de avance rápido en pantalla; la velocidad (x2 a x10) se elige en el menú. |
| **Menú de opciones** | Botón Atrás del teléfono o Select en un mando (el botón ☰ se puede mostrar en *Editar controles*). Pausa el juego mientras está abierto. |
| **Controles** | Estilo Kirin: botones translúcidos con etiqueta y tecla. En *Editar controles* puedes añadir, borrar, mover y mapear botones (todas las teclas, más teclado, turbo y menú), y cambiar su color, forma, tamaño y opacidad. *Mando y teclado*: mapeo propio para mandos, con valores cómodos por defecto. Los controles se ocultan solos al conectar un mando o teclado. |
| **Internet** | HTTP y HTTPS nativos. Los fangames viejos que se conectan con `wininet.dll` se redirigen solos a la conexión de Android. |
| **Actualizaciones seguras** | La versión nueva se prepara aparte y solo reemplaza a la vieja cuando está completa. Las partidas nunca se borran ni se sobrescriben. |
| **Firma del autor** | Cada juego recibe su propia firma, a nombre de su creador (se lee del Léeme o Readme del juego). |

## Lo que necesitas (una sola vez)

1. **Python 3.8 o más nuevo**: [python.org](https://www.python.org/downloads/).
   Al instalar, marca *Add python.exe to PATH*.
2. **Android Studio**: [developer.android.com/studio](https://developer.android.com/studio).
   Ábrelo una vez para que descargue el SDK. Trae Java y `adb`.
3. Para instalar directo en el teléfono: activa la **depuración USB**
   (Ajustes → Acerca del teléfono → toca 7 veces "Número de compilación" →
   Opciones de desarrollador → Depuración USB) y conecta el cable.

No necesitas compilar nada en C++. La primera vez, la herramienta descarga el
motor ya compilado desde las versiones (releases) de este repositorio.

## Cómo portar un juego

1. Descarga este repositorio (botón **Code → Download ZIP**, o con GitHub Desktop).
2. Arrastra la carpeta del juego (la que tiene `Game.ini`) sobre **`Portar.bat`**.
   También puedes abrir `Portar.bat` y usar **Elegir carpeta**.
3. Revisa lo que encontró la herramienta:
   - **Revisión**: avisa si el juego usa cosas de Windows que no existen en
     Android (por ejemplo, llamadas `Win32API` o videos `.wmv`).
   - **Nombre, Autor y Versión**: los saca de `Game.ini` y del Léeme. Si el
     autor no aparece, escríbelo tú.
   - **Icono** (PNG) y **SoundFont** (`.sf2`, solo para juegos con música MIDI): opcionales. Si el juego ya trae un `.sf2`, se usa solo.
4. Presiona **Crear APK**. La primera vez tarda unos minutos porque Gradle
   descarga lo que necesita; las siguientes, mucho menos.

El APK queda en `C:\Users\<tú>\RMXP-Android-Ports\apks\`.

## Cuando el fangame saca una versión nueva

Pásale la carpeta nueva a `Portar.bat` igual que la primera vez. La
herramienta reconoce el juego, usa **la misma firma** y genera un APK que se
instala **encima** del anterior. En el teléfono:

- La versión nueva se extrae en una carpeta aparte. Solo cuando termina,
  reemplaza a la vieja.
- Todo lo que el jugador creó (partidas, configuraciones) se pasa a la versión
  nueva sin sobrescribirlo.
- Si la app se cierra a la mitad, el juego viejo sigue intacto y lo reintenta
  al abrirla otra vez.

> **Importante:** guarda una copia de `C:\Users\<tú>\RMXP-Android-Ports\juegos\`.
> Ahí está la firma de cada juego. Sin ella, Android no deja instalar una
> versión nueva encima de la anterior, y habría que desinstalar (eso borra las
> partidas). La herramienta nunca desinstala nada por su cuenta.

## Sobre la firma

Android exige que cada app vaya firmada con una llave, y que cada
actualización use **la misma** llave. La herramienta crea una llave propia
por juego y pone en el certificado el nombre del autor y del juego (por
ejemplo `CN=NombreDelAutor, O=Pokémon Ejemplo`). También el id de la app usa
el autor: `com.nombredelautor.pokemonejemplo`.

No es la llave privada del creador: esa solo la tiene él. Si el creador
publica su propio APK oficial, ese y el tuyo no se pueden instalar uno encima
del otro.

## Si algo falla

- **"Probablemente NO funciona sin parches"**: el juego usa funciones de
  Windows. Muchos juegos con Essentials v20 o más nuevo funcionan sin cambios;
  los de Essentials v16 a v18 suelen necesitar parches `.rb` (puedes ponerlos
  en `android/patches/`, se cargan antes del juego).
- **El juego se cierra en el teléfono**: conecta el teléfono y en una terminal
  ejecuta `adb logcat -s mkxp-z SDL`. Ese registro dice la causa.
- **Música MIDI sin sonido**: elige un SoundFont `.sf2` antes de crear el APK.

También puedes revisar un juego sin crear nada:

```
python tools\rmxp_analyze.py "C:\Juegos\Mi Fangame"
```

## Para desarrolladores

```
Portar.bat                 lanzador para Windows (interfaz)
tools/rmxp_port.py         la herramienta: revisa, prepara, firma, compila, instala
tools/rmxp_analyze.py      revisión de compatibilidad (solo lee)
android/                   proyecto de Android Studio (app + motor en C++)
android/patches/*.rb       parches de Ruby que se cargan antes del juego
.github/workflows/motor.yml  compila el motor y lo publica como release
```

- La parte nueva de la app está en `android/app/src/main/java/com/hatkid/mkxpz/OptionsMenu.java`,
  `MainActivity.java` (actualización segura) y `android/app/src/main/res/` (menú y controles).
- La velocidad rápida, la pausa y los filtros en vivo están en
  `android/app/jni/mkxp-z/src/display/graphics.cpp` (bloque `__ANDROID__`).
- **Compilar el motor tú mismo** (Linux o WSL): `cd android && bash tools/setup.sh && bash tools/build.sh --solo-motor`.
  Sale en `android/build/motor-android.zip`; úsalo con `python tools/rmxp_port.py <juego> --motor <carpeta lib>`.
- **Publicar un motor nuevo**: en GitHub, Actions → *Motor de Android* → *Run workflow*,
  con el mismo nombre que `MOTOR_VERSION` en `tools/rmxp_port.py`.

## Créditos

Este proyecto se apoya en el trabajo de:

- [**mkxp-z**](https://github.com/mkxp-z/mkxp-z): el motor que ejecuta los juegos de RPG Maker (GPL-2.0-or-later). Basado en [mkxp](https://github.com/Ancurio/mkxp) de Ancurio.
- [**thehatkid/mkxp-z-android**](https://github.com/thehatkid/mkxp-z-android): el port de mkxp-z a Android, con los controles en pantalla.
- [**BookerRues9/mkxp-z-android-reworked**](https://github.com/BookerRues9/mkxp-z-android-reworked): arreglos y guía de compilación.
- [**vrcxz/mkxp-z-android-packager**](https://github.com/vrcxz/mkxp-z-android-packager): meter el juego dentro del APK, scripts de compilación y parches para Essentials. La carpeta `android/` parte de este proyecto.
- [**SDL**](https://www.libsdl.org/), [**Ruby**](https://www.ruby-lang.org/), [OpenAL Soft](https://github.com/kcat/openal-soft), [PhysicsFS](https://github.com/icculus/physfs), [OpenSSL](https://www.openssl.org/), [Pixman](https://pixman.org/), [libogg, libvorbis y libtheora](https://xiph.org/), [uchardet](https://www.freedesktop.org/wiki/Software/uchardet/) y [libiconv](https://www.gnu.org/software/libiconv/): las librerías que usa el motor.
- [**Pokémon Essentials**](https://github.com/Maruno17/pokemon-essentials): los parches están pensados para los juegos hechos con él.

Los tres proyectos de Android no indican una licencia propia; como son
derivados de mkxp-z, se distribuyen aquí bajo su misma licencia GPL.

## Licencia

[GPL-2.0-or-later](LICENSE), la misma de mkxp-z. Puedes usar, cambiar y
compartir este proyecto, siempre que compartas también el código de tus
cambios.

**Los juegos no están incluidos ni cubiertos por esta licencia.** Repartir el
port de un fangame necesita el permiso de su autor, y los fangames de Pokémon
usan propiedad de Nintendo, The Pokémon Company y Game Freak. Usa la
herramienta con juegos que tengas derecho a portar.
