package com.project.back_end_proyecto.repository;

import com.project.back_end_proyecto.entity.ActividadInteres;
import com.project.back_end_proyecto.entity.ActividadInteresId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActividadInteresRepository extends JpaRepository<ActividadInteres, ActividadInteresId> {
    List<ActividadInteres> findAllByActividad_Id(Long actividadId);
    List<ActividadInteres> findAllByActividad_IdIn(List<Long> actividadIds);
}
