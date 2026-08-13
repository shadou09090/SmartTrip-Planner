package com.project.back_end_proyecto.controller;

import com.project.back_end_proyecto.dto.LoginRequest;
import com.project.back_end_proyecto.dto.SignupRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request) {
        System.out.println("=== SIGNUP recibido ===");
        System.out.println("Nombre: " + request.getName());
        System.out.println("Email: " + request.getEmail());
        System.out.println("Password: " + request.getPassword());
        return ResponseEntity.ok("Datos de signup recibidos correctamente");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        System.out.println("=== LOGIN recibido ===");
        System.out.println("Email: " + request.getEmail());
        System.out.println("Password: " + request.getPassword());
        return ResponseEntity.ok("Datos de login recibidos correctamente");
    }
}