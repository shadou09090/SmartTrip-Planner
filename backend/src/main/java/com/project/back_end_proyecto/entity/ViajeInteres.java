package com.project.back_end_proyecto.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "VIAJE_INTERES")
public class ViajeInteres {
    @EmbeddedId
    private ViajeInteresId id;

    @MapsId("viajeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_VIAJE", nullable = false, foreignKey = @ForeignKey(name = "FK_VI_VIAJE"))
    private Viaje viaje;

    @MapsId("interesId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_INTERES", nullable = false, foreignKey = @ForeignKey(name = "FK_VI_INTERES"))
    private Interes interes;

    protected ViajeInteres() { }

    public ViajeInteres(Viaje viaje, Interes interes) {
        this.viaje = viaje;
        this.interes = interes;
        this.id = new ViajeInteresId(viaje.getId(), interes.getId());
    }

    public Interes getInteres() { return interes; }
}
