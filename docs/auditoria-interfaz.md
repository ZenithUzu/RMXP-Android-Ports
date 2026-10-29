# Auditoría del menú, controles y constructor Android

Estas herramientas convierten un juego de RPG Maker en un APK. PC y teléfono
comparten el runtime de `android/app/src/main`. Se revisaron los flujos para una
persona que construye un juego a la vez, conserva partidas y reutiliza sus firmas.
La auditoría se centró en las capturas y fallos comunicados; no es una revisión
completa del motor C++, de SDL ni de todos los scripts de los fangames.

## Corregir primero

1. **Reinstalar reconstruía el juego completo** (`RPGMK-AndroidPort-Apk`, `MainActivity.siguiente`, `pasoCrear`, `crear`).
   - Qué hace: el botón del último paso elegía siempre `crear()`.
   - Problema: después de cancelar la instalación o encontrar otra firma, repetir la acción volvía a empaquetar cientos de MB.
   - Arreglo: separar Crear APK, Instalar APK creado, Guardar y Crear otra app; instalar solo usa el archivo terminado. Al volver a editar datos se invalida el resultado anterior.
   - Sin arreglo: se pierde tiempo y puede confundirse un APK antiguo con los nuevos datos.

2. **Teclado y mando táctil se superponían** (`Teclado.show/hide`, `TouchControls.updateVisibility`, `MainActivity.dispatchTouchEvent`).
   - Qué hace: ambas capas tenían visibilidad independiente.
   - Problema: el teclado dejaba visibles los controles; tocar la pantalla volvía a mostrarlos incluso escribiendo.
   - Arreglo: comunicar la visibilidad, liberar teclas al cambiar de capa y consumir el gesto que cierra el teclado.
   - Sin arreglo: se ven y pueden accionarse dos controles en la misma zona.

3. **Acciones del editor en la esquina** (`TouchControls.mostrarAjuste`, `rebuild`, `buildToolbar`).
   - Qué hace: colocaba un panel con coordenadas de un control recién recreado y añadía traslaciones a una barra con gravedad.
   - Problema: antes de medir, ancho y posición no son definitivos; el ajuste se limita a x=0/y=0 o se desplaza al reconstruir.
   - Arreglo: anclar barra y acciones seleccionadas con `FrameLayout.LayoutParams`, sin calcular posiciones de vistas sin medir.
   - Sin arreglo: el editor cambia de posición de forma imprevisible.

4. **Aviso de firma proponía desinstalar** (`RPGMK-AndroidPort-Apk`, recurso `conflicto`).
   - Qué hace: explicaba cómo resolver firmas distintas.
   - Problema: desinstalar borra los datos privados y una copia externa solo existe si el usuario la activó y se completó.
   - Arreglo: indicar la importación de la firma desde Datos y conservar el juego instalado.
   - Sin arreglo: el usuario puede perder partidas por seguir el mensaje.

## Corregir también

5. **Pausa difícil de encontrar** (`rmxp_menu.xml`, `OptionsMenu.bindMenu/open`).
   - Qué hace: guardaba `seguir_en_menu` dentro de Controles.
   - Problema: estaba oculta al abrir Pantalla, y el estado activo obligaba a interpretar el interruptor al revés para pausar.
   - Arreglo: interruptor global “Pausar en menú y editor” antes de las pestañas; al abrir se muestra el principio del panel. Se conserva la preferencia previa.
   - Sin arreglo: parece que la opción no existe.

6. **120 FPS no se podían elegir aparte de los Hz** (`OptionsMenu.applyHz`, `rmxp_fps`).
   - Qué hace: solo ofrecía los FPS del juego o los de la frecuencia solicitada.
   - Problema: pedir una pantalla de 120 Hz no comprueba que Android la concedió ni permite elegir 120 FPS con otro ajuste de pantalla.
   - Arreglo: Juego/Pantalla/60/120, voto de frecuencia sobre la Surface, Hz solicitados y reales separados y registro del objetivo.
   - Sin arreglo: un objetivo y la frecuencia real de pantalla se confunden con rendimiento medido.

7. **Vibración incompleta** (`ControlView.onTouchEvent`, `Teclado.keyView`).
   - Qué hace: llamaba a la respuesta háptica del sistema solo para botones simples y teclado.
   - Problema: la cruceta no la invocaba; el ajuste de la app no garantizaba respuesta cuando la vista no daba feedback.
   - Arreglo: una pulsación breve compartida por botones, cruceta y teclado, condicionada al interruptor, al permiso y a que haya vibrador.
   - Sin arreglo: algunos controles nunca responden y la opción parece inoperante.

8. **Ajustes visualmente desconectados y teclado incompleto** (`TouchControls`, `OptionsMenu.showInputMapping`, `Teclado`).
   - Qué hace: usaba diálogos del sistema con filas grandes sobre el menú del juego.
   - Problema: opciones generales, edición individual y asignación de teclas no compartían el panel; faltaban F1–F12, navegación y modificadores en el teclado visible.
   - Arreglo: panel lateral común, cruceta segmentada y botones rediseñados, insignias solo en edición y teclado con tres páginas. Se mantienen perfiles, posiciones y colores Android.
   - Sin arreglo: la navegación se siente distinta en cada ajuste y algunas acciones del juego no se pueden enviar.

## Verificación y límites

Las pruebas de lógica ejecutan los métodos reales de selección de FPS y creación/
instalación. Un módulo de prueba independiente reutiliza las clases y recursos
reales de interfaz; únicamente sustituye el motor nativo. Comprueba pausa visible,
objetivo 120 con pantalla 60, coordenadas del editor tras reconstruir, panel lateral,
visibilidad del teclado y liberación de teclas. Guarda capturas en Actions.

Esto no demuestra 120 FPS sostenidos, compatibilidad con todo fangame ni vibración
física en un POCO. Se deben comprobar en el teléfono con un juego regenerado.

Veredicto: corregir las transiciones y acciones antes de añadir más ajustes. Se
eliminaron los diálogos generales duplicados de tamaño y estilo al integrar el panel.
