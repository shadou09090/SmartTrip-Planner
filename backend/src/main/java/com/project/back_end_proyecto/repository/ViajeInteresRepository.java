package com.project.back_end_proyecto.repository;

import com.project.back_end_proyecto.entity.ViajeInteres;
import com.project.back_end_proyecto.entity.ViajeInteresId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ViajeInteresRepository extends JpaRepository<ViajeInteres, ViajeInteresId> {
    List<ViajeInteres> findAllByViaje_Id(Long viajeId);
    void deleteAllByViaje_Id(Long viajeId);
}
