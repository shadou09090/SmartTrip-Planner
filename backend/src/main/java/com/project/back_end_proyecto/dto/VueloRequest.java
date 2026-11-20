package com.project.back_end_proyecto.dto;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record VueloRequest(@NotBlank @Size(max = 150) String origen, @NotBlank @Size(max = 150) String destino,
                           @NotNull LocalDateTime fechaSalida, LocalDateTime fechaLlegada,
                           @Size(max = 150) String aerolinea, @Size(max = 50) String numeroVuelo,
                           @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal precio) {
    @AssertTrue(message = "La fecha de llegada debe ser igual o posterior a la salida")
    public boolean isRangoFechasValido() { return fechaLlegada == null || fechaSalida == null || !fechaLlegada.isBefore(fechaSalida); }
}
