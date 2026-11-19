# Integraciones externas de SmartTrip Planner

Las claves de proveedores se configuran únicamente en el entorno privado del backend. No deben agregarse al frontend, al repositorio ni a variables `VITE_*`.

## Vuelos: Duffel

- Variable requerida: `DUFFEL_ACCESS_TOKEN`.
- Variable opcional: `DUFFEL_API_BASE_URL` (por defecto, `https://api.duffel.com`).
- Rutas implementadas: sugerencias de ciudades/aeropuertos y búsqueda de ofertas.
- El modo de prueba de Duffel devuelve datos de sandbox; no representa disponibilidad, horarios ni precios reales. Un token de producción debe obtenerse en una cuenta del proveedor.
- SmartTrip no reserva ni compra los vuelos. Las búsquedas no se guardan en Oracle; el usuario puede registrar por separado la información que decida guardar.
- Se aplican timeout HTTP, caché de sugerencias y límite de frecuencia de búsquedas.

## Lugares y alojamientos: OpenStreetMap

- No requiere clave.
- La búsqueda de recomendaciones y alojamientos se realiza en el backend mediante Nominatim y Overpass, con caché y límites conservadores.
- La búsqueda de alojamiento se ejecuta a petición del usuario. Seleccionar un resultado solo completa el formulario; el usuario debe guardarlo para persistirlo en Oracle.
- Las respuestas pueden estar incompletas y no incluyen necesariamente precios, disponibilidad ni valoración. No se deben interpretar como ofertas.
- La aplicación muestra atribución a OpenStreetMap. La política del servidor público Nominatim exige identificación de la aplicación, máximo de una solicitud por segundo, caché y prohíbe autocomplete en su API pública: <https://operations.osmfoundation.org/policies/nominatim/>.
- Overpass puede limitar o rechazar consultas. La interfaz muestra resultados vacíos o errores del servicio sin inventar lugares.

## Tiempos de traslado: Google Routes

- Variable requerida: `SMARTTRIP_GOOGLE_MAPS_API_KEY`.
- Variable opcional: `SMARTTRIP_GOOGLE_ROUTES_BASE_URL` (por defecto, `https://routes.googleapis.com`).
- El adaptador backend consulta los modos `WALK`, `TRANSIT` y `DRIVE`; se muestra una advertencia si un tiempo supera 120 minutos.
- Sin clave, el endpoint no realiza solicitudes externas y responde como servicio sin configurar.
- Google exige habilitar Routes API y facturación en el proyecto de Cloud; las solicitudes se cobran según SKU y cuota. Configura límites de cuota antes de usar una clave. Referencia oficial: <https://developers.google.com/maps/documentation/routes/usage-and-billing>.
- La interfaz pide confirmación antes de solicitar una ruta. Los resultados de caminata pueden no reflejar todas las aceras o condiciones peatonales.
