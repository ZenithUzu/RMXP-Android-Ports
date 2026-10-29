**Español** | [English](README.en.md)

# Portémon · PC

> Antes RMXP-Android-Ports. Portémon mantiene la carpeta de datos `RMXP-Android-Ports` y los perfiles existentes para seguir usando las mismas firmas. La interfaz ofrece un formulario desplazable y los registros de construcción y Android al lado. El APK se sigue guardando; su carpeta se abre solo al pulsar **Ver APK…**.

Convierte un fangame de RPG Maker XP, VX o VX Ace (por ejemplo, uno hecho con
Pokémon Essentials) en una **app de Android propia**: con su nombre, su icono
y su firma. No hace falta JoiPlay ni otra app intermedia.

¿Sin PC? Usa la app compañera [**Portémon para Android**](https://github.com/ZenithUzu/RPGMK-AndroidPort-Apk): arma el APK desde el mismo teléfono, con el mismo motor y la misma firma.

En Windows, arrastras la carpeta del juego sobre `Portar.bat`, revisas los
datos y presionas **Crear APK**. Si el teléfono está conectado, se instala
solo.

> **Estado: experimental y en pruebas.** Crear un APK no garantiza que el juego sea compatible. El rendimiento depende del juego, sus scripts y el dispositivo.

**[Inicio rápido](#cómo-portar-un-juego) · [Requisitos](#lo-que-necesitas-una-sola-vez) · [Diagnóstico](#si-algo-falla) · [Créditos](#créditos) · [Licencia](#licencia)**

## Sobre el proyecto

Proyecto personal de **ZenithUzu**, desarrollado **por pasatiempo y sin fines de lucro** para aprender, experimentar y facilitar la creación de ports de RPG Maker para Android. Se mantiene en el tiempo libre; las actualizaciones y la atención de errores dependen de esa disponibilidad.

Se apoya en motores, bibliotecas y aportaciones de la comunidad. Los créditos de esos proyectos se conservan más abajo.

### Uso de inteligencia artificial

En el desarrollo se han utilizado **ChatGPT (OpenAI)** y **Claude (Anthropic)** como herramientas de apoyo para programar, investigar errores, proponer mejoras, preparar pruebas y redactar documentación.

La IA puede equivocarse: su participación no garantiza que el código sea correcto, seguro ni compatible con todos los juegos. Las decisiones del proyecto y la comprobación de los cambios siguen siendo responsabilidad de quienes lo mantienen. Las pruebas automáticas complementan las pruebas en dispositivos reales; no las sustituyen.

Estas herramientas no son autoras de los motores ni de las bibliotecas de terceros. Su mención tampoco implica afiliación, patrocinio o respaldo de OpenAI o Anthropic.

## Qué trae la app

| | |
|---|---|
| **Pantalla** | Sin barras de Android. *Estirar imagen* de 0 % (forma original) a 100 % (llena todo); un punto intermedio usa más pantalla sin deformar tanto. Las opciones de "tamaño de ventana" de los juegos se ignoran, porque en el teléfono no aplican. |
| **Hz, FPS y medidor** | Elige la frecuencia de pantalla y el objetivo de FPS del juego, incluidos 60/120 FPS. El objetivo no garantiza los FPS reales ni equivale a los Hz de la pantalla. *Medidor de rendimiento*: FPS, velocidad, RAM y CPU de la app y batería, en el color que quieras; se arrastra a cualquier lugar. |
| **Velocidad rápida hasta x10** | Botón de avance rápido en pantalla; la velocidad (x2 a x10) se elige en el menú. |
| **Menú de opciones** | Botón Atrás del teléfono o Select en un mando (el botón ☰ se puede mostrar en *Editar controles*). Permite elegir si el juego y su música se pausan al abrir el menú o el editor, o si el juego continúa activo. |
| **Controles** | Cruceta segmentada y botones translúcidos personalizables. En *Editar controles* puedes añadir, borrar, mover y mapear botones (todas las teclas, más teclado, turbo y menú), y cambiar su color y forma. Tamaño (pequeño, mediano, grande) y opacidad (25–100 %) con medidas fijas, iguales para todos o por botón; al moverlos se alinean solos. **Teclado de PC en pantalla** (con Borrar, Enter y flechas) que aparece con su botón o cuando el juego pide escribir; tres tamaños. *Mando y teclado*: asignación en el panel lateral, con perfiles Xbox/Switch, cruceta, ambos sticks y gatillos. Los controles se ocultan solos al conectar un mando o teclado. |
| **Internet** | HTTP y HTTPS nativos (HTTPLite), y la biblioteca de Ruby incluida (`net/http`, `socket`, `uri`, `json`…) para juegos que se conectan en línea o en red local. Los fangames viejos que usan `wininet.dll` se redirigen solos. |
| **Actualizaciones seguras** | La versión nueva se prepara aparte y solo reemplaza a la vieja cuando está completa. Las partidas nunca se borran ni se sobrescriben. |
| **Partidas** | *Exportar* copia tus partidas a una carpeta del teléfono; *Importar* trae las de JoiPlay, Kirin o la PC (`.rxdata`, `.rvdata`, `.rvdata2`) y guarda la anterior como respaldo. Con permiso, guarda además una copia en la carpeta **RPG Maker/<juego>/** del teléfono (junto a Download), que sobrevive a desinstalar. |
| **Juegos viejos** | Parche `Win32API` para Essentials v16–v18: teclado, leer `Game.ini`, tamaño de ventana y avisos de Windows responden sin error. |
| **Más** | Exportar/importar el perfil de controles, gestor de partidas (exportar, respaldar o borrar una), vibración al tocar (se puede apagar), varios juegos a la vez en la PC y aviso de versión nueva. |
| **Ahorrar espacio** | Opcional (experimental): las imágenes y el audio se leen directo del APK en vez de copiarse, así el juego no ocupa el doble en el teléfono. |
| **Música MIDI** | FluidSynth incluido (desde `motor-v10`): los juegos con `.mid` suenan si hay un SoundFont `.sf2`. |
| **Firma del autor** | Se genera una llave por juego; el certificado usa los datos indicados para el autor. No acredita su identidad ni convierte el port en oficial. |

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
2. Arrastra la carpeta del juego (la que tiene `Game.ini`) **o su `.zip`** sobre **`Portar.bat`**.
   También puedes abrir `Portar.bat` y usar **Elegir carpeta** o **Elegir .zip**.
3. Revisa lo que encontró la herramienta:
   - **Revisión**: avisa si el juego usa cosas de Windows que no existen en
     Android (por ejemplo, llamadas `Win32API` o videos `.wmv`).
   - **Nombre, Autor y Versión**: los saca de `Game.ini` y del Léeme. Si el
     autor no aparece, escríbelo tú.
   - **Icono** (PNG) y **SoundFont** (`.sf2`, solo para juegos con música MIDI): opcionales. Si el juego ya trae un `.sf2`, se usa solo. **Fondo del icono**: gris, negro o blanco.
4. Presiona **Crear APK**. La primera vez tarda unos minutos porque Gradle
   descarga lo que necesita; las siguientes, mucho menos.

El APK queda en `C:\Users\<tú>\RMXP-Android-Ports\apks\`. Las copias de trabajo se borran solas al terminar.

**`Limpiar.bat`** muestra un menú y una vista previa con rutas y espacio recuperable. Puedes limpiar APK anteriores (conservando el más reciente de cada juego), archivos `.apk.part` y motores antiguos; los archivos de compilación de Gradle son opcionales. Solo elimina al escribir `SI`. Conserva copias de juegos, ZIP extraídos, firmas, partidas, registros y el motor actual. Si otra ventana está creando un APK, la limpieza se detiene. Los errores se muestran sin contar esos archivos como eliminados.

## Probar el juego y ver su log desde la PC

La interfaz separa **Compilación** y **Log del juego**, con los datos del APK a la
izquierda. Conecta un solo teléfono por USB, activa la depuración USB y acepta
su aviso de autorización. Deja marcadas **Instalar y abrir al terminar (USB)** y
**Capturar log del juego al abrirlo**.

Al terminar, el juego se abre y su registro aparece en **Log del juego**. Se
captura desde el arranque y continúa si la app se cierra o reinicia. El archivo
se guarda automáticamente en `%USERPROFILE%\RMXP-Android-Ports\registros\`.
**Abrir y capturar** permite repetir la prueba sin compilar; **Detener** termina
la captura y **Guardar copia…** exporta el TXT mientras juegas o después del
cierre. Se registra solo el UID del juego seleccionado; no se vacía el logcat
del teléfono. Si hay muchas líneas, la vista se limita y el archivo conserva
el registro completo de la captura.

La PC necesita que el teléfono siga conectado para recibir líneas nuevas.
El menú del juego mantiene su opción **Guardar registro (log)** sin cable.

Consulta también la [comparación entre PC y Android](docs/paridad-pc-android.md).

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
  usa **Log del juego → Abrir y capturar → Guardar copia…**. También puedes exportarlo desde el menú del juego. El registro ayuda a investigar; no siempre identifica por sí solo la causa.
- **Música MIDI sin sonido**: el motor ya trae FluidSynth (desde `motor-v10`), pero necesita un SoundFont `.sf2`: si el juego no trae uno, elígelo antes de crear el APK.

También puedes revisar un juego sin crear nada:

```
python tools\rmxp_analyze.py "C:\Juegos\Mi Fangame"
```

### Reportar un problema

Abre un [issue](https://github.com/ZenithUzu/RMXP-Android-Ports/issues) e incluye:

- Juego y versión; versión del motor y herramienta usada para crear el APK.
- Modelo del teléfono y versión de Android.
- Pasos para reproducirlo, resultado esperado y lo que ocurrió.
- Registro y, si ayuda, captura o video. Para rendimiento, indica zona, objetivo de FPS, Hz actuales y estado del turbo.

Antes de adjuntar archivos, revisa que no contengan datos personales. No publiques llaves de firma, contraseñas ni juegos completos. Para comprobar cambios, consulta la [guía de pruebas](docs/pruebas-ports.md) y la [auditoría de interfaz](docs/auditoria-interfaz.md).

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
- [**FluidSynth**](https://www.fluidsynth.org/) (LGPL-2.1-or-later): música MIDI. Se carga como librería aparte (`libfluidsynth.so`).
- [json5pp](https://github.com/kimushu/json5pp) (MIT): lee `mkxp.json`. HTTPLite (incluido en mkxp-z): internet.
- Idea de los controles en pantalla: Kirin y JoiPlay (solo la idea; no se usa su código).
- [**Pokémon Essentials**](https://github.com/Maruno17/pokemon-essentials): los parches están pensados para los juegos hechos con él.

## Licencia

El código de este proyecto se publica bajo [GPL-2.0-or-later](LICENSE). Los componentes de terceros conservan sus licencias y avisos; revisa también las condiciones de las dependencias al redistribuir una compilación. El carácter no lucrativo del proyecto no añade una prohibición de uso comercial a la GPL.

**Los juegos y sus recursos no están incluidos ni quedan cubiertos por esta licencia.** Usa juegos que tengas derecho a portar y respeta los permisos de sus autores antes de distribuir un APK.

Este es un proyecto independiente, sin afiliación ni respaldo de Nintendo, The Pokémon Company, Game Freak, los responsables de RPG Maker ni los autores de los fangames. Las marcas y recursos pertenecen a sus respectivos titulares.

El software se ofrece sin garantía, según los términos de su licencia. Haz copias de tus partidas y firmas antes de probar actualizaciones.
