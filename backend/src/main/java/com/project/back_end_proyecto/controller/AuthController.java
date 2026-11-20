package com.project.back_end_proyecto.controller;

import com.project.back_end_proyecto.dto.LoginRequest;
import com.project.back_end_proyecto.dto.LoginResponse;
import com.project.back_end_proyecto.dto.SignupRequest;
import com.project.back_end_proyecto.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Profile("!local-no-db")
public class AuthController {
    private static final String SIGNUP_MESSAGE = "Solicitud recibida. Puedes intentar iniciar sesión con ese correo.";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<String> signup(@Valid @RequestBody SignupRequest request) {
        authService.register(request);
        return ResponseEntity.ok(SIGNUP_MESSAGE);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request).orElse(null);
        if (response == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Correo o contraseña incorrectos.");
        }
        return ResponseEntity.ok(response);
    }
}
