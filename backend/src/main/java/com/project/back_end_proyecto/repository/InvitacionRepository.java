package com.project.back_end_proyecto.repository;
import com.project.back_end_proyecto.entity.EstadoInvitacion;
import com.project.back_end_proyecto.entity.Invitacion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface InvitacionRepository extends JpaRepository<Invitacion, Long> {
    Optional<Invitacion> findByToken(String token);
    boolean existsByToken(String token);
    List<Invitacion> findAllByViaje_IdOrderByIdDesc(Long viajeId);
    List<Invitacion> findAllByCorreoInvitadoIgnoreCaseAndEstado(String correo, EstadoInvitacion estado);
    void deleteAllByViaje_Id(Long viajeId);
}
