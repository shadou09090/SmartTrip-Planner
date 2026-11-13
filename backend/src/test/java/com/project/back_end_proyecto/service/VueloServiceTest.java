package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.dto.VueloDto;
import com.project.back_end_proyecto.exception.DatosInvalidosException;
import com.project.back_end_proyecto.vuelos.ProveedorVuelosSimulado;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VueloServiceTest {

    private final VueloService vueloService = new VueloService(new ProveedorVuelosSimulado());

    private final LocalDate ida = LocalDate.now().plusDays(30);
    private final LocalDate vuelta = ida.plusDays(5);

    @Test
    void soloIdaDevuelveTresOpcionesConCodigosEnMayuscula() {
        List<VueloDto> vuelos = vueloService.buscar("bog", " ctg ", ida, null, null);

        assertEquals(3, vuelos.size());
        for (VueloDto vuelo : vuelos) {
            assertEquals("ida", vuelo.tramo());
            assertEquals("BOG", vuelo.origen());
            assertEquals("CTG", vuelo.destino());
            assertTrue(vuelo.llegada().isAfter(vuelo.salida()));
        }
    }

    @Test
    void idaYVueltaDevuelveSeisOpcionesYElTramoDeVueltaInvierteLaRuta() {
        List<VueloDto> vuelos = vueloService.buscar("BOG", "CTG", ida, vuelta, 2);

        assertEquals(6, vuelos.size());
        assertEquals("ida", vuelos.get(0).tramo());
        assertEquals("vuelta", vuelos.get(5).tramo());
        assertEquals("CTG", vuelos.get(5).origen());
        assertEquals("BOG", vuelos.get(5).destino());
    }

    @Test
    void ordenaDelMasBaratoAlMasCaroDentroDeCadaTramo() {
        List<VueloDto> vuelos = vueloService.buscar("BOG", "MDE", ida, null, 1);

        assertTrue(vuelos.get(0).precio().compareTo(vuelos.get(1).precio()) <= 0);
        assertTrue(vuelos.get(1).precio().compareTo(vuelos.get(2).precio()) <= 0);
    }

    @Test
    void laMismaBusquedaDevuelveLosMismosVuelos() {
        assertEquals(
                vueloService.buscar("BOG", "CTG", ida, null, 1),
                vueloService.buscar("BOG", "CTG", ida, null, 1));
    }

    @Test
    void elPrecioSeMultiplicaPorLosPasajeros() {
        VueloDto uno = vueloService.buscar("BOG", "CTG", ida, null, 1).get(0);
        List<VueloDto> dos = vueloService.buscar("BOG", "CTG", ida, null, 2);

        assertTrue(dos.stream().anyMatch(v -> v.precio().compareTo(uno.precio().multiply(java.math.BigDecimal.valueOf(2))) == 0));
    }

    @Test
    void rechazaOrigenVacio() {
        assertThrows(DatosInvalidosException.class, () -> vueloService.buscar(" ", "CTG", ida, null, 1));
        assertThrows(DatosInvalidosException.class, () -> vueloService.buscar(null, "CTG", ida, null, 1));
    }

    @Test
    void rechazaCodigosQueNoSonDeTresLetras() {
        assertThrows(DatosInvalidosException.class, () -> vueloService.buscar("Bogota", "CTG", ida, null, 1));
        assertThrows(DatosInvalidosException.class, () -> vueloService.buscar("B0G", "CTG", ida, null, 1));
    }

    @Test
    void rechazaOrigenIgualAlDestino() {
        assertThrows(DatosInvalidosException.class, () -> vueloService.buscar("BOG", "bog", ida, null, 1));
    }

    @Test
    void rechazaFechasInvalidas() {
        assertThrows(DatosInvalidosException.class, () -> vueloService.buscar("BOG", "CTG", null, null, 1));
        assertThrows(DatosInvalidosException.class,
                () -> vueloService.buscar("BOG", "CTG", LocalDate.now().minusDays(1), null, 1));
        assertThrows(DatosInvalidosException.class,
                () -> vueloService.buscar("BOG", "CTG", ida, ida.minusDays(1), 1));
    }

    @Test
    void rechazaPasajerosFueraDeRango() {
        assertThrows(DatosInvalidosException.class, () -> vueloService.buscar("BOG", "CTG", ida, null, 0));
        assertThrows(DatosInvalidosException.class, () -> vueloService.buscar("BOG", "CTG", ida, null, 10));
    }
}
