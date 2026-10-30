# Insurgence, RGSS Linker y asignación de entradas

El registro de Detergent se detiene en `RGSS Linker script`, línea 11, con `Failed to load RGSS Linker`. La capa Win32API anterior devolvía 0 para `RGSSLinker_Initialize`; el script requiere 1. No es un diagnóstico de falta de RAM ni del controlador gráfico.

## Compatibilidad aplicada

Se reconoce la combinación concreta RGSS Linker + `RGSSLinker_Initialize`. La inicialización de `RGSS FmodEx.dll` instala un adaptador de audio que conserva los métodos nativos antes de que el juego redefina `Audio`, evitando recursión. El adaptador ofrece reproducción, parada y fundido de BGM/BGS/ME/SE, además de los manejadores básicos que usa el script Audio(FmodEx). Una DLL desconocida no recibe éxito genérico.

El audio se reproduce con mkxp-z. No se carga una DLL de Windows. Los puntos arbitrarios de bucle FMOD, panoramización, frecuencia y canales independientes adicionales no están implementados; los métodos opcionales correspondientes devuelven nil. Los streams FMOD en memoria generan un error explícito. Los bucles del archivo de audio siguen dependiendo del soporte nativo del motor.

Cuando el paquete de scripts hace referencia a MGC_Hmode7 y define `Game_System#hm7`, el lector se mantiene en false para usar mapas planos. No se cambia el valor guardado de la partida ni se modifica el archivo original de scripts. Es un respaldo 2D, no una implementación de H-mode7.

Esta corrección reproduce y prueba el contrato que falla en el registro. No demuestra que todos los mapas, eventos, extensiones o variantes de Insurgence sean compatibles. El fork concreto debe probarse en un dispositivo con sus archivos originales.

## Fuentes primarias

- [JoiPlay, RPG Maker Plugin 1.00.69: corrección de valores de RGSS Linker](https://www.patreon.com/joiplay/posts/joiplay-1-01-52-35594656).
- [Bindings Win32 del proyecto joiplay-mkxp](https://github.com/enumag/joiplay-mkxp/blob/master/binding-mri/win32api-binding.cpp) y [puente FmodEx al audio](https://github.com/enumag/joiplay-mkxp/blob/master/binding-mri/audio-binding.cpp).
- [JoiPlay, Plugin 1.20.00: Insurgence usa H-mode7 desactivado y mapas planos](https://www.patreon.com/joiplay/posts/joiplay-1-11-006-69419140).
- [Contrato original de RGSS Linker y Audio(FmodEx), Nuri Yuri](https://www.pokecommunity.com/threads/rgss-fmodex-extension.349496/).
- [Respaldo del lector hm7 en PokeEssentialsAccess](https://github.com/tiflojuegos-com/PokeEssentialsAccess/blob/main/games/insurgence/hm7.rb).

Kirin documenta que las DLL de Windows pueden requerir implementaciones propias, pero no se encontró una corrección pública específica de este error para trasladar: [ayuda oficial](https://www.playkirin.com/help.html).

## Mapeo

Mando y teclado permite añadir una asignación presionando y soltando su entrada física. Después se busca una tecla o se elige una acción de la app: menú, velocidad/turbo, teclado u ocultar controles. Las entradas de sticks y gatillos esperan a volver al centro antes de abrir el selector. Se conservan las asignaciones existentes de Xbox/Switch y se pueden restablecer por separado el perfil del mando y el teclado.

Se incluyen los botones estándar, C/Z, Guide/Back y Button 1–16. La captura admite otros códigos y ejes que Android entregue a la app. Inicio, encendido y otras entradas reservadas por Android o por el firmware del mando pueden no llegar a la aplicación. El volumen conserva su función del sistema.

Las teclas y ejes se consumen en el menú, y los sticks pueden mover el foco. Confirmar/cancelar usan la distribución del mando; los atajos del juego no se ejecutan detrás del menú. La interfaz conserva la paleta dinámica de Android.

## PSystem_Utilities: invalid multibyte escape

Detergent puede superar RGSS Linker y fallar después al compilar el analizador JSON de `PSystem_Utilities`. Su rango `\x7f-\x9f` usa escapes de bytes incompatibles con UTF-8. `rmxp-script-compat.rb` cambia ese rango a `\u007f-\u009f` únicamente en ese script, antes de evaluarlo. Conserva el texto UTF-8 y el rechazo de controles C1; no cambia globalmente la codificación ni oculta excepciones. La prueba `tests/script_compat_check.rb` comprueba texto acentuado, controles y aislamiento respecto a otros scripts.

La frecuencia de pantalla y los FPS del juego son ajustes distintos. `targetFps=0` en el registro indica la opción Original; para forzar 120 se debe seleccionar 120 en FPS del juego. La petición de refresco se reaplica al recrear la superficie; Android puede seguir limitándola por políticas del dispositivo.

## BGM Memory: Invalid next

El log de Detergent del 30 de octubre llega hasta `BGM Memory:18`. La compatibilidad reemplaza el `next` situado al principio de esa línea por `return`, como salida anticipada de método. El cambio se limita al script `BGM Memory` y a la línea indicada; no se reemplazan los `next` de bucles u otros scripts. Falta confirmar la ejecución con el juego completo.

## Petición explícita de 120 FPS

El nuevo log de Relict sí registra `targetFps=120` y una pantalla que pasa de 120 a 60 Hz. El motor-v14 desactiva la espera adicional de VSync en el hilo OpenGL cuando se elige un objetivo explícito y la restaura al volver a Original. La superficie usa una petición de refresco apropiada para juegos y Android 15+ también recibe la petición en la jerarquía de vistas. Se registra el intervalo de intercambio real. Las políticas de HyperOS/Game Turbo pueden seguir imponiendo un límite externo.

Los cambios de interfaz de 0.10 se entregan para compilación sin pruebas de APK ni ejecución en emulador, a petición del usuario.

## SpriteWindow_sprites:216 — TEMP en Android (0.11)

La captura nueva de Detergent muestra `no implicit conversion of nil into String` en
`SpriteWindow_sprites:216`. El [script público de Essentials de la misma generación](https://github.com/Dordevic/Pokemon-Open-Sky/blob/ad20f2cdd1be8dc1f3d793a04018e23114fd4d0e/Pokemon%20Open%20Sky/Scripts/SpriteWindow_sprites.rb#L216)
contiene exactamente `Dir.chdir(ENV["TEMP"])` en esa línea, dentro de `GifBitmap`.
Android no define esa variable de Windows. Antes de arrancar SDL/Ruby, el lanzador
establece `TEMP`, `TMP` y `TMPDIR` en `Activity.getCacheDir()`: una carpeta existente,
escribible y privada del APK. No se sustituye nil globalmente ni se ocultan errores
de imágenes. Falta confirmar esta ruta con el paquete concreto de Detergent.

La [FAQ oficial de Insurgence](https://p-insurgence.com/FAQ/bugs.html) también documenta
un error distinto de SpriteWindow, `filename is nil`, causado por Graphics/Icons
incompleto. No se confunden ambos fallos ni se eliminan partidas. Las adaptaciones
JoiPlay de Linker, FMOD y mapas planos ya están incluidas; no se encontró un parche
público de Kirin para este nuevo caso.

## Mapeo visual 0.11

La pantalla usa dos columnas con insignia de entrada, flecha y destino. Tiene
pestañas separadas, filas con altura mínima de 48 dp y desplazamiento; con fuentes
ampliadas o pantallas estrechas pasa a una columna para conservar las etiquetas.
Guía/Home ocupa una fila completa. Sticks y cruceta agrupa las cuatro direcciones
de cada control; L3/R3 son pulsaciones separadas de sus ejes. Todas las entradas
pueden recibir teclas del catálogo de PC o acciones de la app (menú, turbo, teclado,
ocultar controles), y el menú sigue recibiendo navegación del mando sin enviarla al juego.

Asignar automáticamente usa una pulsación en la posición derecha del grupo ABXY,
como [Azahar Android](https://github.com/azahar-emu/azahar/blob/master/src/android/app/src/main/java/org/citra/citra_emu/fragments/AutoMapDialogFragment.kt),
para detectar Xbox/Nintendo y aplicar valores estándar tras confirmación de reemplazo.
La hoja se limita al panel lateral y bloquea el foco del fondo. No es una secuencia
de doce pasos. No se copia código de los emuladores.

En Switch, Captura permite detectar su código real en vez de asumir uno universal.
Las entradas adicionales se muestran solo si Android las anuncia o ya tienen una
asignación guardada. Si Home/Guía/Captura está reservado por el sistema, la app no
puede capturarlo. Los perfiles existentes se conservan hasta restablecerlos o aceptar
la asignación automática. No se ejecutaron tests ni APKs/emuladores para esta entrega.

## Motor-v15 y perfil experimental Insurgence (Portémon 0.12)

Este perfil se activa al crear el APK. En PC está en Recursos, **Insurgence y
derivados (experimental)**, o por CLI con `--insurgence`. En Android está en el paso
**Datos**, con el mismo nombre. Es optativo, se guarda en el estado de la pantalla
y se incluye en el registro de construcción. Se escribe `rmxp_insurgence.txt` con
1 o 0 incluso al desactivarlo, para que una reconstrucción no herede una bandera
antigua. Conserva firma y partidas; requiere reconstruir el APK del juego.

### Cambios incluidos

- Motor-v15: un mutex registrado con el GC permite secciones críticas cooperativas
  usadas por `Thread.critical` y `Thread.exclusive`. La lectura es por propietario
  para restaurar correctamente secciones anidadas. No se deshabilita globalmente
  el planificador de MRI ni se simula el método con un no-op.
- `BitmapCache`: la implementación reconocida de `WeakRef` de Ruby 1.8, con
  `@@id_rev_map` y finalizadores `Thread.critical`, se sustituye en memoria por
  `weakref` de la biblioteca de Ruby actual. Se conserva el número de líneas.
  Así no se adquiere un mutex desde esos finalizadores, algo prohibido en MRI moderno.
- APIs retiradas de Ruby: `Array#nitems`, `indexes/indices`, `Hash#index`,
  `Object#type`, `String#each/to_a` y `File/FileTest/Dir.exists?`, solo si faltan.
- `GifBitmap`: para scripts reconocidos con `GifLibrary`, un adaptador usa el
  decodificador libnsgif del motor, incluyendo animación, copias, fotogramas,
  parada y liberación de bitmaps. No carga `gif.dll` ni extrae PNG temporales.
  El motor actual usa una tasa de animación uniforme: no se promete reproducir
  exactamente cada retardo variable de los GIF originales.
- El perfil fuerza el respaldo plano de H-mode7 mediante el lector de
  `Game_System`, conservando la partida sin sobrescribir su valor guardado.
- Mantiene las adaptaciones existentes de Linker/FMOD, TEMP, JSON de
  PSystem_Utilities, BGM Memory, internet y controles de Essentials.

### Qué se pudo trasladar de las referencias

[JoiPlay 1.00.69](https://www.patreon.com/joiplay/posts/joiplay-1-01-52-35594656)
documenta valores correctos de RGSS Linker, métodos Graphics y ajustes de GC.
[JoiPlay 1.20.00](https://www.patreon.com/joiplay/posts/joiplay-1-11-006-69419140)
documenta H-mode7 desactivado, prioridad de recursos locales y tilemap/atlas de
mkxp-z (el motor de Portémon ya se basa en mkxp-z). El soporte de FMOD se basa en
el contrato público de [joiplay-mkxp](https://github.com/enumag/joiplay-mkxp).

La [ayuda de Kirin](https://www.playkirin.com/help.html) explica perfiles de Ruby
1.8/3 y sustituciones de DLL, pero no publica un conjunto de parches de Insurgence
que se pueda incorporar íntegramente. Portémon sigue usando MRI 3.1 con este perfil
de compatibilidad: **no incluye un intérprete Ruby 1.8 adicional ni código privado de Kirin**.
El [BitmapCache público](https://github.com/griest024/essentials-sample-project/blob/master/export/scripts/BitmapCache.rb)
permite verificar la implementación obsoleta y su uso de `Thread.critical`.

Las notas de JoiPlay también mencionan correcciones de Challenge Run sin publicar
su implementación. No se inventa un reemplazo para ese código ni se afirma haberlo
incorporado. Tampoco se ocultan recursos ausentes o dañados: el perfil sigue siendo
experimental, no una garantía de que Insurgence y todos sus forks completen el juego.
Las partidas online, todos los canales FMOD y bucles arbitrarios conservan las
limitaciones documentadas arriba. No se ejecutaron pruebas, APKs ni emuladores.
