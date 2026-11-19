package com.project.back_end_proyecto.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "VUELO")
public class Vuelo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_VUELO", nullable = false) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_VIAJE", nullable = false, foreignKey = @ForeignKey(name = "FK_VUELO_VIAJE"))
    private Viaje viaje;
    @Column(name = "ORIGEN", nullable = false, length = 150) private String origen;
    @Column(name = "DESTINO", nullable = false, length = 150) private String destino;
    @Column(name = "FECHA_SALIDA", nullable = false) private LocalDateTime fechaSalida;
    @Column(name = "FECHA_LLEGADA") private LocalDateTime fechaLlegada;
    @Column(name = "AEROLINEA", length = 150) private String aerolinea;
    @Column(name = "NUMERO_VUELO", length = 50) private String numeroVuelo;
    @Column(name = "PRECIO", precision = 12, scale = 2) private BigDecimal precio;
    protected Vuelo() { }
    public Vuelo(Viaje viaje, String origen, String destino, LocalDateTime fechaSalida, LocalDateTime fechaLlegada,
                 String aerolinea, String numeroVuelo, BigDecimal precio) {
        this.viaje = viaje; this.origen = origen; this.destino = destino; this.fechaSalida = fechaSalida;
        this.fechaLlegada = fechaLlegada; this.aerolinea = aerolinea; this.numeroVuelo = numeroVuelo; this.precio = precio;
    }
    public void actualizar(String origen, String destino, LocalDateTime fechaSalida, LocalDateTime fechaLlegada,
                           String aerolinea, String numeroVuelo, BigDecimal precio) {
        this.origen = origen; this.destino = destino; this.fechaSalida = fechaSalida; this.fechaLlegada = fechaLlegada;
        this.aerolinea = aerolinea; this.numeroVuelo = numeroVuelo; this.precio = precio;
    }
    public Long getId() { return id; }
    public String getOrigen() { return origen; }
    public String getDestino() { return destino; }
    public LocalDateTime getFechaSalida() { return fechaSalida; }
    public LocalDateTime getFechaLlegada() { return fechaLlegada; }
    public String getAerolinea() { return aerolinea; }
    public String getNumeroVuelo() { return numeroVuelo; }
    public BigDecimal getPrecio() { return precio; }
}
