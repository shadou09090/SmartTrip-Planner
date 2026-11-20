package com.project.back_end_proyecto.dto;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
public record AlojamientoRequest(@NotBlank @Size(max = 200) String nombre, @Size(max = 300) String direccion,
                                 @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 7) BigDecimal latitud,
                                 @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 7) BigDecimal longitud) {
    public AlojamientoRequest {
        latitud = fitOracleCoordinate(latitud, new BigDecimal("-90"), new BigDecimal("90"), "Latitud");
        longitud = fitOracleCoordinate(longitud, new BigDecimal("-180"), new BigDecimal("180"), "Longitud");
    }

    private static BigDecimal fitOracleCoordinate(BigDecimal value, BigDecimal minimum,
                                                   BigDecimal maximum, String label) {
        if (value == null) return null;
        if (value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(label + " fuera del rango geográfico válido.");
        }
        // ALOJAMIENTO uses NUMBER(10,7); normalize provider decimals before JDBC binding.
        return value.setScale(7, RoundingMode.HALF_UP);
    }
}
