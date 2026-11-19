package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.dto.LoginRequest;
import com.project.back_end_proyecto.dto.LoginResponse;
import com.project.back_end_proyecto.dto.SignupRequest;
import com.project.back_end_proyecto.dto.UserSummary;
import com.project.back_end_proyecto.entity.User;
import com.project.back_end_proyecto.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private UserRepository userRepository;
    private UserRegistrationPersistenceService registrationPersistenceService;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        registrationPersistenceService = mock(UserRegistrationPersistenceService.class);
        passwordEncoder = new BCryptPasswordEncoder();
        jwtService = mock(JwtService.class);
        authService = new AuthService(userRepository, registrationPersistenceService, passwordEncoder, jwtService);
    }

    @Test
    void registerStoresOnlyBcryptHashAndNormalizesEmail() {
        SignupRequest request = signupRequest("  Daniel@Example.COM  ", "correct-horse");
        when(userRepository.existsByEmailIgnoreCase("daniel@example.com")).thenReturn(false);
        doAnswer(invocation -> null).when(registrationPersistenceService).saveNewUser(any(User.class));

        assertDoesNotThrow(() -> authService.register(request));

        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(registrationPersistenceService).saveNewUser(captor.capture());
        User saved = captor.getValue();
        assertEquals("daniel@example.com", saved.getEmail());
        assertNotEquals(request.getPassword(), saved.getPassword());
        assertTrue(passwordEncoder.matches(request.getPassword(), saved.getPassword()));
    }

    @Test
    void registerExistingEmailReturnsNormallyWithoutSavingAnotherUser() {
        SignupRequest request = signupRequest("daniel@example.com", "correct-horse");
        when(userRepository.existsByEmailIgnoreCase(request.getEmail())).thenReturn(true);

        assertDoesNotThrow(() -> authService.register(request));
        verifyNoInteractions(registrationPersistenceService);
    }

    @Test
    void concurrentDuplicateIsHandledAfterTheInsertTransactionRollsBack() {
        SignupRequest request = signupRequest("daniel@example.com", "correct-horse");
        when(userRepository.existsByEmailIgnoreCase(request.getEmail())).thenReturn(false, true);
        doThrow(new DataIntegrityViolationException("unique conflict"))
                .when(registrationPersistenceService).saveNewUser(any(User.class));

        assertDoesNotThrow(() -> authService.register(request));
        verify(registrationPersistenceService).saveNewUser(any(User.class));
        verify(userRepository, times(2)).existsByEmailIgnoreCase(request.getEmail());
    }

    @Test
    void unrelatedIntegrityFailureIsNotReportedAsAHandledDuplicate() {
        SignupRequest request = signupRequest("daniel@example.com", "correct-horse");
        when(userRepository.existsByEmailIgnoreCase(request.getEmail())).thenReturn(false);
        doThrow(new DataIntegrityViolationException("other constraint"))
                .when(registrationPersistenceService).saveNewUser(any(User.class));

        assertThrows(DataIntegrityViolationException.class, () -> authService.register(request));
    }

    @Test
    void loginIssuesTokenOnlyForMatchingPersistedPassword() {
        String rawPassword = "correct-horse";
        User user = new User("Daniel Example", "daniel@example.com", passwordEncoder.encode(rawPassword));
        LoginResponse response = new LoginResponse("signed-token", "Bearer", 1200,
                new UserSummary(12L, user.getName(), user.getEmail()));
        when(userRepository.findByEmailIgnoreCase("daniel@example.com")).thenReturn(Optional.of(user));
        when(jwtService.createLoginResponse(user)).thenReturn(response);

        assertEquals(Optional.of(response), authService.login(loginRequest("daniel@example.com", rawPassword)));
    }

    @Test
    void loginRejectsWrongPasswordAndUnknownEmailWithoutIssuingToken() {
        User user = new User("Daniel Example", "daniel@example.com", passwordEncoder.encode("correct-horse"));
        when(userRepository.findByEmailIgnoreCase("daniel@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

        assertTrue(authService.login(loginRequest("daniel@example.com", "wrong-password")).isEmpty());
        assertTrue(authService.login(loginRequest("unknown@example.com", "wrong-password")).isEmpty());
        verifyNoInteractions(jwtService);
    }

    private SignupRequest signupRequest(String email, String password) {
        SignupRequest request = new SignupRequest();
        request.setName("Daniel Example");
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }
}
