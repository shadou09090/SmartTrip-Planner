package com.project.back_end_proyecto.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "ACTIVIDAD")
public class Actividad {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_ACTIVIDAD", nullable = false)
    private Long id;
    @Column(name = "NOMBRE", nullable = false, length = 150)
    private String nombre;
    @Column(name = "DESCRIPCION", length = 500)
    private String descripcion;
    @Column(name = "UBICACION", length = 250)
    private String ubicacion;
    @Digits(integer = 3, fraction = 7) @Column(name = "LATITUD", precision = 10, scale = 7)
    private BigDecimal latitud;
    @Digits(integer = 3, fraction = 7) @Column(name = "LONGITUD", precision = 10, scale = 7)
    private BigDecimal longitud;
    @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) @Column(name = "COSTO", precision = 10, scale = 2)
    private BigDecimal costo;
    @Column(name = "FECHA_DISPONIBLE_INICIO")
    private LocalDate fechaDisponibleInicio;
    @Column(name = "FECHA_DISPONIBLE_FIN")
    private LocalDate fechaDisponibleFin;
    protected Actividad() { }
    public Actividad(String nombre, String descripcion, String ubicacion, BigDecimal latitud, BigDecimal longitud,
                     BigDecimal costo, LocalDate fechaDisponibleInicio, LocalDate fechaDisponibleFin) {
        this.nombre = nombre; this.descripcion = descripcion; this.ubicacion = ubicacion;
        this.latitud = latitud; this.longitud = longitud; this.costo = costo;
        this.fechaDisponibleInicio = fechaDisponibleInicio; this.fechaDisponibleFin = fechaDisponibleFin;
    }
    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public String getUbicacion() { return ubicacion; }
    public BigDecimal getLatitud() { return latitud; }
    public BigDecimal getLongitud() { return longitud; }
    public BigDecimal getCosto() { return costo; }
    public LocalDate getFechaDisponibleInicio() { return fechaDisponibleInicio; }
    public LocalDate getFechaDisponibleFin() { return fechaDisponibleFin; }
}
