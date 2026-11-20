package com.project.back_end_proyecto.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
@Embeddable
public class ColaboradorViajeId implements Serializable {
    @Column(name = "ID_VIAJE") private Long viajeId;
    @Column(name = "ID_USUARIO") private Long usuarioId;
    protected ColaboradorViajeId() { }
    public ColaboradorViajeId(Long viajeId, Long usuarioId) { this.viajeId = viajeId; this.usuarioId = usuarioId; }
    @Override public boolean equals(Object other) { return this == other || other instanceof ColaboradorViajeId that && Objects.equals(viajeId, that.viajeId) && Objects.equals(usuarioId, that.usuarioId); }
    @Override public int hashCode() { return Objects.hash(viajeId, usuarioId); }
}
