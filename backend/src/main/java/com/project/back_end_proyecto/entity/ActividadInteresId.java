package com.project.back_end_proyecto.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
@Embeddable
public class ActividadInteresId implements Serializable {
    @Column(name = "ID_ACTIVIDAD") private Long actividadId;
    @Column(name = "ID_INTERES") private Long interesId;
    protected ActividadInteresId() { }
    public ActividadInteresId(Long actividadId, Long interesId) { this.actividadId = actividadId; this.interesId = interesId; }
    @Override public boolean equals(Object other) { return this == other || other instanceof ActividadInteresId that && Objects.equals(actividadId, that.actividadId) && Objects.equals(interesId, that.interesId); }
    @Override public int hashCode() { return Objects.hash(actividadId, interesId); }
}
