package com.project.back_end_proyecto.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ViajeInteresId implements Serializable {
    @Column(name = "ID_VIAJE")
    private Long viajeId;

    @Column(name = "ID_INTERES")
    private Long interesId;

    protected ViajeInteresId() { }

    public ViajeInteresId(Long viajeId, Long interesId) {
        this.viajeId = viajeId;
        this.interesId = interesId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ViajeInteresId that)) return false;
        return Objects.equals(viajeId, that.viajeId) && Objects.equals(interesId, that.interesId);
    }

    @Override
    public int hashCode() { return Objects.hash(viajeId, interesId); }
}
