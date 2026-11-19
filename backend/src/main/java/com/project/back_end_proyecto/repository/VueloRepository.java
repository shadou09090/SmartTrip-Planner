package com.project.back_end_proyecto.repository;
import com.project.back_end_proyecto.entity.Vuelo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface VueloRepository extends JpaRepository<Vuelo, Long> {
    List<Vuelo> findAllByViaje_IdOrderByFechaSalida(Long viajeId);
    Optional<Vuelo> findByIdAndViaje_Id(Long vueloId, Long viajeId);
    void deleteAllByViaje_Id(Long viajeId);
}
