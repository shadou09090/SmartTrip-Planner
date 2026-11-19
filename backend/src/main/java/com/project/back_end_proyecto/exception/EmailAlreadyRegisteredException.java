package com.project.back_end_proyecto.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {
    public EmailAlreadyRegisteredException() {
        super("No fue posible registrar la cuenta con esos datos.");
    }
}
