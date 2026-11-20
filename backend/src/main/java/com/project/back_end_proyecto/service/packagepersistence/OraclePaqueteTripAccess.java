package com.project.back_end_proyecto.service.packagepersistence;

import com.project.back_end_proyecto.entity.Viaje;
import com.project.back_end_proyecto.service.TripPermissionService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!local-no-db")
public class OraclePaqueteTripAccess implements PaqueteTripAccess {
    private final TripPermissionService permissions;

    public OraclePaqueteTripAccess(TripPermissionService permissions) {
        this.permissions = permissions;
    }

    @Override public TripContext requireRead(long userId, long tripId) {
        return context(permissions.requireRead(userId, tripId));
    }

    @Override public TripContext requireEdit(long userId, long tripId) {
        return context(permissions.requireEdit(userId, tripId));
    }

    private static TripContext context(Viaje trip) {
        return new TripContext(trip.getFechaInicio(), trip.getFechaFin());
    }
}
