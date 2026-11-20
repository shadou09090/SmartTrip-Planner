package com.project.back_end_proyecto.entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
@Entity @Table(name = "ACTIVIDAD_INTERES")
public class ActividadInteres {
    @EmbeddedId private ActividadInteresId id;
    @MapsId("actividadId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_ACTIVIDAD", nullable = false, foreignKey = @ForeignKey(name = "FK_AI_ACTIVIDAD"))
    private Actividad actividad;
    @MapsId("interesId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_INTERES", nullable = false, foreignKey = @ForeignKey(name = "FK_AI_INTERES"))
    private Interes interes;
    protected ActividadInteres() { }
    public ActividadInteres(Actividad actividad, Interes interes) { this.actividad = actividad; this.interes = interes; this.id = new ActividadInteresId(actividad.getId(), interes.getId()); }
    public Actividad getActividad() { return actividad; }
    public Interes getInteres() { return interes; }
}
