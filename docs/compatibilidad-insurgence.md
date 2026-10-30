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
