package com.project.back_end_proyecto.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void preservesProviderStatusAndSafeReasonInApiError() {
        var response = handler.responseStatus(new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "El proveedor de vuelos no está disponible temporalmente."));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("El proveedor de vuelos no está disponible temporalmente.", response.getBody().message());
    }

    @Test
    void usesGenericMessageWhenExceptionHasNoReason() {
        var response = handler.responseStatus(new ResponseStatusException(HttpStatus.BAD_GATEWAY));

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("La solicitud no pudo completarse.", response.getBody().message());
    }

    @Test
    void returnsStructuredPackageValidationFieldsWithoutRejectedValues() {
        var exception = org.mockito.Mockito.mock(MethodArgumentNotValidException.class);
        var binding = org.mockito.Mockito.mock(BindingResult.class);
        org.mockito.Mockito.when(exception.getBindingResult()).thenReturn(binding);
        org.mockito.Mockito.when(binding.getFieldErrors()).thenReturn(java.util.List.of(
                new FieldError("package", "vueloSeleccionado.tramos", "sensitive-value", false,
                        new String[]{"NotEmpty"}, null, "must not be empty")));
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        org.mockito.Mockito.when(request.getRequestURI()).thenReturn("/api/viajes/341/paquete");

        var response = handler.invalidRequest(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("PACKAGE_VALIDATION_ERROR", response.getBody().code());
        assertEquals("vueloSeleccionado.tramos", response.getBody().fields().get(0).field());
        assertEquals("No puede estar vacío.", response.getBody().fields().get(0).message());
        assertFalse(response.getBody().toString().contains("sensitive-value"));
    }

    @Test
    void unexpectedFailureReturnsSafeReferenceInsteadOfExceptionDetails() {
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        org.mockito.Mockito.when(request.getMethod()).thenReturn("POST");

        var response = handler.unexpectedFailure(new IllegalStateException("secret-like detail"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(response.getBody().message().startsWith("La solicitud no pudo completarse. Referencia: "));
        assertFalse(response.getBody().message().contains("secret-like detail"));
    }
}
