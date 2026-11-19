package com.project.back_end_proyecto.repository;
import com.project.back_end_proyecto.entity.HistorialPrecioVuelo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface HistorialPrecioVueloRepository extends JpaRepository<HistorialPrecioVuelo, Long> {
    List<HistorialPrecioVuelo> findAllByVuelo_IdOrderByFechaRegistro(Long vueloId);
    void deleteAllByVuelo_Viaje_Id(Long viajeId);
}
