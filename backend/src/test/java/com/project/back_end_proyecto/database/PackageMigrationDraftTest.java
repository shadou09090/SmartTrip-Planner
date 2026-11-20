package com.project.back_end_proyecto.database;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class PackageMigrationDraftTest {
    private static final Path MIGRATION = Path.of("..", "Docs", "database", "migrations",
            "V20261116_01__persistencia_paquete.sql");

    @Test void definesSingleAdditiveSnapshotAndIdempotencyTables() throws IOException {
        String sql = Files.readString(MIGRATION).toUpperCase();
        assertTrue(sql.contains("CREATE TABLE PAQUETE_DOCUMENTO"));
        assertTrue(sql.contains("DOCUMENTO_JSON CLOB NOT NULL"));
        assertTrue(sql.contains("CREATE TABLE PAQUETE_SOLICITUD"));
        assertTrue(sql.contains("RESPUESTA_JSON CLOB NOT NULL"));
        assertTrue(sql.contains("PRIMARY KEY (ID_VIAJE, CLAVE_SOLICITUD)"));
        assertFalse(sql.contains("CREATE UNIQUE INDEX UX_VUELO_SELECCIONADO_VIAJE"));
        assertFalse(sql.contains("ALTER TABLE VUELO ADD"));
    }

    @Test void hasClosedCreateStatementsAndNoDestructiveDdl() throws IOException {
        String sql = Files.readString(MIGRATION).toUpperCase().replaceAll("(?m)^\\s*--.*$", "");
        var createTable = Pattern.compile("CREATE\\s+TABLE\\s+\\w+\\s*\\((.*?)\\);", Pattern.DOTALL);
        var statements = createTable.matcher(sql);
        int count = 0;
        while (statements.find()) {
            count++;
            assertFalse(statements.group(1).matches("(?s).*?,\\s*$"), "Trailing comma in CREATE TABLE");
        }
        assertEquals(2, count);
        assertFalse(Pattern.compile("\\b(DROP|DELETE|UPDATE|ALTER)\\b").matcher(sql).find());
        assertTrue(sql.contains("REFERENCES VIAJE(ID_VIAJE)"));
    }
}
