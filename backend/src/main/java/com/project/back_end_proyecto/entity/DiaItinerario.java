package com.project.back_end_proyecto.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "dias_itinerario")
public class DiaItinerario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate fecha;

    @ElementCollection
    @CollectionTable(name = "actividades_dia", joinColumns = @JoinColumn(name = "dia_id"))
    @Column(name = "actividad")
    private List<String> actividades = new ArrayList<>();

    public DiaItinerario() {}

    public DiaItinerario(LocalDate fecha) {
        this.fecha = fecha;
    }

    public Long getId() { return id; }
    public LocalDate getFecha() { return fecha; }
    public List<String> getActividades() { return actividades; }
    public void agregarActividad(String actividad) { this.actividades.add(actividad); }
}