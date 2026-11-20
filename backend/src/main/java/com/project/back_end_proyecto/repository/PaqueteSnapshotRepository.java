package com.project.back_end_proyecto.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Optional;

@Repository
public class PaqueteSnapshotRepository {
    private final JdbcTemplate jdbc;

    public PaqueteSnapshotRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean lockTrip(long tripId) {
        return !jdbc.query("SELECT ID_VIAJE FROM VIAJE WHERE ID_VIAJE = ? FOR UPDATE",
                (rs, row) -> rs.getLong(1), tripId).isEmpty();
    }

    public Optional<StoredPackage> find(long tripId) {
        return jdbc.query("SELECT REVISION, DOCUMENTO_JSON, ACTUALIZADO_EN FROM PAQUETE_DOCUMENTO WHERE ID_VIAJE = ?",
                (rs, row) -> new StoredPackage(rs.getLong("REVISION"), readClob(rs, "DOCUMENTO_JSON"),
                        rs.getTimestamp("ACTUALIZADO_EN").toLocalDateTime()), tripId).stream().findFirst();
    }

    public Optional<StoredResponse> findRequest(long tripId, String key) {
        return jdbc.query("SELECT HASH_SOLICITUD, RESPUESTA_JSON FROM PAQUETE_SOLICITUD WHERE ID_VIAJE = ? AND CLAVE_SOLICITUD = ?",
                (rs, row) -> new StoredResponse(rs.getString("HASH_SOLICITUD"), readClob(rs, "RESPUESTA_JSON")),
                tripId, key).stream().findFirst();
    }

    public void insertPackage(long tripId, long revision, String json, String hash, LocalDateTime now) {
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO PAQUETE_DOCUMENTO (ID_VIAJE, REVISION, DOCUMENTO_JSON, HASH_DOCUMENTO, ACTUALIZADO_EN) VALUES (?, ?, ?, ?, ?)");
            statement.setLong(1, tripId); statement.setLong(2, revision);
            statement.setClob(3, new StringReader(json), json.length());
            statement.setString(4, hash); statement.setTimestamp(5, Timestamp.valueOf(now));
            return statement;
        });
    }

    public void updatePackage(long tripId, long revision, String json, String hash, LocalDateTime now) {
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("UPDATE PAQUETE_DOCUMENTO SET REVISION = ?, DOCUMENTO_JSON = ?, HASH_DOCUMENTO = ?, ACTUALIZADO_EN = ? WHERE ID_VIAJE = ?");
            statement.setLong(1, revision); statement.setClob(2, new StringReader(json), json.length());
            statement.setString(3, hash); statement.setTimestamp(4, Timestamp.valueOf(now)); statement.setLong(5, tripId);
            return statement;
        });
    }

    public void insertRequest(long tripId, String key, String hash, long revision, String responseJson, LocalDateTime now) {
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO PAQUETE_SOLICITUD (ID_VIAJE, CLAVE_SOLICITUD, HASH_SOLICITUD, REVISION_RESULTANTE, RESPUESTA_JSON, GUARDADO_EN) VALUES (?, ?, ?, ?, ?, ?)");
            statement.setLong(1, tripId); statement.setString(2, key); statement.setString(3, hash);
            statement.setLong(4, revision); statement.setClob(5, new StringReader(responseJson), responseJson.length());
            statement.setTimestamp(6, Timestamp.valueOf(now));
            return statement;
        });
    }

    public boolean activityExists(long activityId) {
        return count("SELECT COUNT(*) FROM ACTIVIDAD WHERE ID_ACTIVIDAD = ?", activityId);
    }

    public boolean dayBelongsToTrip(long dayId, long tripId) {
        return count("SELECT COUNT(*) FROM DIA_ITINERARIO D JOIN ITINERARIO I ON I.ID_ITINERARIO = D.ID_ITINERARIO WHERE D.ID_DIA = ? AND I.ID_VIAJE = ?",
                dayId, tripId);
    }

    public boolean dayMatchesTripAndDate(long dayId, long tripId, LocalDate date) {
        return count("SELECT COUNT(*) FROM DIA_ITINERARIO D JOIN ITINERARIO I ON I.ID_ITINERARIO = D.ID_ITINERARIO WHERE D.ID_DIA = ? AND I.ID_VIAJE = ? AND D.FECHA = ?",
                dayId, tripId, java.sql.Date.valueOf(date));
    }

    public boolean flightBelongsToTrip(long flightId, long tripId) {
        return count("SELECT COUNT(*) FROM VUELO WHERE ID_VUELO = ? AND ID_VIAJE = ?", flightId, tripId);
    }

    public boolean lodgingBelongsToTrip(long lodgingId, long tripId) {
        return count("SELECT COUNT(*) FROM ALOJAMIENTO A JOIN ITINERARIO I ON I.ID_ITINERARIO = A.ID_ITINERARIO WHERE A.ID_ALOJAMIENTO = ? AND I.ID_VIAJE = ?",
                lodgingId, tripId);
    }

    private boolean count(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count != null && count > 0;
    }

    private static String readClob(ResultSet resultSet, String column) throws SQLException {
        var clob = resultSet.getClob(column);
        if (clob == null) return null;
        StringBuilder value = new StringBuilder();
        try (Reader reader = clob.getCharacterStream()) {
            char[] buffer = new char[8192];
            int count;
            while ((count = reader.read(buffer)) >= 0) value.append(buffer, 0, count);
            return value.toString();
        } catch (IOException exception) {
            throw new SQLException("No se pudo leer el documento CLOB del paquete.", exception);
        }
    }

    public record StoredPackage(long revision, String json, LocalDateTime updatedAt) { }
    public record StoredResponse(String requestHash, String responseJson) { }
}
