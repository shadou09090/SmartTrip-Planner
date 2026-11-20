package com.project.back_end_proyecto.entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
@Entity @Table(name = "COLABORADOR_VIAJE")
public class ColaboradorViaje {
    @EmbeddedId private ColaboradorViajeId id;
    @MapsId("viajeId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_VIAJE", nullable = false, foreignKey = @ForeignKey(name = "FK_CV_VIAJE")) private Viaje viaje;
    @MapsId("usuarioId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_USUARIO", nullable = false, foreignKey = @ForeignKey(name = "FK_CV_USUARIO")) private User usuario;
    @Enumerated(EnumType.STRING) @Column(name = "ROL", nullable = false, length = 20) private RolColaborador rol;
    protected ColaboradorViaje() { }
    public ColaboradorViaje(Viaje viaje, User usuario, RolColaborador rol) {
        this.viaje = viaje; this.usuario = usuario; this.rol = rol;
        this.id = new ColaboradorViajeId(viaje.getId(), usuario.getId());
    }
    public Viaje getViaje() { return viaje; }
    public User getUsuario() { return usuario; }
    public RolColaborador getRol() { return rol; }
}
