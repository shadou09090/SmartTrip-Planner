package com.project.back_end_proyecto.service.publicplaces;

import java.util.List;
import java.util.Optional;
import java.util.Map;

public interface FuenteLugaresPublicos {
    List<LugarPublico> buscar(String destino);

    default ResultadoLugares buscarConDiagnostico(String destino) {
        return new ResultadoLugares(buscar(destino), List.of());
    }

    default String areaGeograficaConsultada(String destino) { return "No informada por la fuente"; }

    default List<LugarPublico> buscarAlojamientos(String destino) { return List.of(); }

    default Optional<CoordenadasPublicas> geocodificarDestino(String destino) { return Optional.empty(); }

    default List<DestinoCandidato> buscarDestinos(String consulta) { return List.of(); }

    record ResultadoLugares(List<LugarPublico> lugares, List<String> avisos,
                            List<String> fuentesConsultadas, Map<String, Integer> resultadosConservadosPorFuente,
                            String areaGeografica) {
        public ResultadoLugares(List<LugarPublico> lugares, List<String> avisos) {
            this(lugares, avisos, List.of(), Map.of(), null);
        }
        public ResultadoLugares(List<LugarPublico> lugares, List<String> avisos,
                                List<String> fuentesConsultadas, Map<String, Integer> resultadosConservadosPorFuente) {
            this(lugares, avisos, fuentesConsultadas, resultadosConservadosPorFuente, null);
        }
        public ResultadoLugares {
            lugares = lugares == null ? List.of() : List.copyOf(lugares);
            avisos = avisos == null ? List.of() : List.copyOf(avisos);
            fuentesConsultadas = fuentesConsultadas == null ? List.of() : List.copyOf(fuentesConsultadas);
            resultadosConservadosPorFuente = resultadosConservadosPorFuente == null
                    ? Map.of() : Map.copyOf(resultadosConservadosPorFuente);
        }
    }
}
