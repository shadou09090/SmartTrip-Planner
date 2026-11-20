package com.project.back_end_proyecto.dto;

import java.math.BigDecimal;

public record PresupuestoActividadesResponse(
        BigDecimal presupuesto,
        BigDecimal costoAcumulado,
        BigDecimal presupuestoRestante,
        boolean superaPresupuesto,
        long actividadesSinPrecio,
        BigDecimal porcentajeUtilizado
) {
    public PresupuestoActividadesResponse(BigDecimal presupuesto, BigDecimal costoAcumulado,
                                          BigDecimal presupuestoRestante, boolean superaPresupuesto) {
        this(presupuesto, costoAcumulado, presupuestoRestante, superaPresupuesto, 0, null);
    }
}
