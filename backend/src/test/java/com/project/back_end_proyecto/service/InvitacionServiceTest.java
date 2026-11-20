package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.dto.InvitacionRequest;
import com.project.back_end_proyecto.entity.RolColaborador;
import com.project.back_end_proyecto.entity.User;
import com.project.back_end_proyecto.entity.Viaje;
import com.project.back_end_proyecto.repository.ColaboradorViajeRepository;
import com.project.back_end_proyecto.repository.InvitacionRepository;
import com.project.back_end_proyecto.repository.UserRepository;
import com.project.back_end_proyecto.repository.ViajeRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvitacionServiceTest {

    @Test
    void invitationsExpireWithinFortyEightHours() {
        ViajeRepository trips = mock(ViajeRepository.class);
        UserRepository users = mock(UserRepository.class);
        ColaboradorViajeRepository collaborators = mock(ColaboradorViajeRepository.class);
        InvitacionRepository invitations = mock(InvitacionRepository.class);
        User owner = new User("Owner", "owner@example.test", "bcrypt-hash");
        Viaje trip = new Viaje(owner, "Bogota", "Cali", LocalDate.now(), LocalDate.now().plusDays(1),
                new BigDecimal("100.00"));

        when(trips.findByIdAndUsuario_Id(9L, 4L)).thenReturn(Optional.of(trip));
        when(users.findById(4L)).thenReturn(Optional.of(owner));
        when(users.findByEmailIgnoreCase("guest@example.test")).thenReturn(Optional.empty());
        when(invitations.existsByToken(anyString())).thenReturn(false);
        when(invitations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime beforeCreation = LocalDateTime.now();
        var response = new InvitacionService(trips, users, collaborators, invitations).crear(4L, 9L,
                new InvitacionRequest("guest@example.test", RolColaborador.READER));
        LocalDateTime afterCreation = LocalDateTime.now();
        LocalDateTime minimumExpectedExpiry = beforeCreation.plusHours(48);
        LocalDateTime maximumExpectedExpiry = afterCreation.plusHours(48);

        assertFalse(response.expiresAt().isBefore(minimumExpectedExpiry));
        assertTrue(response.expiresAt().isBefore(maximumExpectedExpiry.plusNanos(1)));
    }
}
