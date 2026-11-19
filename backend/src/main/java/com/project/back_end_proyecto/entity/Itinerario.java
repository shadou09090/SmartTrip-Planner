package com.project.back_end_proyecto.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "ITINERARIO",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_ITINERARIO_VIAJE",
                columnNames = "ID_VIAJE"
        )
)
public class Itinerario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_ITINERARIO", nullable = false)
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "ID_VIAJE",
            nullable = false,
            foreignKey = @ForeignKey(name = "FK_ITINERARIO_VIAJE")
    )
    private Viaje viaje;

    @NotNull
    @Column(name = "FECHA_CREACION", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    protected Itinerario() {
    }

    public Itinerario(Viaje viaje) {
        this.viaje = Objects.requireNonNull(viaje, "viaje es obligatorio");
        this.fechaCreacion = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Viaje getViaje() {
        return viaje;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
