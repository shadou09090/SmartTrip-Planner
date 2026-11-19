package com.project.back_end_proyecto.exception;

import com.project.back_end_proyecto.dto.ApiError;
import com.project.back_end_proyecto.service.publicplaces.ErrorFuentePublica;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> responseStatus(ResponseStatusException exception) {
        String message = StringUtils.hasText(exception.getReason())
                ? exception.getReason()
                : "La solicitud no pudo completarse.";
        return ResponseEntity.status(exception.getStatusCode()).body(new ApiError(message));
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ResponseEntity<ApiError> duplicateEmail(EmailAlreadyRegisteredException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(exception.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> notFound(ResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(exception.getMessage()));
    }

    @ExceptionHandler(ErrorFuentePublica.class)
    ResponseEntity<ApiError> publicSourceUnavailable(ErrorFuentePublica exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidRequest(MethodArgumentNotValidException exception, HttpServletRequest request) {
        // Log field + constraint only: never log rejected values or the request body.
        exception.getBindingResult().getFieldErrors().stream().limit(5).forEach(error ->
                log.warn("Request validation rejected field={} constraint={}", error.getField(),
                        error.getCode() == null ? "unknown" : error.getCode()));
        var fields = exception.getBindingResult().getFieldErrors().stream().limit(50)
                .map(error -> new ApiError.FieldIssue(error.getField(), validationMessage(error.getCode(), error.getDefaultMessage())))
                .toList();
        String code = request.getRequestURI() != null && request.getRequestURI().matches("/api/viajes/[^/]+/paquete")
                ? "PACKAGE_VALIDATION_ERROR" : "VALIDATION_ERROR";
        String message = code.equals("PACKAGE_VALIDATION_ERROR")
                ? "El paquete contiene datos inválidos. Revisa los campos indicados."
                : "La solicitud contiene datos inválidos. Revisa los campos indicados.";
        return ResponseEntity.badRequest().body(new ApiError(message, code, fields));
    }

    private static String validationMessage(String constraint, String defaultMessage) {
        if ("NotEmpty".equals(constraint) || "NotBlank".equals(constraint)) return "No puede estar vacío.";
        if ("NotNull".equals(constraint)) return "Es obligatorio.";
        if ("Digits".equals(constraint)) return "El valor tiene una precisión numérica no admitida.";
        return defaultMessage == null ? "El valor no es válido." : defaultMessage;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> malformedRequest() {
        return ResponseEntity.badRequest().body(new ApiError("El cuerpo de la solicitud no tiene un formato válido."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> invalidOperation(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ApiError(exception.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> integrityViolation() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("La operación entra en conflicto con un dato existente."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpectedFailure(Exception exception, HttpServletRequest request) {
        String failureId = UUID.randomUUID().toString();
        Throwable root = exception;
        java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        while (root.getCause() != null && seen.add(root)) root = root.getCause();
        // Log stack locations and exception types only. Do not log request paths, headers,
        // payloads, exception messages, SQL, or provider URLs that could contain secrets.
        String frames = java.util.Arrays.stream(exception.getStackTrace()).limit(8)
                .map(frame -> frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber())
                .collect(java.util.stream.Collectors.joining(" <- "));
        log.error("Unhandled API failure id={} method={} exception={} rootCause={} frames={}", failureId,
                request.getMethod(), exception.getClass().getName(), root.getClass().getName(), frames);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("La solicitud no pudo completarse. Referencia: " + failureId));
    }
}
