# Cómo actualizar y probar los ports

## Actualizar

En GitHub Desktop, selecciona `RMXP-Android-Ports`, cambia a `main`, pulsa
**Fetch origin** y después **Pull origin** si aparece. No se necesita un Pull
Request. Cierra la ventana de la herramienta y vuelve a abrir `Portar.bat`.

Espera a que **Motor de Android** termine correctamente y exista `motor-v11`.
La PC lo descarga la primera vez que compilas. Para Android, espera a que el
flujo **APK** termine y descarga `RPGMK-Port.apk` de la release `ultima` de
`RPGMK-AndroidPort-Apk`. Instálalo como actualización, conservando los datos.
El constructor debe ser la versión 0.2 o posterior para estas pruebas.

No instales `plantilla.apk` como si fuera Relict: es un componente del constructor,
sin los archivos del juego. La corrección del motor requiere volver a crear el
APK de Relict; actualizar solamente la herramienta no modifica un APK anterior.

## PC: crear y capturar

1. Exporta tu partida desde el juego como respaldo. Conserva su firma original.
2. Conecta un solo teléfono por USB, activa Depuración USB y acepta la autorización.
3. Abre `Portar.bat` y elige la carpeta completa de Relict o el ZIP completo,
   con sus gráficos y música. Conserva nombre y firma para actualizar el juego.
4. Deja activadas **Instalar y abrir al terminar (USB)** y **Capturar log del juego al abrirlo**.
5. Pulsa **Crear APK**. Debe aparecer el resultado en **Compilación** y después
   abrirse el juego y la pestaña **Log del juego**.
6. Carga la partida, habla con Mesprit y vende un objeto del que tengas una unidad.
   Repite con un objeto del que tengas varias unidades y prueba cancelar la venta.
7. Comprueba que la cantidad y el dinero cambian correctamente y que puedes volver
   a hablar con Mesprit y salir de la tienda sin cierre.
8. Pulsa **Detener** y **Guardar copia…** para compartir el TXT. El original también
   queda en `%USERPROFILE%\RMXP-Android-Ports\registros\`.
9. Usa **Abrir y capturar** para repetir sin compilar otra vez. Si el juego se
   cierra, deja la captura activa y vuelve a abrirlo: el UID mantiene el filtro.

Quita la carpeta temporal `Data/Scripts/998_RMXP_Ventas` del juego de prueba al
evaluar el rendimiento normal. El log de PC ya captura los errores del motor;
el diagnóstico anterior genera muchas líneas y ralentiza la tienda.

## Android: comprobar el constructor

1. Actualiza a `RPGMK-Port.apk` 0.2 y selecciona el juego desde ZIP; después repite
   con una carpeta si usas ese modo. No uses el ZIP reducido sin Graphics/Audio.
2. Cambia nombre/autor/versión, gira el teléfono en el paso de datos y abre el
   selector de icono. Al volver, los campos deben conservarse.
3. Selecciona un SF2 real. Prueba también un archivo de texto renombrado a `.sf2`:
   debe mostrar un error, permitir exportar el registro y dejarte elegir otro.
4. Genera una vez para tu teléfono y otra con **APK universal**. Ambos deben
   instalarse en ese teléfono; el universal normalmente ocupa más espacio.
5. Importa la firma que usaste antes si vienes de PC. La instalación del juego
   debe ser una actualización y conservar la partida. Si Android informa otra
   firma, cancela la instalación y comprueba la firma importada.
6. Repite la venta con Mesprit. Para fallos del juego usa **Atrás → Guardar registro (log)**.
   Para fallos al crear el APK usa **Guardar registro de creación** en el constructor.

## Qué compartir

- Si se creó desde PC o Android y qué opciones estaban activadas.
- El objeto, la cantidad, el momento del fallo y si ya se modificaron dinero/inventario.
- El TXT del log correspondiente. Si hay un mensaje en pantalla, su texto o una captura.
- Si no falla, confirma las pruebas de venta individual, cantidad múltiple y cancelación.

La compilación correcta confirma que el código construye; no sustituye la prueba
de la venta en el teléfono ni demuestra que todos los fangames funcionen.


## Menú, volumen y ritmo del juego (motor-v12)

Recrea el APK y actualiza encima usando la misma firma. Los APK ya instalados
no cambian al hacer Pull.

1. En **Controles**, deja **Mantener el juego activo** encendido: tanto el menú
   como el editor deben mostrar el juego moviéndose detrás. El estado dice
   **Juego activo**. Apágalo: ambos deben dejar la imagen quieta y el menú decir
   **En pausa**. Cierra y abre la app: debe conservar la elección.
2. Repite con avance rápido activo. Sal a otra app y vuelve con el menú y el
   editor abiertos. Fuera de la app siempre debe pausarse; al volver debe respetar
   la preferencia. Al cerrar menú/editor debe recuperar velocidad normal o turbo.
3. Sube y baja volumen durante el juego, en cada pestaña del menú y en el editor.
   Debe cambiar el volumen multimedia y no enviar esas teclas al juego.
4. Con turbo apagado, alterna **Igual a la pantalla → Los del juego**. Debe
   recuperar los FPS que pide el juego, incluso si los cambió mientras estaba
   forzado. Prueba la misma ruta y menú antes/después, durante un minuto.
5. En Añil cambia VSync, cierra y abre dos veces. Exporta el log desde **Partidas**
   si aparece Zlib o un error JSON. El registro identifica la versión del APK y
   si se restauró la configuración incluida. No se rescatan opciones de pantalla
   desde un archivo dañado.
6. Para los dos iconos en volumen por aplicación: indica modelo del teléfono,
   Android/HyperOS/MIUI y manda captura con el panel expandido. Comprueba si ambos
   deslizadores cambian el mismo audio y si aparecen también tras reiniciar el
   teléfono. El log actual no permite atribuirlo a una sesión de audio concreta.

Hz de pantalla, FPS solicitados por el juego y VSync son controles distintos.
El arreglo de retorno a FPS originales no garantiza FPS constantes en cada zona.
Los logs muestran tirones con y sin GC; hace falta medir una ruta repetible para
atribuir el trabajo a scripts, carga de recursos o renderizado.

## Interfaz revisada del runtime

- El interruptor **Pausar en menú y editor** está al principio, antes de las pestañas.
  Encendido pausa; apagado conserva el juego activo detrás. También debe respetarlo
  el editor. La preferencia anterior se conserva, con el texto ahora expresado como pausa.
- Edita un botón de una esquina, cambia su tamaño y vuelve a seleccionarlo. La barra
  principal y las acciones de selección deben seguir ancladas. Ajustes, edición de
  un botón y Mando y teclado abren el panel lateral con la paleta del sistema.
- Abre el teclado: desaparece el mando táctil. Prueba ABC, símbolos y F1–F12,
  Ctrl/Alt, flechas verticales, Home/End y borrar mantenido. Cerrar con ▼, Atrás o
  tocar fuera devuelve los controles y libera las teclas; el toque de cierre no
  debe pulsar también un botón del juego.
- En pantalla de 120 Hz, selecciona FPS **120** y luego **60** con turbo apagado.
  Repite con Hz en 60. El objetivo de FPS es independiente de la frecuencia. Anota
  los Hz actuales, los FPS medidos, batería y temperatura; exporta el log. Si se
  solicitan 120 Hz y Android mantiene 60, no es prueba de un límite de FPS del motor.
- Activa/desactiva vibración y prueba cruceta, botones y teclado. Un emulador no
  puede confirmar la sensación física del vibrador.
- Constructor Android 0.3: **Crear APK** no abre el instalador. Después usa
  **Instalar APK creado** y cancela; repetir instalar no debe mostrar creación ni
  modificar el archivo. Guardar, Crear otra app y Cambiar datos tienen acciones
  independientes. Importa la firma original para actualizar un juego instalado.
