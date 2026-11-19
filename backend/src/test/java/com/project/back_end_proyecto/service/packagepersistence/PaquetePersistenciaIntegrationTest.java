package com.project.back_end_proyecto.service.packagepersistence;

import tools.jackson.databind.ObjectMapper;
import com.project.back_end_proyecto.dto.packagepersistence.PaqueteGuardarRequest;
import com.project.back_end_proyecto.dto.packagepersistence.PaqueteGuardarRequest.*;
import com.project.back_end_proyecto.repository.PaqueteSnapshotRepository;
import jakarta.validation.Validation;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(PaquetePersistenciaIntegrationTest.TestConfig.class)
@ActiveProfiles("oracle")
@TestPropertySource(properties = "smarttrip.package-persistence.enabled=true")
class PaquetePersistenciaIntegrationTest {
    @Configuration
    @EnableTransactionManagement
    @Import(PaquetePersistenciaService.class)
    static class TestConfig {
        @Bean DataSource dataSource() {
            var ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:package-save;MODE=Oracle;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=TRUE");
            ds.setUser("sa"); ds.setPassword(""); return ds;
        }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager tx(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean ObjectMapper mapper() { return new ObjectMapper(); }
        @Bean jakarta.validation.Validator validator() {
            return Validation.buildDefaultValidatorFactory().getValidator();
        }
        @Bean TripAccessStub tripAccess() { return new TripAccessStub(); }
        @Bean FailingSnapshotRepository snapshots(JdbcTemplate jdbc) { return new FailingSnapshotRepository(jdbc); }
    }

    static class TripAccessStub implements PaqueteTripAccess {
        @Override public TripContext requireRead(long userId, long tripId) { return require(userId, tripId); }
        @Override public TripContext requireEdit(long userId, long tripId) { return require(userId, tripId); }
        private TripContext require(long userId, long tripId) {
            if (userId != tripId) throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
            return new TripContext(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 24));
        }
    }

    static class FailingSnapshotRepository extends PaqueteSnapshotRepository {
        private final AtomicBoolean failNextRequest = new AtomicBoolean();
        FailingSnapshotRepository(JdbcTemplate jdbc) { super(jdbc); }
        void failAfterNextIdempotencyWrite() { failNextRequest.set(true); }
        @Override public void insertRequest(long tripId, String key, String hash, long revision,
                                            String responseJson, LocalDateTime now) {
            super.insertRequest(tripId, key, hash, revision, responseJson, now);
            if (failNextRequest.compareAndSet(true, false)) throw new IllegalStateException("forced isolated rollback");
        }
    }

    @org.springframework.beans.factory.annotation.Autowired PaquetePersistenciaService service;
    @org.springframework.beans.factory.annotation.Autowired JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired FailingSnapshotRepository snapshots;

    @BeforeEach void prepareIsolatedSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS VIAJE (ID_VIAJE BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ACTIVIDAD (ID_ACTIVIDAD BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ITINERARIO (ID_ITINERARIO BIGINT PRIMARY KEY, ID_VIAJE BIGINT)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS DIA_ITINERARIO (ID_DIA BIGINT PRIMARY KEY, ID_ITINERARIO BIGINT, FECHA DATE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS VUELO (ID_VUELO BIGINT PRIMARY KEY, ID_VIAJE BIGINT)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ALOJAMIENTO (ID_ALOJAMIENTO BIGINT PRIMARY KEY, ID_ITINERARIO BIGINT)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS PAQUETE_DOCUMENTO (ID_VIAJE BIGINT PRIMARY KEY, REVISION BIGINT NOT NULL, DOCUMENTO_JSON CLOB NOT NULL, HASH_DOCUMENTO CHAR(64) NOT NULL, ACTUALIZADO_EN TIMESTAMP NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS PAQUETE_SOLICITUD (ID_VIAJE BIGINT NOT NULL, CLAVE_SOLICITUD VARCHAR(80) NOT NULL, HASH_SOLICITUD CHAR(64) NOT NULL, REVISION_RESULTANTE BIGINT NOT NULL, RESPUESTA_JSON CLOB NOT NULL, GUARDADO_EN TIMESTAMP NOT NULL, PRIMARY KEY (ID_VIAJE, CLAVE_SOLICITUD))");
        jdbc.update("DELETE FROM PAQUETE_SOLICITUD"); jdbc.update("DELETE FROM PAQUETE_DOCUMENTO");
        jdbc.update("DELETE FROM VIAJE");
        jdbc.update("INSERT INTO VIAJE (ID_VIAJE) VALUES (1), (2)");
        jdbc.update("DELETE FROM ACTIVIDAD");
        for (long id = 10; id < 15; id++) jdbc.update("INSERT INTO ACTIVIDAD (ID_ACTIVIDAD) VALUES (?)", id);
        jdbc.update("DELETE FROM ITINERARIO"); jdbc.update("INSERT INTO ITINERARIO (ID_ITINERARIO, ID_VIAJE) VALUES (101, 1)");
        jdbc.update("DELETE FROM DIA_ITINERARIO"); jdbc.update("INSERT INTO DIA_ITINERARIO (ID_DIA, ID_ITINERARIO, FECHA) VALUES (1001, 101, DATE '2026-11-20')");
        jdbc.update("DELETE FROM VUELO"); jdbc.update("INSERT INTO VUELO (ID_VUELO, ID_VIAJE) VALUES (20, 1)");
        jdbc.update("DELETE FROM ALOJAMIENTO"); jdbc.update("INSERT INTO ALOJAMIENTO (ID_ALOJAMIENTO, ID_ITINERARIO) VALUES (30, 101)");
    }

    @Test void savesAndRecoversFiveDaysExactOrderSourcesScheduleCostsAndSelections() {
        var request = fullPackage(0);
        var saved = service.guardar(1, 1, "request-0001", request);
        var loaded = service.recuperar(1, 1);
        assertEquals(1, saved.revision());
        assertEquals(request, loaded.paquete());
        assertEquals(new BigDecimal("9000000"), loaded.paquete().viaje().presupuestoTotal());
        assertEquals(request.dias().stream().map(d -> d.actividades().stream().map(Actividad::nombre).toList()).toList(),
                loaded.paquete().dias().stream().map(d -> d.actividades().stream().map(Actividad::nombre).toList()).toList());
        assertEquals("Geoapify", loaded.paquete().dias().get(0).actividades().get(0).fuente());
        assertEquals("place/long-id-0", loaded.paquete().dias().get(0).actividades().get(0).idExterno());
        assertEquals("2026-11-19T20:00", loaded.paquete().vueloSeleccionado().tramos().get(0).salida());
        assertEquals("EUR", loaded.paquete().dias().get(0).actividades().get(0).costo().moneda());
        assertEquals(TipoCosto.DESCONOCIDO, loaded.paquete().alojamiento().costo().tipo());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM PAQUETE_DOCUMENTO WHERE ID_VIAJE=1", Integer.class));
    }

    @Test void identicalRetryReturnsSameRevisionAndDoesNotDuplicate() {
        var request = fullPackage(0);
        var first = service.guardar(1, 1, "request-retry", request);
        var retry = service.guardar(1, 1, "request-retry", request);
        assertEquals(first, retry);
        assertEquals(1, jdbc.queryForObject("SELECT REVISION FROM PAQUETE_DOCUMENTO WHERE ID_VIAJE=1", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM PAQUETE_SOLICITUD WHERE ID_VIAJE=1", Integer.class));
    }

    @Test void rejectsChangedBodyForAnAlreadyUsedIdempotencyKeyAndPersistsReorderedDaysOnNewRevision() {
        var original = fullPackage(0);
        service.guardar(1, 1, "request-stable-key", original);
        var changedWithSameKey = fullPackage(1);
        var changedActivities = new java.util.ArrayList<>(changedWithSameKey.dias());
        var first = changedActivities.get(0);
        changedActivities.set(0, new Dia(first.diaId(), first.fecha(), List.of(first.actividades().get(1), first.actividades().get(0))));
        var changed = new PaqueteGuardarRequest(1L, changedWithSameKey.viaje(), changedActivities,
                changedWithSameKey.vueloSeleccionado(), changedWithSameKey.alojamiento(),
                changedWithSameKey.gastos(), changedWithSameKey.asignacionesEliminadas());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.guardar(1, 1, "request-stable-key", changed)).getStatusCode().value());

        var movedDays = new java.util.ArrayList<>(original.dias());
        var dayOne = movedDays.get(0);
        var dayTwo = movedDays.get(1);
        var movedActivity = dayOne.actividades().get(1);
        movedDays.set(0, new Dia(dayOne.diaId(), dayOne.fecha(), List.of(dayOne.actividades().get(0))));
        movedDays.set(1, new Dia(dayTwo.diaId(), dayTwo.fecha(), List.of(dayTwo.actividades().get(0),
                dayTwo.actividades().get(1), withOrder(movedActivity, 3))));
        var moved = new PaqueteGuardarRequest(1L, original.viaje(), movedDays, original.vueloSeleccionado(),
                original.alojamiento(), original.gastos(), original.asignacionesEliminadas());
        service.guardar(1, 1, "request-move-day", moved);
        assertEquals(moved, service.recuperar(1, 1).paquete());
    }

    @Test void staleRevisionAndDuplicateDayOrderAreRejected() {
        service.guardar(1, 1, "request-first", fullPackage(0));
        var stale = assertThrows(ResponseStatusException.class,
                () -> service.guardar(1, 1, "request-second", fullPackage(0)));
        assertEquals(409, stale.getStatusCode().value());
        var duplicate = fullPackage(1);
        var day = duplicate.dias().get(0);
        var acts = List.of(day.actividades().get(0), new Actividad("second", TipoActividad.MANUAL, null,
                null, null, "Duplicate order", null, null, null, null, 1, null, null, unknown()));
        var badDay = new Dia(day.diaId(), day.fecha(), acts);
        var days = new java.util.ArrayList<>(duplicate.dias()); days.set(0, badDay);
        var invalid = new PaqueteGuardarRequest(1L, duplicate.viaje(), days, duplicate.vueloSeleccionado(),
                duplicate.alojamiento(), duplicate.gastos(), duplicate.asignacionesEliminadas());
        assertThrows(ResponseStatusException.class, () -> service.guardar(1, 1, "request-order", invalid));
    }

    @Test void transactionRollsBackSnapshotWhenIdempotencyWriteFails() {
        snapshots.failAfterNextIdempotencyWrite();
        assertThrows(IllegalStateException.class, () -> service.guardar(1, 1, "request-rollback", fullPackage(0)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM PAQUETE_DOCUMENTO", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM PAQUETE_SOLICITUD", Integer.class));
    }

    @Test void writesAndReadsPackageDocumentsLargerThanVarcharBindLimitsUsingClob() {
        var template = fullPackage(0);
        var days = new java.util.ArrayList<Dia>();
        for (var day : template.dias()) {
            var activities = new java.util.ArrayList<>(day.actividades());
            for (int index = 0; index < 12; index++) {
                activities.add(new Actividad("manual-" + day.fecha() + "-" + index, TipoActividad.MANUAL,
                        null, null, null, "Manual " + index, "x".repeat(500), "Ubicación", null, null,
                        index + 3, null, null, unknown()));
            }
            days.add(new Dia(day.diaId(), day.fecha(), activities));
        }
        var large = new PaqueteGuardarRequest(0L, template.viaje(), days, template.vueloSeleccionado(),
                template.alojamiento(), template.gastos(), template.asignacionesEliminadas());
        service.guardar(1, 1, "request-large-clob", large);
        assertEquals(large, service.recuperar(1, 1).paquete());
    }

    @Test void preservesHighPrecisionCoordinatesThroughPackageClobSaveAndRead() {
        var original = fullPackage(0);
        var days = new java.util.ArrayList<>(original.dias());
        var firstDay = days.get(0);
        var external = firstDay.actividades().get(0);
        var precise = new Actividad(external.claveCliente(), external.tipo(), external.actividadId(),
                external.fuente(), external.idExterno(), external.nombre(), external.descripcion(), external.ubicacion(),
                new BigDecimal("41.387420012345678901"), new BigDecimal("2.169918765432109876"), external.orden(),
                external.horaLocal(), external.duracionMinutos(), external.costo());
        days.set(0, new Dia(firstDay.diaId(), firstDay.fecha(), List.of(precise, firstDay.actividades().get(1))));
        var originalHotel = original.alojamiento();
        var preciseHotel = new AlojamientoSeleccion(originalHotel.alojamientoId(), originalHotel.fuente(),
                originalHotel.idExterno(), originalHotel.nombre(), originalHotel.direccion(),
                new BigDecimal("41.380000012345678901"), new BigDecimal("2.170000098765432109"),
                originalHotel.costo(), originalHotel.noches(), originalHotel.habitaciones(), originalHotel.huespedes());
        var request = new PaqueteGuardarRequest(0L, original.viaje(), days, original.vueloSeleccionado(),
                preciseHotel, original.gastos(), original.asignacionesEliminadas());

        service.guardar(1, 1, "request-precise-coordinates", request);
        var recovered = service.recuperar(1, 1).paquete();

        assertEquals(request, recovered);
        assertEquals(new BigDecimal("41.387420012345678901"), recovered.dias().get(0).actividades().get(0).latitud());
        assertEquals(new BigDecimal("2.170000098765432109"), recovered.alojamiento().longitud());
    }

    @Test void isolatesTripsAndUsersAndValidatesReferencedEntities() {
        service.guardar(1, 1, "request-trip-one", fullPackage(0));
        assertThrows(ResponseStatusException.class, () -> service.recuperar(2, 1));
        assertThrows(ResponseStatusException.class, () -> service.recuperar(1, 2));
        var current = fullPackage(0);
        var first = current.dias().get(0);
        var wrongDay = new Dia(999L, first.fecha(), first.actividades());
        var days = new java.util.ArrayList<>(current.dias()); days.set(0, wrongDay);
        var invalid = new PaqueteGuardarRequest(0L, current.viaje(), days, current.vueloSeleccionado(),
                current.alojamiento(), current.gastos(), current.asignacionesEliminadas());
        assertThrows(ResponseStatusException.class, () -> service.guardar(1, 1, "request-wrong-day", invalid));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM PAQUETE_DOCUMENTO WHERE ID_VIAJE=2", Integer.class));
    }

    private static PaqueteGuardarRequest fullPackage(long revision) {
        var days = new java.util.ArrayList<Dia>();
        for (int i = 0; i < 5; i++) {
            var external = new Actividad("external-day-" + i, TipoActividad.EXTERNA, null,
                    "Geoapify", "place/long-id-" + i, "Attraction " + i, "Description", "District",
                    new BigDecimal("48.1000000"), new BigDecimal("2.2000000"), 1,
                    java.time.LocalTime.of(9, 30), 90,
                    new Costo(new BigDecimal("12.50"), "EUR", "person", BigDecimal.ONE,
                            TipoCosto.PRECIO_PUBLICADO, "official", "2026-10-01T10:00:00Z", "official listing"));
            var internal = new Actividad("internal-day-" + i, TipoActividad.INTERNA, 10L + i,
                    null, null, "Catalog activity " + i, null, "District", null, null, 2, null, 45, unknown());
            days.add(new Dia(null, LocalDate.of(2026, 11, 20).plusDays(i), List.of(external, internal)));
        }
        var legs = List.of(new TramoVuelo("BOG", "MAD", LocalDateTime.parse("2026-11-19T20:00:00"),
                LocalDateTime.parse("2026-11-20T12:00:00"), "Air", "A100"));
        var flight = new VueloSeleccion(null, "DuffelSandbox", "offer-id", "COP", new BigDecimal("2500000.00"),
                TipoCosto.SANDBOX, "BOG", "MAD", legs.get(0).salida(), legs.get(0).llegada(), legs);
        var hotel = new AlojamientoSeleccion(null, "Geoapify", "lodging-id", "Hotel", "Address",
                new BigDecimal("48.1"), new BigDecimal("2.2"), unknown(), 4, 1, 1);
        return new PaqueteGuardarRequest(revision,
                new ViajePaquete(1, "COP", "Europe/Paris", Map.of("pace", "moderate"), new BigDecimal("9000000")), days,
                flight, hotel, List.of(new Gasto("food", "ALIMENTACION", "Food", unknown())), List.of());
    }

    private static Costo unknown() {
        return new Costo(null, null, null, null, TipoCosto.DESCONOCIDO, null, null, null);
    }

    private static Actividad withOrder(Actividad activity, int order) {
        return new Actividad(activity.claveCliente(), activity.tipo(), activity.actividadId(), activity.fuente(),
                activity.idExterno(), activity.nombre(), activity.descripcion(), activity.ubicacion(), activity.latitud(),
                activity.longitud(), order, activity.horaLocal(), activity.duracionMinutos(), activity.costo());
    }
}
