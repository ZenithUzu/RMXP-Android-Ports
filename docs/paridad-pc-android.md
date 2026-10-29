# PC y Android: comparación de las herramientas

Revisión del 28 de octubre de 2026, tras actualizar ambas herramientas en `main`.
Las funciones principales están alineadas. Las diferencias de prueba y de cola
se mantienen según la plataforma.

| Función | PC | RPGMK-AndroidPort-Apk 0.2 |
| --- | --- | --- |
| Importar carpeta o ZIP | Sí | Sí, sin copiar todo el origen |
| Nombre, autor, versión, icono y fondo | Sí | Sí; se restauran al recrearse la pantalla |
| Firma persistente por juego | Sí | Sí; importa y exporta la firma de PC |
| Ahorrar espacio | Sí | Sí |
| SoundFont del juego o externo | Ambos | Ambos; valida la cabecera del externo |
| APK universal | Incluye las arquitecturas del motor | Opción universal o APK pequeño para este teléfono |
| Configuración con comentarios y comas finales | Sí; conserva cadenas literales | Sí; conserva cadenas literales |
| Registro de creación | Pestaña Compilación | Guardar registro de creación |
| Log del juego | Captura en vivo por USB, aislada por UID, con guardado automático | El APK generado exporta su propio log desde el menú |
| Crear varios juegos en cola | Sí | Un juego por proceso |

## Motor y plantilla

PC solicita `motor-v11`. El flujo Motor de Android compila y publica ese motor
y, al terminar, dispara **Plantilla para el teléfono**, que usa Java 17 y publica
`plantilla-ultima` con las mismas bibliotecas. El flujo del constructor
Android espera una plantilla cuya nota declare la versión solicitada por PC,
y ejecuta pruebas y compilación antes de publicar su app en `ultima`.

Al modificar el motor compartido hay que incrementar `MOTOR_VERSION` antes de
publicarlo: las versiones evitan que la PC reutilice una biblioteca antigua de
su caché. La app Android incorpora la plantilla al compilarse y vuelve a copiarla
al preparar un APK, sin depender de que cambie el tamaño del archivo.
Una app ya instalada recibe el motor nuevo al actualizar el constructor y volver
a generar el juego con su misma firma. No se cambia la firma ni se desinstala.

## Límites de esta revisión

Se corrigieron la restauración con `mInfo` nulo, la pérdida de datos al elegir
un icono, la caché basada solo en tamaño, los descriptores de ZIP abiertos y el
reemplazo prematuro del APK anterior. Se añadieron SoundFont externo, APK
universal y el registro del constructor.

Las pruebas automáticas cubren la captura ADB simulada, la configuración, SF2 y
las comprobaciones de superficies SDL. Las compilaciones de GitHub validan los
binarios. La interfaz de PC se abrió en un entorno de prueba. La rotación y la
instalación en Android y la venta con Mesprit requieren comprobarse en un teléfono.

Consulta [las instrucciones de prueba](pruebas-ports.md).
