package com.project.back_end_proyecto.service.packagepersistence;

import java.time.LocalDate;

/** Authorization boundary so package persistence can be tested against an isolated database. */
public interface PaqueteTripAccess {
    TripContext requireRead(long userId, long tripId);
    TripContext requireEdit(long userId, long tripId);

    record TripContext(LocalDate fechaInicio, LocalDate fechaFin) { }
}
