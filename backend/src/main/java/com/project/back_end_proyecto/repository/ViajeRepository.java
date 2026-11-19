package com.project.back_end_proyecto.repository;

import com.project.back_end_proyecto.entity.Viaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ViajeRepository extends JpaRepository<Viaje, Long> {
    List<Viaje> findAllByUsuario_IdOrderByFechaInicioDesc(Long userId);
    Optional<Viaje> findByIdAndUsuario_Id(Long viajeId, Long userId);
    boolean existsByIdAndUsuario_Id(Long viajeId, Long userId);
}
