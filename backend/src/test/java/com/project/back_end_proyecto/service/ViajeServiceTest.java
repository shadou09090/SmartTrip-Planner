package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.dto.CrearViajeRequest;
import com.project.back_end_proyecto.dto.ViajeResponse;
import com.project.back_end_proyecto.entity.Viaje;
import com.project.back_end_proyecto.exception.DatosInvalidosException;
import com.project.back_end_proyecto.exception.RecursoNoEncontradoException;
import com.project.back_end_proyecto.repository.ViajeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViajeServiceTest {

    @Mock
    private ViajeRepository viajeRepository;

    @InjectMocks
    private ViajeService viajeService;

    private CrearViajeRequest pedido(LocalDate inicio, LocalDate fin) {
        CrearViajeRequest request = new CrearViajeRequest();
        request.setDestino("  Cartagena ");
        request.setFechaInicio(inicio);
        request.setFechaFin(fin);
        request.setPresupuesto(new BigDecimal("2500000"));
        request.setIntereses(List.of(" playa", "gastronomia "));
        return request;
    }

    @Test
    void creaElViajeLimpiandoEspacios() {
        when(viajeRepository.save(any(Viaje.class))).thenAnswer(inv -> inv.getArgument(0));

        ViajeResponse respuesta = viajeService.crear(
                pedido(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5)));

        assertEquals("Cartagena", respuesta.destino());
        assertEquals(new BigDecimal("2500000"), respuesta.presupuesto());
        assertEquals(List.of("playa", "gastronomia"), respuesta.intereses());
        assertEquals(LocalDate.of(2026, 12, 5), respuesta.fechaFin());
    }

    @Test
    void permiteViajeDeUnSoloDia() {
        when(viajeRepository.save(any(Viaje.class))).thenAnswer(inv -> inv.getArgument(0));

        ViajeResponse respuesta = viajeService.crear(
                pedido(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 1)));

        assertEquals(respuesta.fechaInicio(), respuesta.fechaFin());
    }

    @Test
    void rechazaFechaFinAnteriorAlInicio() {
        assertThrows(DatosInvalidosException.class, () -> viajeService.crear(
                pedido(LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 1))));

        verify(viajeRepository, never()).save(any(Viaje.class));
    }

    @Test
    void obtenerLanzaErrorSiNoExiste() {
        when(viajeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> viajeService.obtener(99L));
    }
}
