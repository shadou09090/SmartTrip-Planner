package com.project.back_end_proyecto.repository;

import com.project.back_end_proyecto.entity.Actividad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ActividadRepository extends JpaRepository<Actividad, Long> {
    List<Actividad> findAllByOrderByNombreAsc();

    @Query("""
            select a from Actividad a
            where (a.fechaDisponibleInicio is null or a.fechaDisponibleInicio <= :fechaFinViaje)
              and (a.fechaDisponibleFin is null or a.fechaDisponibleFin >= :fechaInicioViaje)
            order by a.nombre asc
            """)
    List<Actividad> findAllDisponiblesEnRango(@Param("fechaInicioViaje") java.time.LocalDate fechaInicioViaje,
                                               @Param("fechaFinViaje") java.time.LocalDate fechaFinViaje);
}
