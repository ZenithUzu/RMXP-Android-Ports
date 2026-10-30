# Motores en Portémon

Portémon 0.13 hace explícita la selección del runtime RGSS en PC y Android.
No cambia la interfaz de controles, personalización, menús, firmas ni partidas.

| Juego | Runtime | Estado de esta entrega |
| --- | --- | --- |
| RPG Maker XP / Essentials | mkxp-z · RGSS1 | Integrado; conserva el perfil XP y la opción experimental Insurgence |
| RPG Maker VX | mkxp-z · RGSS2 | Selección y empaquetado integrados; falta comprobación de juegos en dispositivo |
| RPG Maker VX Ace | mkxp-z · RGSS3 | Selección y empaquetado integrados; análisis de scripts cifrados RGSS3A |
| RPG Maker 2000/2003 | EasyRPG | Pendiente; no se incluye ese runtime |
| Game Maker 8/8.1 | OpenGMK | Pendiente; no se incluye ese runtime |
| RPG Maker MV/MZ | Entorno web Android | Pendiente; no se incluye ese runtime |

## Uso

- PC: en **Recursos → Motor del juego**, dejar **Automático** o seleccionar XP, VX o VX Ace.
  En CLI: `--motor-juego auto|xp|vx|ace`. `--motor` conserva su significado anterior:
  una carpeta de bibliotecas nativas, no el tipo de juego.
- Android: en **Datos → Motor del juego**, la misma selección. La pantalla de origen
  muestra la detección y el registro de construcción incluye el runtime utilizado.
- Insurgence solo se habilita en XP. Intentar usar ese perfil con VX/Ace mediante
  CLI o directamente mediante el constructor se rechaza antes de empaquetar.

La detección usa Game.ini, la extensión de Scripts, los archivos de datos presentes
y la biblioteca RGSS declarada. Las rutas personalizadas se conservan y se rechazan
las que salen de la carpeta del juego. Las carpetas ambiguas permiten selección
manual, pero el motor seleccionado debe tener scripts o el archivo cifrado adecuado.
Se escriben la versión RGSS en mkxp.json, una sección Game normalizada en la copia
de Game.ini y rmxp_motor.txt para seleccionar los controles iniciales. No se modifican
los originales. Firmas e identificadores de aplicaciones se calculan como antes.

## Controles y partidas

El perfil XP existente se conserva. VX/Ace usan Confirmar con Enter, Cancelar con X,
Acción con Shift, Auxiliar X con A y los hombros con Q/W. Se conservan todas las
asignaciones guardadas por el usuario. Los sticks, gatillos, teclado y acciones de
la app siguen usando el mismo editor. Las partidas .rxdata/.rvdata/.rvdata2 siguen
usando el gestor existente y se conservan al reconstruir con la misma firma.

## Alcance y comprobación

El soporte RGSS1/2/3 ya existe en [mkxp-z](https://github.com/mkxp-z/mkxp-z).
Esta entrega conecta su selección, validación y empaquetado en ambas herramientas;
no es un motor nuevo ni soporte universal de plugins o DLL de Windows.
El analizador lee RGSSAD v1 y RGSS3A v3 sin cargar todo el archivo de recursos a RAM,
sin modificarlo y sin necesitar ejecutar el juego. La lectura se basa en el formato
que ya implementa `android/app/jni/mkxp-z/src/crypto/rgssad.cpp`.

Por petición del usuario no se ejecutaron tests, APKs ni emuladores. Actions compila
la plantilla y el constructor. La compatibilidad real de cada juego queda pendiente
de su comprobación en el teléfono.
