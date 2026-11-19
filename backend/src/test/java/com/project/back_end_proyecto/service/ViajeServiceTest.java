package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.dto.ViajeCreateRequest;
import com.project.back_end_proyecto.entity.User;
import com.project.back_end_proyecto.entity.Viaje;
import com.project.back_end_proyecto.repository.InteresRepository;
import com.project.back_end_proyecto.exception.ResourceNotFoundException;
import com.project.back_end_proyecto.repository.UserRepository;
import com.project.back_end_proyecto.repository.ViajeRepository;
import com.project.back_end_proyecto.repository.ViajeInteresRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ViajeServiceTest {

    private ViajeRepository viajeRepository;
    private UserRepository userRepository;
    private InteresRepository interesRepository;
    private ViajeInteresRepository viajeInteresRepository;
    private TripPermissionService tripPermissionService;
    private ViajeDeletionService viajeDeletionService;
    private ViajeService viajeService;

    @BeforeEach
    void setUp() {
        viajeRepository = mock(ViajeRepository.class);
        userRepository = mock(UserRepository.class);
        interesRepository = mock(InteresRepository.class);
        viajeInteresRepository = mock(ViajeInteresRepository.class);
        tripPermissionService = mock(TripPermissionService.class);
        viajeDeletionService = mock(ViajeDeletionService.class);
        viajeService = new ViajeService(viajeRepository, userRepository, interesRepository, viajeInteresRepository,
                tripPermissionService, viajeDeletionService);
        when(viajeInteresRepository.findAllByViaje_Id(anyLong())).thenReturn(List.of());
    }

    @Test
    void createsTripOnlyForUserResolvedByAuthenticatedId() {
        User owner = new User("Owner", "owner@example.com", "bcrypt-hash");
        when(userRepository.findById(41L)).thenReturn(Optional.of(owner));
        when(viajeRepository.save(any(Viaje.class))).thenAnswer(invocation -> {
            Viaje viaje = invocation.getArgument(0);
            return viaje;
        });

        var response = viajeService.create(41L, new ViajeCreateRequest(
                "Bogotá", "Cali", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), new BigDecimal("0.00")
        ));

        assertEquals("Cali", response.destino());
        var captor = org.mockito.ArgumentCaptor.forClass(Viaje.class);
        verify(viajeRepository).save(captor.capture());
        assertSame(owner, captor.getValue().getUsuario());
        verify(userRepository).findById(41L);
    }

    @Test
    void listAndDetailQueriesAreAlwaysScopedToAuthenticatedUser() {
        when(tripPermissionService.listAccessible(41L)).thenReturn(List.of());
        assertTrue(viajeService.listForUser(41L).isEmpty());
        when(tripPermissionService.requireRead(41L, 9L)).thenThrow(new ResourceNotFoundException());
        assertThrows(ResourceNotFoundException.class, () -> viajeService.getForUser(41L, 9L));
        verify(tripPermissionService).requireRead(41L, 9L);
    }

    @Test
    void updateAndDeleteAreScopedToAuthenticatedUser() {
        User owner = new User("Owner", "owner@example.com", "bcrypt-hash");
        Viaje trip = new Viaje(owner, "Bogotá", "Cali", LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 3), new BigDecimal("100.00"));
        when(tripPermissionService.requireEdit(41L, 9L)).thenReturn(trip);
        when(viajeRepository.save(any(Viaje.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var updated = viajeService.updateForUser(41L, 9L, new ViajeCreateRequest(
                "Bogotá", "Pereira", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3), new BigDecimal("200.00")
        ));
        assertEquals("Pereira", updated.destino());
        verify(tripPermissionService).requireEdit(41L, 9L);

        viajeService.deleteForUser(41L, 9L);
        verify(viajeDeletionService).deleteOwnedTrip(41L, 9L);
    }

    @Test
    void updateAndDeleteRejectTripsOwnedByAnotherUser() {
        when(tripPermissionService.requireEdit(41L, 9L)).thenThrow(new ResourceNotFoundException());
        doThrow(new ResourceNotFoundException()).when(viajeDeletionService).deleteOwnedTrip(41L, 9L);
        var request = new ViajeCreateRequest("Bogotá", "Cali", LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 3), BigDecimal.ZERO);

        assertThrows(ResourceNotFoundException.class, () -> viajeService.updateForUser(41L, 9L, request));
        assertThrows(ResourceNotFoundException.class, () -> viajeService.deleteForUser(41L, 9L));
        verify(viajeDeletionService).deleteOwnedTrip(41L, 9L);
    }

    @Test
    void createsInterestCatalogEntriesAndAssociatesThemWithTheTrip() {
        User owner = new User("Owner", "owner@example.invalid", "hash");
        when(userRepository.findById(41L)).thenReturn(Optional.of(owner));
        when(viajeRepository.save(any(Viaje.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(interesRepository.findByNombreIgnoreCase("Aventura")).thenReturn(Optional.empty());
        when(interesRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(viajeInteresRepository.findAllByViaje_Id(anyLong())).thenReturn(List.of());

        viajeService.create(41L, new ViajeCreateRequest("Bogotá", "Cali", LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 3), BigDecimal.ZERO, List.of("Aventura", "Aventura")));

        verify(interesRepository).save(any());
        verify(viajeInteresRepository).save(any());
    }
}
