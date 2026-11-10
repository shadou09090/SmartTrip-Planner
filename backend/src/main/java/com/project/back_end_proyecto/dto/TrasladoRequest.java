package com.project.back_end_proyecto.dto;

public class TrasladoRequest {
    private String origen;
    private Double origenLat;
    private Double origenLng;
    private String destino;
    private Double destinoLat;
    private Double destinoLng;

    public TrasladoRequest() {}

    public TrasladoRequest(String origen, Double origenLat, Double origenLng,
                           String destino, Double destinoLat, Double destinoLng) {
        this.origen = origen;
        this.origenLat = origenLat;
        this.origenLng = origenLng;
        this.destino = destino;
        this.destinoLat = destinoLat;
        this.destinoLng = destinoLng;
    }

    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }

    public Double getOrigenLat() { return origenLat; }
    public void setOrigenLat(Double origenLat) { this.origenLat = origenLat; }

    public Double getOrigenLng() { return origenLng; }
    public void setOrigenLng(Double origenLng) { this.origenLng = origenLng; }

    public String getDestino() { return destino; }
    public void setDestino(String destino) { this.destino = destino; }

    public Double getDestinoLat() { return destinoLat; }
    public void setDestinoLat(Double destinoLat) { this.destinoLat = destinoLat; }

    public Double getDestinoLng() { return destinoLng; }
    public void setDestinoLng(Double destinoLng) { this.destinoLng = destinoLng; }
}
