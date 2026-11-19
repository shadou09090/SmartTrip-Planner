package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.entity.Actividad;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisponibilidadActividadTest {
    private static final LocalDate START = LocalDate.of(2026, 6, 10);
    private static final LocalDate END = LocalDate.of(2026, 6, 14);

    @Test
    void activityAvailableInsideTripRangeIsIncluded() {
        assertTrue(DisponibilidadActividad.cruzaRango(activity(START.plusDays(1), END.minusDays(1)), START, END));
    }

    @Test
    void activityEntirelyOutsideTripRangeIsExcluded() {
        assertFalse(DisponibilidadActividad.cruzaRango(activity(END.plusDays(1), END.plusDays(3)), START, END));
    }

    @Test
    void activityPartiallyOverlappingTripRangeIsIncludedAtInclusiveBoundary() {
        assertTrue(DisponibilidadActividad.cruzaRango(activity(END.minusDays(1), END.plusDays(2)), START, END));
        assertTrue(DisponibilidadActividad.cruzaRango(activity(START.minusDays(2), START), START, END));
    }

    @Test
    void nullAvailabilityBoundsMeanUnrestrictedOnThatSide() {
        assertTrue(DisponibilidadActividad.cruzaRango(activity(null, null), START, END));
        assertTrue(DisponibilidadActividad.cruzaRango(activity(null, START), START, END));
        assertFalse(DisponibilidadActividad.cruzaRango(activity(END.plusDays(1), null), START, END));
    }

    private static Actividad activity(LocalDate availableStart, LocalDate availableEnd) {
        return new Actividad("Activity", null, null, null, null, null, availableStart, availableEnd);
    }
}
