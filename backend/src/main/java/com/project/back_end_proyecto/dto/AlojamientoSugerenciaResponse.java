package com.project.back_end_proyecto.dto;

import java.math.BigDecimal;

public record AlojamientoSugerenciaResponse(String id, String nombre, String direccion, String categoria,
                                             BigDecimal latitud, BigDecimal longitud, String urlFuente,
                                             String imagenUrl, String imagenAtribucion,
                                             String imagenLicencia, String imagenLicenciaUrl,
                                             String imagenFuenteUrl, String urlAlojamiento,
                                             String urlBusquedaTarifas) {
    public AlojamientoSugerenciaResponse(String id, String nombre, String direccion, String categoria,
                                         BigDecimal latitud, BigDecimal longitud, String urlFuente,
                                         String imagenUrl, String imagenAtribucion, String imagenLicencia,
                                         String imagenLicenciaUrl, String imagenFuenteUrl, String urlAlojamiento) {
        this(id, nombre, direccion, categoria, latitud, longitud, urlFuente, imagenUrl, imagenAtribucion,
                imagenLicencia, imagenLicenciaUrl, imagenFuenteUrl, urlAlojamiento, null);
    }

    public AlojamientoSugerenciaResponse(String id, String nombre, String direccion, String categoria,
                                         BigDecimal latitud, BigDecimal longitud, String urlFuente,
                                         String imagenUrl) {
        this(id, nombre, direccion, categoria, latitud, longitud, urlFuente, imagenUrl,
                null, null, null, null, null);
    }
}
