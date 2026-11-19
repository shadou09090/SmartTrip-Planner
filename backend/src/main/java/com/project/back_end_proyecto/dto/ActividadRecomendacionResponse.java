package com.project.back_end_proyecto.dto;

import java.util.List;

public record ActividadRecomendacionResponse(
        boolean requiereSeleccionarIntereses,
        List<ActividadRecomendada> actividades,
        List<RecomendacionExterna> recomendacionesExternas,
        List<String> avisosFuentes,
        RecomendacionDiagnostico diagnostico
) {
    public ActividadRecomendacionResponse(boolean requiereSeleccionarIntereses, List<ActividadRecomendada> actividades) {
        this(requiereSeleccionarIntereses, actividades, List.of(), List.of(), null);
    }
    public ActividadRecomendacionResponse(boolean requiereSeleccionarIntereses, List<ActividadRecomendada> actividades,
                                          List<RecomendacionExterna> recomendacionesExternas, List<String> avisosFuentes) {
        this(requiereSeleccionarIntereses, actividades, recomendacionesExternas, avisosFuentes, null);
    }
    public record ActividadRecomendada(ActividadResponse actividad, List<String> interesesCoincidentes) { }
}
