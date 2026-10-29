# PC y Android: comparación de las herramientas

Revisión del 28 de octubre de 2026. Se comparó el código de PC de este cambio
con `RPGMK-AndroidPort-Apk` en `c64e61c3546e7fe042f7e6f84498c066834cdfc8`.
**Comparten el motor y muchas funciones, pero todavía no hay paridad completa.**

| Función | PC | Constructor Android |
| --- | --- | --- |
| Importar carpeta o ZIP | Sí | Sí; lee el origen sin copiar todo el juego |
| Nombre, autor, versión, icono y fondo | Sí | Sí |
| Firma persistente por juego | Sí | Sí, con importación y exportación de la firma de PC |
| Ahorrar espacio | Sí | Sí |
| SoundFont que ya viene en el juego | Detección automática | Detección automática |
| Elegir un SoundFont externo | Sí | No hay selector; debe incluirse en el juego |
| Crear varios juegos en cola | Sí | Un juego por proceso |
| Arquitecturas | Incluye todas las del motor descargado | Selecciona la ABI compatible con el teléfono; el APK puede servir en menos dispositivos |
| Prueba y log en vivo | Instalación, apertura y captura por USB desde la interfaz | El constructor no captura otra app; el APK generado exporta su propio log desde su menú |
| Parches y motor | Parches del repositorio local; motor descargado o `--motor` | Parches y motor incluidos en la plantilla integrada en la app |
| Configuración `mkxp.json` con comentarios | Admite comentarios de línea, de bloque y comas finales | `Constructor.sinComentarios` elimina comentarios `//`; si el JSON falla, crea una configuración nueva |

## Diferencias que requieren atención

- **Actualizar el código no actualiza por sí solo los binarios.** PC sigue
  apuntando a `motor-v10`. La app Android copia `plantilla.apk` de sus assets;
  no descarga un motor nuevo al crear cada juego. Hay que compilar/publicar el
  motor, renovar la plantilla y reconstruir la app para propagar una corrección.
- La versión publicada de Android consultada fue `c64e61c-66abb29`; la plantilla
  `plantilla-ultima` declara el código `e7e85b9` y `motor-v10` declara `f90a2d4`.
  Son referencias distintas y ninguna incorpora por sí sola la corrección de
  texto que se agregó después a `main`. Los artefactos de prueba se validan
  por separado antes de sustituir las versiones publicadas.
- En `MainActivity.onCreate`, al restaurar una sesión se llama a `mostrar(mPaso)`
  antes de recuperar `mInfo` mediante `importar`. Si el paso guardado es el de
  creación, `pasoCrear` accede a `mInfo.titulo` con `mInfo == null`. Es una
  diferencia de estabilidad pendiente; coincide con el fallo del constructor
  que apareció en el primer registro de esta investigación.
- La caché de la plantilla se invalida por tamaño. Una actualización de la app
  cuya plantilla cambie pero conserve exactamente el tamaño podría reutilizar
  la copia anterior. Comparar contenido o versión sería más fiable.

Esta revisión compara funciones y rutas de código. No constituye una prueba
completa del constructor Android ni confirma que la venta en Relict esté
corregida en un teléfono real. En este cambio se actualizó la herramienta de PC;
no se modificó ni publicó una nueva versión del constructor Android.
