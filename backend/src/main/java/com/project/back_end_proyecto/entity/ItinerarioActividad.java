package com.project.back_end_proyecto.entity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
@Entity @Table(name = "ITINERARIO_ACTIVIDAD", uniqueConstraints = @UniqueConstraint(name = "UK_IA_ORDEN", columnNames = {"ID_DIA", "ORDEN"}))
public class ItinerarioActividad {
    @EmbeddedId private ItinerarioActividadId id;
    @MapsId("diaId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_DIA", nullable = false, foreignKey = @ForeignKey(name = "FK_IA_DIA"))
    private DiaItinerario dia;
    @MapsId("actividadId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_ACTIVIDAD", nullable = false, foreignKey = @ForeignKey(name = "FK_IA_ACTIVIDAD"))
    private Actividad actividad;
    @Column(name = "ORDEN", nullable = false)
    private Integer orden;
    protected ItinerarioActividad() { }
    public ItinerarioActividad(DiaItinerario dia, Actividad actividad, Integer orden) { this.dia = dia; this.actividad = actividad; this.orden = orden; this.id = new ItinerarioActividadId(dia.getId(), actividad.getId()); }
    public Integer getOrden() { return orden; }
    public DiaItinerario getDia() { return dia; }
    public Actividad getActividad() { return actividad; }
    public void cambiarOrden(Integer orden) { this.orden = orden; }
}
