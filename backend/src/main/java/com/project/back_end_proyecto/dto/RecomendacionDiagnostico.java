package com.project.back_end_proyecto.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Safe per-search counters; contains no credentials or provider response payloads. */
public record RecomendacionDiagnostico(String destinoConsultado, BigDecimal latitudCentro,
                                       BigDecimal longitudCentro, List<String> fuentesConsultadas,
                                       Map<String, Integer> resultadosConservadosPorFuente,
                                       Map<String, Integer> descartes, int atractivosFinales,
                                       List<String> errores, String areaGeografica,
                                       Map<String, Integer> clasificacionCandidatos,
                                       Map<String, Integer> razonesExclusion) {
    public RecomendacionDiagnostico(String destinoConsultado, BigDecimal latitudCentro, BigDecimal longitudCentro,
                                    List<String> fuentesConsultadas, Map<String, Integer> resultadosConservadosPorFuente,
                                    Map<String, Integer> descartes, int atractivosFinales, List<String> errores) {
        this(destinoConsultado, latitudCentro, longitudCentro, fuentesConsultadas,
                resultadosConservadosPorFuente, descartes, atractivosFinales, errores, null, Map.of(), Map.of());
    }
    public RecomendacionDiagnostico(String destinoConsultado, BigDecimal latitudCentro, BigDecimal longitudCentro,
                                    List<String> fuentesConsultadas, Map<String, Integer> resultadosConservadosPorFuente,
                                    Map<String, Integer> descartes, int atractivosFinales, List<String> errores,
                                    String areaGeografica) {
        this(destinoConsultado, latitudCentro, longitudCentro, fuentesConsultadas, resultadosConservadosPorFuente,
                descartes, atractivosFinales, errores, areaGeografica, Map.of(), Map.of());
    }
    public RecomendacionDiagnostico {
        fuentesConsultadas = fuentesConsultadas == null ? List.of() : List.copyOf(fuentesConsultadas);
        resultadosConservadosPorFuente = resultadosConservadosPorFuente == null
                ? Map.of() : Map.copyOf(resultadosConservadosPorFuente);
        descartes = descartes == null ? Map.of() : Map.copyOf(descartes);
        errores = errores == null ? List.of() : List.copyOf(errores);
        clasificacionCandidatos = clasificacionCandidatos == null ? Map.of() : Map.copyOf(clasificacionCandidatos);
        razonesExclusion = razonesExclusion == null ? Map.of() : Map.copyOf(razonesExclusion);
    }
}
