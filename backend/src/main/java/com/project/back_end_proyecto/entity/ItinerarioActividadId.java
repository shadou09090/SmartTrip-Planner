package com.project.back_end_proyecto.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
@Embeddable
public class ItinerarioActividadId implements Serializable {
    @Column(name = "ID_DIA") private Long diaId;
    @Column(name = "ID_ACTIVIDAD") private Long actividadId;
    protected ItinerarioActividadId() { }
    public ItinerarioActividadId(Long diaId, Long actividadId) { this.diaId = diaId; this.actividadId = actividadId; }
    @Override public boolean equals(Object other) { return this == other || other instanceof ItinerarioActividadId that && Objects.equals(diaId, that.diaId) && Objects.equals(actividadId, that.actividadId); }
    @Override public int hashCode() { return Objects.hash(diaId, actividadId); }
}
