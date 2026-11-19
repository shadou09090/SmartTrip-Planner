package com.project.back_end_proyecto.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "HISTORIAL_PRECIO_VUELO")
public class HistorialPrecioVuelo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_HISTORIAL", nullable = false) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_VUELO", nullable = false, foreignKey = @ForeignKey(name = "FK_HISTORIAL_VUELO"))
    private Vuelo vuelo;
    @Column(name = "PRECIO", nullable = false, precision = 12, scale = 2) private BigDecimal precio;
    @Column(name = "FECHA_REGISTRO", nullable = false, updatable = false) private LocalDateTime fechaRegistro;
    protected HistorialPrecioVuelo() { }
    public HistorialPrecioVuelo(Vuelo vuelo, BigDecimal precio) { this.vuelo = vuelo; this.precio = precio; this.fechaRegistro = LocalDateTime.now(); }
    public Long getId() { return id; }
    public BigDecimal getPrecio() { return precio; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
}
