package com.project.back_end_proyecto.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "ALOJAMIENTO", uniqueConstraints = @UniqueConstraint(name = "UK_ALOJAMIENTO_ITINERARIO", columnNames = "ID_ITINERARIO"))
public class Alojamiento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_ALOJAMIENTO", nullable = false)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_ITINERARIO", nullable = false, unique = true, foreignKey = @ForeignKey(name = "FK_ALOJAMIENTO_ITINERARIO"))
    private Itinerario itinerario;
    @Column(name = "NOMBRE", nullable = false, length = 200) private String nombre;
    @Column(name = "DIRECCION", length = 300) private String direccion;
    @Column(name = "LATITUD", precision = 10, scale = 7) private BigDecimal latitud;
    @Column(name = "LONGITUD", precision = 10, scale = 7) private BigDecimal longitud;
    protected Alojamiento() { }
    public Alojamiento(Itinerario itinerario, String nombre, String direccion, BigDecimal latitud, BigDecimal longitud) {
        this.itinerario = itinerario; this.nombre = nombre; this.direccion = direccion; this.latitud = latitud; this.longitud = longitud;
    }
    public void actualizar(String nombre, String direccion, BigDecimal latitud, BigDecimal longitud) {
        this.nombre = nombre; this.direccion = direccion; this.latitud = latitud; this.longitud = longitud;
    }
    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDireccion() { return direccion; }
    public BigDecimal getLatitud() { return latitud; }
    public BigDecimal getLongitud() { return longitud; }
}
