package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.dto.LoginRequest;
import com.project.back_end_proyecto.dto.LoginResponse;
import com.project.back_end_proyecto.entity.User;
import com.project.back_end_proyecto.exception.CredencialesInvalidasException;
import com.project.back_end_proyecto.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(LoginRequest request) {
        String email = request.getEmail().trim();

        // mismo error si el correo no existe o la clave falla
        User user = userRepository.findByEmail(email)
                .orElseThrow(CredencialesInvalidasException::new);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new CredencialesInvalidasException();
        }

        return new LoginResponse(user.getId(), user.getName(), user.getEmail());
    }
}
