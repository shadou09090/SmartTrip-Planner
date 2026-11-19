package com.project.back_end_proyecto.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(
        name = "DIA_ITINERARIO",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_DIA_ITINERARIO",
                columnNames = {"ID_ITINERARIO", "FECHA"}
        )
)
public class DiaItinerario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_DIA", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "ID_ITINERARIO",
            nullable = false,
            foreignKey = @ForeignKey(name = "FK_DIA_ITINERARIO")
    )
    private Itinerario itinerario;

    @NotNull
    @Column(name = "FECHA", nullable = false)
    private LocalDate fecha;

    protected DiaItinerario() {
    }

    public DiaItinerario(Itinerario itinerario, LocalDate fecha) {
        this.itinerario = Objects.requireNonNull(itinerario, "itinerario es obligatorio");
        this.fecha = Objects.requireNonNull(fecha, "fecha es obligatoria");
    }

    public Long getId() {
        return id;
    }

    public Itinerario getItinerario() {
        return itinerario;
    }

    public LocalDate getFecha() {
        return fecha;
    }
}
