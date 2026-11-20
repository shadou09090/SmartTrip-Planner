package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.dto.LoginRequest;
import com.project.back_end_proyecto.dto.LoginResponse;
import com.project.back_end_proyecto.dto.SignupRequest;
import com.project.back_end_proyecto.dto.UserSummary;
import com.project.back_end_proyecto.entity.User;
import com.project.back_end_proyecto.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@Profile("!local-no-db")
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

    @Autowired
    public AuthService(UserRepository userRepository, UserRegistrationPersistenceService registrationPersistenceService,
                       PasswordEncoder passwordEncoder, ObjectProvider<JwtService> jwtServiceProvider) {
        this(userRepository, registrationPersistenceService, passwordEncoder, jwtServiceProvider.getIfAvailable());
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
        if (jwtService != null) {
            return Optional.of(jwtService.createLoginResponse(authenticatedUser));
        }
        return Optional.of(new LoginResponse(
                "dev-token",
                "Bearer",
                1200L,
                new UserSummary(authenticatedUser.getId(), authenticatedUser.getName(), authenticatedUser.getEmail())
        ));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void performDummyPasswordWork(String rawPassword) {
        passwordEncoder.matches(rawPassword, dummyPasswordHash);
    }
}
