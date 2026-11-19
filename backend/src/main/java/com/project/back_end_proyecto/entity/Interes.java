package com.project.back_end_proyecto.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "INTERES", uniqueConstraints = @UniqueConstraint(name = "UK_INTERES_NOMBRE", columnNames = "NOMBRE"))
public class Interes {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_INTERES", nullable = false)
    private Long id;

    @Column(name = "NOMBRE", nullable = false, length = 100)
    private String nombre;

    protected Interes() { }

    public Interes(String nombre) { this.nombre = nombre; }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
}
