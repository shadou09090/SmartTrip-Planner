package com.project.back_end_proyecto.dto.packagepersistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.LocalTime;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/**
 * Versioned request contract for an atomic trip-package save.
 * The Oracle endpoint remains unavailable until its additive migration is approved and applied.
 */
public record PaqueteGuardarRequest(
        @NotNull @PositiveOrZero Long revisionEsperada,
        @NotNull @Valid ViajePaquete viaje,
        @NotEmpty @Size(max = 366) List<@NotNull @Valid Dia> dias,
        @Valid VueloSeleccion vueloSeleccionado,
        @Valid AlojamientoSeleccion alojamiento,
        @NotNull @Size(max = 500) List<@NotNull @Valid Gasto> gastos,
        @NotNull @Size(max = 500) List<@NotBlank @Size(max = 160) String> asignacionesEliminadas) {

    public PaqueteGuardarRequest {
        dias = dias == null ? null : List.copyOf(dias);
        gastos = gastos == null ? null : List.copyOf(gastos);
        asignacionesEliminadas = asignacionesEliminadas == null ? null : List.copyOf(asignacionesEliminadas);
    }

    /** Snapshot of package-level fields that the additive schema proposal persists. */
    public record ViajePaquete(
            @Positive @Digits(integer = 4, fraction = 0) Integer numeroViajeros,
            @Pattern(regexp = "[A-Z]{3}") String monedaPresupuesto,
            @Size(max = 80) String zonaHoraria,
            Map<@NotBlank @Size(max = 80) String, Object> preferencias,
            @DecimalMin("0") @Digits(integer = 14, fraction = 2) BigDecimal presupuestoTotal) {
        public ViajePaquete(Integer numeroViajeros, String monedaPresupuesto, String zonaHoraria,
                            Map<String, Object> preferencias) {
            this(numeroViajeros, monedaPresupuesto, zonaHoraria, preferencias, null);
        }
        public ViajePaquete {
            preferencias = preferencias == null ? null
                    : Collections.unmodifiableMap(new java.util.HashMap<>(preferencias));
        }

        @AssertTrue(message = "La zona horaria debe ser un identificador IANA válido")
        @JsonIgnore
        public boolean isZonaHorariaValida() {
            if (zonaHoraria == null || zonaHoraria.isBlank()) return true;
            try {
                ZoneId.of(zonaHoraria);
                return true;
            } catch (DateTimeException ignored) {
                return false;
            }
        }

        @AssertTrue(message = "El presupuesto requiere una moneda explícita")
        @JsonIgnore
        public boolean isPresupuestoConMoneda() {
            return presupuestoTotal == null || monedaPresupuesto != null && monedaPresupuesto.matches("[A-Z]{3}");
        }
    }

    @AssertTrue(message = "Las fechas de los días deben ser únicas")
    @JsonIgnore
    public boolean isFechasUnicas() {
        return dias == null || dias.stream().map(Dia::fecha).filter(java.util.Objects::nonNull).distinct().count() == dias.size();
    }

    @AssertTrue(message = "Las claves de asignación del paquete deben ser únicas")
    @JsonIgnore
    public boolean isClavesAsignacionUnicas() {
        if (dias == null) return true;
        var keys = new HashSet<String>();
        for (Dia day : dias) {
            if (day == null || day.actividades() == null) continue;
            for (Actividad activity : day.actividades()) {
                if (activity != null && activity.claveCliente() != null && !keys.add(activity.claveCliente())) return false;
            }
        }
        return true;
    }

    public record Dia(
            Long diaId,
            @NotNull LocalDate fecha,
            @NotNull @Size(max = 30) List<@NotNull @Valid Actividad> actividades) {
        public Dia { actividades = actividades == null ? null : List.copyOf(actividades); }

        @AssertTrue(message = "El orden de las actividades debe ser único dentro de cada día")
        @JsonIgnore
        public boolean isOrdenUnico() {
            if (actividades == null) return true;
            var orders = new HashSet<Integer>();
            for (Actividad activity : actividades) {
                if (activity != null && activity.orden() != null && !orders.add(activity.orden())) return false;
            }
            return true;
        }
    }

    public record Actividad(
            @NotBlank @Size(max = 160) String claveCliente,
            @NotNull TipoActividad tipo,
            @Positive Long actividadId,
            @Size(max = 40) String fuente,
            @Size(max = 500) String idExterno,
            @NotBlank @Size(max = 150) String nombre,
            @Size(max = 500) String descripcion,
            @Size(max = 250) String ubicacion,
            // Coordinates are stored as exact JSON numbers in the package CLOB, not in the
            // legacy NUMBER(10,7) activity columns. Preserve provider precision; range checks
            // still reject invalid geographic locations without rounding or truncating them.
            @DecimalMin("-90") @DecimalMax("90") BigDecimal latitud,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitud,
            @NotNull @Positive Integer orden,
            LocalTime horaLocal,
            @Positive Integer duracionMinutos,
            @NotNull @Valid Costo costo) {

        @AssertTrue(message = "La actividad externa requiere fuente e identificador externo; la interna requiere ID")
        @JsonIgnore
        public boolean isIdentidadValida() {
            if (tipo == null) return true;
            return switch (tipo) {
                case INTERNA -> actividadId != null && idExterno == null;
                case EXTERNA -> actividadId == null && fuente != null && !fuente.isBlank()
                        && idExterno != null && !idExterno.isBlank();
                case MANUAL -> actividadId == null && idExterno == null;
            };
        }

        @AssertTrue(message = "Las coordenadas deben enviarse juntas")
        @JsonIgnore
        public boolean isParCoordenadasValido() { return (latitud == null) == (longitud == null); }
    }

    public record Costo(
            @DecimalMin("0") @Digits(integer = 10, fraction = 2)
            BigDecimal importe,
            @Pattern(regexp = "[A-Z]{3}") String moneda,
            @Size(max = 20) String unidad,
            @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal cantidad,
            @NotNull TipoCosto tipo,
            @Size(max = 300) String fuente,
            @Size(max = 40) String consultadoEn,
            @Size(max = 1000) String evidencia) {
        @AssertTrue(message = "Un costo desconocido no puede incluir importe y un costo con importe requiere moneda")
        @JsonIgnore
        public boolean isEstadoConsistente() {
            if (tipo == null) return true;
            if (tipo == TipoCosto.DESCONOCIDO) return importe == null && moneda == null;
            if (importe == null || importe.signum() < 0) return false;
            if (tipo == TipoCosto.GRATUITO_VERIFICADO || tipo == TipoCosto.GRATUITO_SUPUESTO_MANUAL)
                return importe.signum() == 0;
            return moneda != null && moneda.matches("[A-Z]{3}");
        }
        @AssertTrue(message = "La fecha de consulta debe ser ISO-8601")
        @JsonIgnore
        public boolean isFechaConsultaValida() { return consultadoEn == null || isIsoDateTime(consultadoEn); }
    }

    public record VueloSeleccion(
            @Positive Long vueloId,
            @Size(max = 40) String proveedor,
            @Size(max = 500) String idOferta,
            @Pattern(regexp = "[A-Z]{3}") String moneda,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal importe,
            @NotNull TipoCosto tipo,
            @Size(max = 40) String origen,
            @Size(max = 40) String destino,
            @Size(max = 40) String salida,
            @Size(max = 40) String llegada,
            @NotEmpty @Size(max = 20) List<@NotNull @Valid TramoVuelo> tramos) {
        public VueloSeleccion { tramos = tramos == null ? null : List.copyOf(tramos); }
        @AssertTrue(message = "Una oferta Sandbox no puede presentarse como cotización real")
        @JsonIgnore
        public boolean isSandboxConsistente() { return tipo != TipoCosto.SANDBOX || vueloId == null; }
        @AssertTrue(message = "Una tarifa de vuelo requiere moneda si tiene importe")
        @JsonIgnore
        public boolean isMonedaConsistente() { return importe == null || moneda != null && moneda.matches("[A-Z]{3}"); }
        @AssertTrue(message = "Las fechas del vuelo deben ser ISO-8601")
        @JsonIgnore
        public boolean isFechaValida() { return (salida == null || isIsoDateTime(salida)) && (llegada == null || isIsoDateTime(llegada)); }
        public VueloSeleccion(Long vueloId, String proveedor, String idOferta, String moneda, BigDecimal importe,
                              TipoCosto tipo, String origen, String destino, LocalDateTime salida,
                              LocalDateTime llegada, List<TramoVuelo> tramos) {
            this(vueloId, proveedor, idOferta, moneda, importe, tipo, origen, destino,
                    salida == null ? null : salida.toString(), llegada == null ? null : llegada.toString(), tramos);
        }
    }

    /** One leg of a selected provider offer; an offer may include connections or round trip. */
    public record TramoVuelo(
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String origenIata,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String destinoIata,
            @NotBlank @Size(max = 40) String salida,
            @NotBlank @Size(max = 40) String llegada,
            @Size(max = 150) String aerolinea,
            @Size(max = 50) String numeroVuelo,
            @PositiveOrZero Integer numeroTrayecto) {
        @AssertTrue(message = "Las fechas del tramo deben ser ISO-8601")
        @JsonIgnore
        public boolean isFechaValida() { return isIsoDateTime(salida) && isIsoDateTime(llegada); }
        public TramoVuelo(String origenIata, String destinoIata, LocalDateTime salida, LocalDateTime llegada,
                          String aerolinea, String numeroVuelo) {
            this(origenIata, destinoIata, salida == null ? null : salida.toString(),
                    llegada == null ? null : llegada.toString(), aerolinea, numeroVuelo, null);
        }
        public TramoVuelo(String origenIata, String destinoIata, String salida, String llegada,
                          String aerolinea, String numeroVuelo) {
            this(origenIata, destinoIata, salida, llegada, aerolinea, numeroVuelo, null);
        }
    }

    public record AlojamientoSeleccion(
            @Positive Long alojamientoId,
            @Size(max = 40) String fuente,
            @Size(max = 500) String idExterno,
            @Size(max = 200) String nombre,
            @Size(max = 300) String direccion,
            // The package snapshot is a CLOB; unlike legacy relational rows, it has no scale 7.
            @DecimalMin("-90") @DecimalMax("90") BigDecimal latitud,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitud,
            @NotNull @Valid Costo costo,
            @Positive Integer noches,
            @Positive Integer habitaciones,
            @Positive Integer huespedes) {
        @AssertTrue(message = "Las coordenadas del alojamiento deben enviarse juntas")
        @JsonIgnore
        public boolean isParCoordenadasValido() { return (latitud == null) == (longitud == null); }
        @AssertTrue(message = "El alojamiento identificado requiere nombre")
        @JsonIgnore
        public boolean isNombreValido() { return alojamientoId == null && idExterno == null || nombre != null && !nombre.isBlank(); }
    }

    public record Gasto(
            @NotBlank @Size(max = 160) String claveEstable,
            @NotBlank @Size(max = 30) String categoria,
            @NotBlank @Size(max = 200) String concepto,
            @Valid @NotNull Costo costo) { }

    public enum TipoActividad { INTERNA, EXTERNA, MANUAL }
    public enum TipoCosto { COTIZACION_REAL, PRECIO_PUBLICADO, ESTIMACION_SUSTENTADA,
        ESTIMACION_MANUAL, CONFIRMADO_POR_USUARIO, GRATUITO_VERIFICADO, GRATUITO_SUPUESTO_MANUAL, DESCONOCIDO, SANDBOX }

    private static boolean isIsoDateTime(String value) {
        if (value == null || value.isBlank()) return false;
        try { LocalDateTime.parse(value); return true; } catch (DateTimeException ignored) { }
        try { OffsetDateTime.parse(value); return true; } catch (DateTimeException ignored) { }
        try { Instant.parse(value); return true; } catch (DateTimeException ignored) { return false; }
    }
}
