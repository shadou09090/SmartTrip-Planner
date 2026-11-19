-- SmartTrip package persistence, migration draft V20261116_01.
-- REVIEW ONLY. Not executed. Requires comparison against the target Oracle schema and approval.
-- Additive only. This aggregate stores the exact validated package request as JSON CLOB;
-- old itinerary tables and their constraints are not altered by this migration.
-- The V prefix is a review/version label only: the project has no Flyway/Liquibase runner.

-- Read-only preflight (run separately using an approved read-only account):
-- SELECT table_name, column_name, data_type, data_length, data_precision, data_scale, nullable
-- FROM user_tab_columns WHERE table_name IN ('VIAJE','ITINERARIO')
-- ORDER BY table_name, column_id;
-- SELECT table_name, constraint_name, constraint_type, status
-- FROM user_constraints WHERE table_name IN ('VIAJE','ITINERARIO')
-- ORDER BY table_name, constraint_name;
-- SELECT table_name, index_name, uniqueness, status
-- FROM user_indexes WHERE table_name IN ('VIAJE','ITINERARIO')
-- ORDER BY table_name, index_name;
-- Confirm the parent key is VIAJE(ID_VIAJE) and inspect its datatype before creating the FK.
-- Check for existing objects with the names below before applying; stop and review if found.

CREATE TABLE PAQUETE_DOCUMENTO (
  ID_VIAJE NUMBER NOT NULL,
  REVISION NUMBER(10) NOT NULL,
  DOCUMENTO_JSON CLOB NOT NULL,
  HASH_DOCUMENTO CHAR(64) NOT NULL,
  ACTUALIZADO_EN TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT PK_PAQUETE_DOCUMENTO PRIMARY KEY (ID_VIAJE),
  CONSTRAINT FK_PAQUETE_DOCUMENTO_VIAJE FOREIGN KEY (ID_VIAJE) REFERENCES VIAJE(ID_VIAJE),
  CONSTRAINT CK_PAQUETE_DOCUMENTO_REV CHECK (REVISION >= 1)
);

CREATE TABLE PAQUETE_SOLICITUD (
  ID_VIAJE NUMBER NOT NULL,
  CLAVE_SOLICITUD VARCHAR2(80) NOT NULL,
  HASH_SOLICITUD CHAR(64) NOT NULL,
  REVISION_RESULTANTE NUMBER(10) NOT NULL,
  RESPUESTA_JSON CLOB NOT NULL,
  GUARDADO_EN TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT PK_PAQUETE_SOLICITUD PRIMARY KEY (ID_VIAJE, CLAVE_SOLICITUD),
  CONSTRAINT FK_PAQUETE_SOLICITUD_VIAJE FOREIGN KEY (ID_VIAJE) REFERENCES VIAJE(ID_VIAJE),
  CONSTRAINT CK_PAQUETE_SOLICITUD_REV CHECK (REVISION_RESULTANTE >= 1)
);

-- JSON persists the complete package: dates/order, internal/external/manual activity identity,
-- source/provider IDs, location, schedules, durations, cost evidence/currency, flight offer legs,
-- lodging and budget selections. It is intentionally not a searchable relational projection.
-- No backfill is needed; existing itinerary rows are untouched and package GET returns 404 until
-- the user explicitly saves a package snapshot.
-- Oracle DDL commits implicitly. If statement 2 fails, stop, inspect USER_OBJECTS and constraints,
-- then apply a DBA-reviewed forward repair. Do not rerun blindly or use DROP as rollback.
