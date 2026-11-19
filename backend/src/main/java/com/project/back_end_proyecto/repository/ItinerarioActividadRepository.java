package com.project.back_end_proyecto.repository;

import com.project.back_end_proyecto.entity.ItinerarioActividad;
import com.project.back_end_proyecto.entity.ItinerarioActividadId;
import com.project.back_end_proyecto.entity.Actividad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ItinerarioActividadRepository extends JpaRepository<ItinerarioActividad, ItinerarioActividadId> {
    List<ItinerarioActividad> findAllByDia_IdOrderByOrden(Long diaId);
    Optional<ItinerarioActividad> findByDia_IdAndActividad_Id(Long diaId, Long actividadId);
    Optional<ItinerarioActividad> findByDia_IdAndOrden(Long diaId, Integer orden);
    void deleteAllByDia_Id(Long diaId);

    @Query("select distinct activity from ItinerarioActividad assignment join assignment.actividad activity " +
            "where assignment.dia.itinerario.viaje.id = :viajeId")
    List<Actividad> findDistinctActivitiesByTripId(@Param("viajeId") Long viajeId);

    @Query("select sum(actividad.costo) from ItinerarioActividad asignacion " +
            "join asignacion.actividad actividad where asignacion.dia.itinerario.viaje.id = :viajeId")
    BigDecimal sumarCostosPorViaje(@Param("viajeId") Long viajeId);

    @Query("select count(asignacion) from ItinerarioActividad asignacion " +
            "where asignacion.dia.itinerario.viaje.id = :viajeId and asignacion.actividad.costo is null")
    long contarActividadesSinPrecioPorViaje(@Param("viajeId") Long viajeId);
}
