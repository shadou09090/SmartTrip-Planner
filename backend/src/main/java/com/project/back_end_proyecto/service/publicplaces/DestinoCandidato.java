package com.project.back_end_proyecto.service.publicplaces;

import java.math.BigDecimal;

/** A geocoder result a traveler can choose when a destination name is ambiguous. */
public record DestinoCandidato(String id, String nombreCompleto, String ciudad, String region,
                               String pais, String tipo, BigDecimal latitud, BigDecimal longitud,
                               String proveedor) { }
