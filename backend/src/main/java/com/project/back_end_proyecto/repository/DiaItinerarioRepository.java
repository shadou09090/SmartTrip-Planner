package com.project.back_end_proyecto.repository;

import com.project.back_end_proyecto.entity.DiaItinerario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DiaItinerarioRepository extends JpaRepository<DiaItinerario, Long> {
    List<DiaItinerario> findAllByItinerario_IdOrderByFecha(Long itinerarioId);
    Optional<DiaItinerario> findByIdAndItinerario_Viaje_IdAndItinerario_Viaje_Usuario_Id(Long diaId, Long viajeId, Long usuarioId);
    Optional<DiaItinerario> findByIdAndItinerario_Viaje_Id(Long diaId, Long viajeId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DiaItinerario d where d.id = :dayId and d.itinerario.viaje.id = :tripId")
    Optional<DiaItinerario> findForUpdateByIdAndTripId(@Param("dayId") Long dayId, @Param("tripId") Long tripId);
    List<DiaItinerario> findAllByItinerario_Id(Long itinerarioId);
    void deleteAllByItinerario_Id(Long itinerarioId);
}
