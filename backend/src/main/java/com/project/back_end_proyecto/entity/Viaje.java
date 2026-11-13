package com.project.back_end_proyecto.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "viajes")
public class Viaje {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String destino;

    private LocalDate fechaInicio;
    private LocalDate fechaFin;

    @Column(precision = 15, scale = 2)
    private BigDecimal presupuesto;

    @ElementCollection
    @CollectionTable(name = "viaje_intereses", joinColumns = @JoinColumn(name = "viaje_id"))
    @Column(name = "interes")
    private List<String> intereses = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "viaje_id")
    private List<DiaItinerario> dias = new ArrayList<>();

    public Viaje() {}

    public Viaje(LocalDate fechaInicio, LocalDate fechaFin) {
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
    }

    public Long getId() { return id; }
    public String getDestino() { return destino; }
    public void setDestino(String destino) { this.destino = destino; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }
    public BigDecimal getPresupuesto() { return presupuesto; }
    public void setPresupuesto(BigDecimal presupuesto) { this.presupuesto = presupuesto; }
    public List<String> getIntereses() { return intereses; }
    public void setIntereses(List<String> intereses) { this.intereses = intereses; }
    public List<DiaItinerario> getDias() { return dias; }
    public void setDias(List<DiaItinerario> dias) { this.dias = dias; }
}
