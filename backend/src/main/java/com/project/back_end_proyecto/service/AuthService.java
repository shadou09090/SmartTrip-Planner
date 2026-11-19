package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.dto.LoginRequest;
import com.project.back_end_proyecto.dto.LoginResponse;
import com.project.back_end_proyecto.dto.SignupRequest;
import com.project.back_end_proyecto.dto.UserSummary;
import com.project.back_end_proyecto.entity.User;
import com.project.back_end_proyecto.repository.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@Profile("oracle")
public class AuthService {

    private final UserRepository userRepository;
    private final UserRegistrationPersistenceService registrationPersistenceService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, UserRegistrationPersistenceService registrationPersistenceService,
                       PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.registrationPersistenceService = registrationPersistenceService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public void register(SignupRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            performDummyPasswordWork(request.getPassword());
            return;
        }

        User user = new User(
                request.getName().trim(),
                email,
                passwordEncoder.encode(request.getPassword())
        );
        try {
            registrationPersistenceService.saveNewUser(user);
        } catch (DataIntegrityViolationException conflict) {
            // The unique constraint can win a race after the initial existence check.
            // The persistence bean's transaction has rolled back before this lookup.
            if (!userRepository.existsByEmailIgnoreCase(email)) {
                throw conflict;
            }
            performDummyPasswordWork(request.getPassword());
        }
    }

    @Transactional(readOnly = true)
    public Optional<LoginResponse> login(LoginRequest request) {
        Optional<User> user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.getEmail()));
        String passwordHash = user.map(User::getPassword).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), passwordHash);
        if (!passwordMatches || user.isEmpty()) {
            return Optional.empty();
        }

        User authenticatedUser = user.get();
        return Optional.of(jwtService.createLoginResponse(authenticatedUser));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void performDummyPasswordWork(String rawPassword) {
        passwordEncoder.matches(rawPassword, dummyPasswordHash);
    }
}
