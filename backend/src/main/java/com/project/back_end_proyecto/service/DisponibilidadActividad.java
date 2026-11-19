package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.entity.Actividad;

import java.time.LocalDate;

final class DisponibilidadActividad {
    private DisponibilidadActividad() { }

    static boolean cruzaRango(Actividad actividad, LocalDate fechaInicioViaje, LocalDate fechaFinViaje) {
        return (actividad.getFechaDisponibleInicio() == null
                || !actividad.getFechaDisponibleInicio().isAfter(fechaFinViaje))
                && (actividad.getFechaDisponibleFin() == null
                || !actividad.getFechaDisponibleFin().isBefore(fechaInicioViaje));
    }

    static boolean disponibleEnFecha(Actividad actividad, LocalDate fecha) {
        return (actividad.getFechaDisponibleInicio() == null
                || !fecha.isBefore(actividad.getFechaDisponibleInicio()))
                && (actividad.getFechaDisponibleFin() == null
                || !fecha.isAfter(actividad.getFechaDisponibleFin()));
    }
}
