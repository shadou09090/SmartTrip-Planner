package com.project.back_end_proyecto.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException() {
        super("No se encontró el recurso solicitado.");
    }
}
