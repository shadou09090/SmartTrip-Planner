package com.project.back_end_proyecto.entity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
@Entity @Table(name = "INVITACION", uniqueConstraints = @UniqueConstraint(name = "UK_INVITACION_TOKEN", columnNames = "TOKEN"))
public class Invitacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_INVITACION", nullable = false) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_VIAJE", nullable = false, foreignKey = @ForeignKey(name = "FK_INVITACION_VIAJE")) private Viaje viaje;
    @Column(name = "CORREO_INVITADO", nullable = false, length = 150) private String correoInvitado;
    @Enumerated(EnumType.STRING) @Column(name = "ROL", nullable = false, length = 20) private RolColaborador rol;
    @Column(name = "TOKEN", nullable = false, length = 255) private String token;
    @Column(name = "FECHA_EXPIRACION", nullable = false) private LocalDateTime fechaExpiracion;
    @Enumerated(EnumType.STRING) @Column(name = "ESTADO", nullable = false, length = 20) private EstadoInvitacion estado;
    protected Invitacion() { }
    public Invitacion(Viaje viaje, String correo, RolColaborador rol, String token, LocalDateTime expira) {
        this.viaje = viaje; this.correoInvitado = correo; this.rol = rol; this.token = token;
        this.fechaExpiracion = expira; this.estado = EstadoInvitacion.PENDIENTE;
    }
    public Long getId() { return id; }
    public Viaje getViaje() { return viaje; }
    public String getCorreoInvitado() { return correoInvitado; }
    public RolColaborador getRol() { return rol; }
    @JsonIgnore public String getToken() { return token; }
    public LocalDateTime getFechaExpiracion() { return fechaExpiracion; }
    public EstadoInvitacion getEstado() { return estado; }
    public void setEstado(EstadoInvitacion estado) { this.estado = estado; }
}
