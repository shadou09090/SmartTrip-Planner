package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.dto.LoginRequest;
import com.project.back_end_proyecto.dto.LoginResponse;
import com.project.back_end_proyecto.entity.User;
import com.project.back_end_proyecto.exception.CredencialesInvalidasException;
import com.project.back_end_proyecto.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private LoginRequest pedido(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private User usuario() {
        User user = new User();
        user.setId(1L);
        user.setName("Juan");
        user.setEmail("juan@correo.com");
        user.setPassword("hash");
        return user;
    }

    @Test
    void loginCorrectoDevuelveDatosDelUsuario() {
        when(userRepository.findByEmail("juan@correo.com")).thenReturn(Optional.of(usuario()));
        when(passwordEncoder.matches("clave123", "hash")).thenReturn(true);

        LoginResponse respuesta = authService.login(pedido(" juan@correo.com ", "clave123"));

        assertEquals(1L, respuesta.id());
        assertEquals("Juan", respuesta.name());
        assertEquals("juan@correo.com", respuesta.email());
    }

    @Test
    void correoNoRegistradoLanzaCredencialesInvalidas() {
        when(userRepository.findByEmail("nadie@correo.com")).thenReturn(Optional.empty());

        assertThrows(CredencialesInvalidasException.class,
                () -> authService.login(pedido("nadie@correo.com", "clave123")));
    }

    @Test
    void claveIncorrectaLanzaCredencialesInvalidas() {
        when(userRepository.findByEmail("juan@correo.com")).thenReturn(Optional.of(usuario()));
        when(passwordEncoder.matches("otra", "hash")).thenReturn(false);

        assertThrows(CredencialesInvalidasException.class,
                () -> authService.login(pedido("juan@correo.com", "otra")));
    }
}
