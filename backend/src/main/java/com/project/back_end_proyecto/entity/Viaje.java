package com.project.back_end_proyecto.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "VIAJE")
public class Viaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_VIAJE", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "ID_USUARIO",
            nullable = false,
            foreignKey = @ForeignKey(name = "FK_VIAJE_USUARIO")
    )
    private User usuario;

    @NotBlank
    @Size(max = 150)
    @Column(name = "ORIGEN", nullable = false, length = 150)
    private String origen;

    @NotBlank
    @Size(max = 150)
    @Column(name = "DESTINO", nullable = false, length = 150)
    private String destino;

    @NotNull
    @Column(name = "FECHA_INICIO", nullable = false)
    private LocalDate fechaInicio;

    @NotNull
    @Column(name = "FECHA_FIN", nullable = false)
    private LocalDate fechaFin;

    @DecimalMin(value = "0.00", inclusive = true)
    @Digits(integer = 10, fraction = 2)
    @Column(name = "PRESUPUESTO", precision = 12, scale = 2)
    private BigDecimal presupuesto;

    protected Viaje() {
    }

    public Viaje(
            User usuario,
            String origen,
            String destino,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            BigDecimal presupuesto
    ) {
        this.usuario = usuario;
        this.origen = origen;
        this.destino = destino;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.presupuesto = presupuesto;
    }

    @AssertTrue(message = "La fecha de fin debe ser igual o posterior a la fecha de inicio")
    @JsonIgnore
    public boolean isRangoFechasValido() {
        return fechaInicio == null || fechaFin == null || !fechaFin.isBefore(fechaInicio);
    }

    public Long getId() {
        return id;
    }

    @JsonIgnore
    public User getUsuario() {
        return usuario;
    }

    public String getOrigen() {
        return origen;
    }

    public String getDestino() {
        return destino;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public BigDecimal getPresupuesto() {
        return presupuesto;
    }

    public void actualizar(String origen, String destino, LocalDate fechaInicio, LocalDate fechaFin, BigDecimal presupuesto) {
        this.origen = origen;
        this.destino = destino;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.presupuesto = presupuesto;
    }
}
