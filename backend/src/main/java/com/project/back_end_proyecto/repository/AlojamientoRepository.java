package com.project.back_end_proyecto.repository;
import com.project.back_end_proyecto.entity.Alojamiento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface AlojamientoRepository extends JpaRepository<Alojamiento, Long> {
    Optional<Alojamiento> findByItinerario_Id(Long itinerarioId);
    void deleteByItinerario_Id(Long itinerarioId);
}
