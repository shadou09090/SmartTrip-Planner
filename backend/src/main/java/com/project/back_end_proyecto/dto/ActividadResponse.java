package com.project.back_end_proyecto.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ActividadResponse(Long id, String nombre, String descripcion, String ubicacion,
                                BigDecimal latitud, BigDecimal longitud, BigDecimal costo,
                                LocalDate fechaDisponibleInicio, LocalDate fechaDisponibleFin,
                                Integer orden, List<String> intereses) { }
