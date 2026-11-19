# Persistencia del paquete de viaje

## Estado

`PUT/GET /api/viajes/{id}/paquete` implementa un agregado JSON transaccional, aislado en `PAQUETE_DOCUMENTO`. El feature flag del backend está apagado por defecto. La migración sigue sin aplicar y no se consultó ni escribió Oracle ATP.

La instancia Oracle no está disponible en este entorno para metadatos ni pruebas. El SQL debe revisarse en una instancia Oracle aislada antes de aprobar/aplicar. La integración H2 valida lógica JDBC/transaccional en modo Oracle, no sintaxis completa ni locking/concurrencia específicos de Oracle.

## Decisión de almacenamiento

El paquete validado se guarda como un documento JSON en CLOB con revisión y SHA-256, asociado al viaje mediante FK. Esto conserva en una única transacción los días, orden, actividades internas/manuales/externas, identidad del proveedor, coordenadas, horarios, duración, presupuesto, monedas/procedencia, vuelo y tramos de oferta, hotel y gastos. `PAQUETE_SOLICITUD` registra idempotencia y la respuesta exacta a un reintento.

Ventajas: el paquete completo se confirma o revierte como una unidad; no se fragmenta entre varias peticiones ni requiere reescribir filas globales del catálogo. Coste de la decisión: esos campos no son directamente consultables como columnas relacionales y los endpoints de itinerario legado no reflejan automáticamente el documento. La interfaz usa el contrato de paquete solo con activación explícita.

## Migración

El borrador versionado está en `migrations/V20261116_01__persistencia_paquete.sql`. Crea solamente las tablas `PAQUETE_DOCUMENTO` y `PAQUETE_SOLICITUD`, con PK/FK y revisiones positivas. No altera ni rellena viajes existentes. No hay Flyway/Liquibase en `pom.xml`; el prefijo versiona el artefacto para revisión, no lo aplica automáticamente.

Oracle hace commit implícito de DDL. La aplicación debe detenerse ante cualquier fallo, inspeccionar objetos/columnas/constraints ya creados y continuar con un script de reparación revisado; no ejecutar el archivo completo a ciegas ni intentar rollback destructivo.

## Contrato

- `PUT /api/viajes/{id}/paquete`, autenticado, con `Idempotency-Key` y `revisionEsperada`.
- `GET /api/viajes/{id}/paquete`, autenticado.
- Reintento mismo viaje/clave/hash devuelve la respuesta originalmente confirmada.
- Misma clave con otro cuerpo o revisión obsoleta devuelve 409.
- Fechas fuera del viaje y referencias ajenas/no existentes se rechazan.
- Respuesta `persistido=true` solo se produce tras guardar el documento y registro idempotente dentro de la transacción. La lectura GET posterior es el paso de verificación del cliente.

## Activación

Tras revisión y aplicación autorizada de la migración en el entorno objetivo:

1. Configurar `SMARTTRIP_PACKAGE_PERSISTENCE_ENABLED=true` en backend Oracle.
2. Configurar `VITE_SMARTTRIP_PACKAGE_PERSISTENCE=true` en frontend y reconstruir.
3. Probar GET inicial, PUT, GET posterior, permisos y conflictos con un viaje de prueba aislado.

Sin esas dos banderas, el endpoint no queda registrado y la UI conserva el guardado legado. No es seguro activar la bandera solo en frontend.
