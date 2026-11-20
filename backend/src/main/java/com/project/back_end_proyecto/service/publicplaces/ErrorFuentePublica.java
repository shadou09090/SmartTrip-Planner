package com.project.back_end_proyecto.service.publicplaces;

import java.util.List;

public class ErrorFuentePublica extends RuntimeException {
    private final List<String> fuentesConsultadas;
    private final List<String> avisos;

    public ErrorFuentePublica(String message) { this(message, null, List.of(), List.of()); }
    public ErrorFuentePublica(String message, Throwable cause) { this(message, cause, List.of(), List.of()); }
    public ErrorFuentePublica(String message, Throwable cause, List<String> fuentesConsultadas, List<String> avisos) {
        super(message, cause);
        this.fuentesConsultadas = fuentesConsultadas == null ? List.of() : List.copyOf(fuentesConsultadas);
        this.avisos = avisos == null ? List.of() : List.copyOf(avisos);
    }
    public List<String> fuentesConsultadas() { return fuentesConsultadas; }
    public List<String> avisos() { return avisos; }
}
