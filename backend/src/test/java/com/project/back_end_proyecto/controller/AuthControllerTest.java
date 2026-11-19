package com.project.back_end_proyecto.controller;

import com.project.back_end_proyecto.dto.LoginRequest;
import com.project.back_end_proyecto.dto.LoginResponse;
import com.project.back_end_proyecto.dto.SignupRequest;
import com.project.back_end_proyecto.dto.UserSummary;
import com.project.back_end_proyecto.entity.User;
import com.project.back_end_proyecto.repository.UserRepository;
import com.project.back_end_proyecto.service.AuthService;
import com.project.back_end_proyecto.service.JwtService;
import com.project.back_end_proyecto.service.UserRegistrationPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthControllerTest {
    private UserRepository userRepository;
    private UserRegistrationPersistenceService persistenceService;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        persistenceService = mock(UserRegistrationPersistenceService.class);
        passwordEncoder = new BCryptPasswordEncoder();
        jwtService = mock(JwtService.class);
        controller = new AuthController(new AuthService(userRepository, persistenceService, passwordEncoder, jwtService));
    }

    @Test
    void signupUsesSameStatusAndMessageForNewAndExistingEmailsWithoutDuplicating() {
        when(userRepository.existsByEmailIgnoreCase("member@example.com")).thenReturn(false, true);
        SignupRequest request = signupRequest();

        var newEmailResponse = controller.signup(request);
        var existingEmailResponse = controller.signup(request);

        assertEquals(HttpStatus.OK, newEmailResponse.getStatusCode());
        assertEquals(newEmailResponse.getStatusCode(), existingEmailResponse.getStatusCode());
        assertEquals(newEmailResponse.getBody(), existingEmailResponse.getBody());
        assertNotNull(newEmailResponse.getBody());
        verify(persistenceService, times(1)).saveNewUser(any(User.class));
    }

    @Test
    void loginReturnsSuccessForCorrectPasswordAndSameGenericUnauthorizedForBothFailures() {
        String email = "member@example.com";
        User user = new User("Test Member", email, passwordEncoder.encode("test-password"));
        when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));
        when(userRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());
        when(jwtService.createLoginResponse(user)).thenReturn(new LoginResponse(
                "synthetic-test-value", "Bearer", 1200, new UserSummary(1L, user.getName(), user.getEmail())));

        var successful = controller.login(loginRequest(email, "test-password"));
        var wrongPassword = controller.login(loginRequest(email, "wrong-password"));
        var unknownEmail = controller.login(loginRequest("unknown@example.com", "wrong-password"));

        assertEquals(HttpStatus.OK, successful.getStatusCode());
        assertInstanceOf(LoginResponse.class, successful.getBody());
        assertEquals(HttpStatus.UNAUTHORIZED, wrongPassword.getStatusCode());
        assertEquals(wrongPassword.getStatusCode(), unknownEmail.getStatusCode());
        assertEquals(wrongPassword.getBody(), unknownEmail.getBody());
        verify(jwtService, times(1)).createLoginResponse(user);
    }

    private SignupRequest signupRequest() {
        SignupRequest request = new SignupRequest();
        request.setName("Test Member");
        request.setEmail("member@example.com");
        request.setPassword("test-password");
        return request;
    }

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }
}
