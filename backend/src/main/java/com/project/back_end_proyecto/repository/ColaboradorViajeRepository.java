package com.project.back_end_proyecto.repository;
import com.project.back_end_proyecto.entity.ColaboradorViaje;
import com.project.back_end_proyecto.entity.ColaboradorViajeId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface ColaboradorViajeRepository extends JpaRepository<ColaboradorViaje, ColaboradorViajeId> {
    Optional<ColaboradorViaje> findById_ViajeIdAndId_UsuarioId(Long viajeId, Long usuarioId);
    boolean existsById_ViajeIdAndId_UsuarioId(Long viajeId, Long usuarioId);
    List<ColaboradorViaje> findAllByUsuario_Id(Long usuarioId);
    List<ColaboradorViaje> findAllByViaje_Id(Long viajeId);
    void deleteById_ViajeIdAndId_UsuarioId(Long viajeId, Long usuarioId);
    void deleteAllByViaje_Id(Long viajeId);
}
