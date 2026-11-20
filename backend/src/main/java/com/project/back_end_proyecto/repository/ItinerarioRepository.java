package com.project.back_end_proyecto.repository;

import com.project.back_end_proyecto.entity.Itinerario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ItinerarioRepository extends JpaRepository<Itinerario, Long> {
    Optional<Itinerario> findByViaje_Id(Long viajeId);
    void deleteByViaje_Id(Long viajeId);
}
