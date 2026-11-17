# HU-007 — Control de presupuesto manual

Issue: https://github.com/shadou09090/SmartTrip-Planner/issues/23

En el itinerario se muestra el presupuesto del viaje, el costo acumulado de las
actividades de todos los días y el saldo disponible (o el exceso). Cada actividad
manual admite un costo en COP, con hasta dos decimales; cero representa una
actividad gratuita. Los costos negativos o inválidos no se aceptan.

El total se deriva de las actividades actuales: agregar o eliminar actualiza
inmediatamente el resumen. La alerta aparece únicamente cuando el gasto supera
el límite y desaparece al volver a un total igual o inferior al presupuesto.
Los cálculos utilizan centavos para evitar errores de comparación decimal.
Las actividades anteriores sin costo se contabilizan como cero.

Si se abre el itinerario sin un viaje, se muestra el gasto y una indicación para
definir el presupuesto al crear el viaje, sin inventar un límite.

## Validación

Desde `frontend`, con Node.js 24:

```sh
npm ci
npm test
npm run build
```

Las pruebas cubren agregar costos en varios días, alcanzar exactamente el límite,
superarlo, eliminar la actividad que provoca el exceso, actividades gratuitas,
validación de costos, precisión decimal y ausencia de presupuesto.

Prueba manual: crear un viaje con presupuesto de 100.000 COP, abrir su itinerario,
agregar actividades de 60.000 y 40.000 COP en distintos días y comprobar que no
hay alerta. Agregar una entrada de 1 COP y comprobar la alerta; eliminarla y
comprobar que desaparece.

## Alcance

Esta historia trabaja con el estado del itinerario existente en el frontend.
Al recargar, salir del planificador o regenerar los días se reinician las
actividades, igual que antes de este cambio. Guardar itinerarios entre sesiones
corresponde a HU-015 (#7). No se modifican endpoints ni se suman vuelos u hoteles.
