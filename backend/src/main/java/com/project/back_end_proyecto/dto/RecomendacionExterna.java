package com.project.back_end_proyecto.dto;

import java.math.BigDecimal;
import java.util.List;

public record RecomendacionExterna(
        String id,
        String nombre,
        String descripcion,
        String categoria,
        String ubicacion,
        BigDecimal latitud,
        BigDecimal longitud,
        BigDecimal precio,
        String moneda,
        String urlFuente,
        String imagenUrl,
        List<String> interesesCoincidentes,
        String motivo,
        String disponibilidad,
        String imagenAtribucion,
        String imagenLicencia,
        String imagenLicenciaUrl,
        String imagenFuenteUrl,
        String proveedor,
        String alcance,
        BigDecimal distanciaDestinoKm,
        int senalTuristica,
        String categoriaNormalizada,
        String clasificacionTuristica,
        List<String> evidenciasImportancia,
        boolean elegibleItinerario,
        String razonNoElegible
) {
    public RecomendacionExterna(String id, String nombre, String descripcion, String categoria,
                                String ubicacion, BigDecimal latitud, BigDecimal longitud,
                                BigDecimal precio, String moneda, String urlFuente, String imagenUrl,
                                List<String> interesesCoincidentes, String motivo, String disponibilidad) {
        this(id, nombre, descripcion, categoria, ubicacion, latitud, longitud, precio, moneda, urlFuente,
                imagenUrl, interesesCoincidentes, motivo, disponibilidad, null, null, null, null,
                null, null, null, 0, null, "EXPLORABLE", List.of(), false,
                "La respuesta no contiene clasificación suficiente para incluirla automáticamente.");
    }

    public RecomendacionExterna(String id, String nombre, String descripcion, String categoria,
                                String ubicacion, BigDecimal latitud, BigDecimal longitud,
                                BigDecimal precio, String moneda, String urlFuente, String imagenUrl,
                                List<String> interesesCoincidentes, String motivo, String disponibilidad,
                                String imagenAtribucion, String imagenLicencia, String imagenLicenciaUrl,
                                String imagenFuenteUrl) {
        this(id, nombre, descripcion, categoria, ubicacion, latitud, longitud, precio, moneda,
                urlFuente, imagenUrl, interesesCoincidentes, motivo, disponibilidad, imagenAtribucion,
                imagenLicencia, imagenLicenciaUrl, imagenFuenteUrl, null, null, null, 0,
                null, "EXPLORABLE", List.of(), false,
                "La respuesta no contiene clasificación suficiente para incluirla automáticamente.");
    }

    public RecomendacionExterna(String id, String nombre, String descripcion, String categoria,
                                String ubicacion, BigDecimal latitud, BigDecimal longitud,
                                BigDecimal precio, String moneda, String urlFuente, String imagenUrl,
                                List<String> interesesCoincidentes, String motivo, String disponibilidad,
                                String imagenAtribucion, String imagenLicencia, String imagenLicenciaUrl,
                                String imagenFuenteUrl, String proveedor, String alcance,
                                BigDecimal distanciaDestinoKm) {
        this(id, nombre, descripcion, categoria, ubicacion, latitud, longitud, precio, moneda, urlFuente,
                imagenUrl, interesesCoincidentes, motivo, disponibilidad, imagenAtribucion, imagenLicencia,
                imagenLicenciaUrl, imagenFuenteUrl, proveedor, alcance, distanciaDestinoKm, 0,
                null, "EXPLORABLE", List.of(), false,
                "La respuesta no contiene clasificación suficiente para incluirla automáticamente.");
    }

    public RecomendacionExterna {
        interesesCoincidentes = interesesCoincidentes == null ? List.of() : List.copyOf(interesesCoincidentes);
        evidenciasImportancia = evidenciasImportancia == null ? List.of() : List.copyOf(evidenciasImportancia);
    }
}
