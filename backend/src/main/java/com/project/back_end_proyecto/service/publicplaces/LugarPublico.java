package com.project.back_end_proyecto.service.publicplaces;

import java.math.BigDecimal;
import java.util.Map;

public record LugarPublico(String id, String nombre, String descripcion, String categoria,
                           String ubicacion, BigDecimal latitud, BigDecimal longitud,
                           String urlFuente, Map<String, String> etiquetas) { }
