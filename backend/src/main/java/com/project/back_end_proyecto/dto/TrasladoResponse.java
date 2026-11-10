package com.project.back_end_proyecto.dto;

public class TrasladoResponse {
    private String origen;
    private String destino;
    private double distanciaKm;
    private int tiempoPieMinutos;
    private String tiempoPieFormato;
    private boolean pieSupera120;
    private int tiempoTransporteMinutos;
    private String tiempoTransporteFormato;
    private boolean transporteSupera120;
    private int tiempoAutoMinutos;
    private String tiempoAutoFormato;
    private boolean autoSupera120;
    private boolean advertenciaVisualSupera120;
    private boolean desdeCache;

    public TrasladoResponse() {}

    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }

    public String getDestino() { return destino; }
    public void setDestino(String destino) { this.destino = destino; }

    public double getDistanciaKm() { return distanciaKm; }
    public void setDistanciaKm(double distanciaKm) { this.distanciaKm = distanciaKm; }

    public int getTiempoPieMinutos() { return tiempoPieMinutos; }
    public void setTiempoPieMinutos(int tiempoPieMinutos) { this.tiempoPieMinutos = tiempoPieMinutos; }

    public String getTiempoPieFormato() { return tiempoPieFormato; }
    public void setTiempoPieFormato(String tiempoPieFormato) { this.tiempoPieFormato = tiempoPieFormato; }

    public boolean isPieSupera120() { return pieSupera120; }
    public void setPieSupera120(boolean pieSupera120) { this.pieSupera120 = pieSupera120; }

    public int getTiempoTransporteMinutos() { return tiempoTransporteMinutos; }
    public void setTiempoTransporteMinutos(int tiempoTransporteMinutos) { this.tiempoTransporteMinutos = tiempoTransporteMinutos; }

    public String getTiempoTransporteFormato() { return tiempoTransporteFormato; }
    public void setTiempoTransporteFormato(String tiempoTransporteFormato) { this.tiempoTransporteFormato = tiempoTransporteFormato; }

    public boolean isTransporteSupera120() { return transporteSupera120; }
    public void setTransporteSupera120(boolean transporteSupera120) { this.transporteSupera120 = transporteSupera120; }

    public int getTiempoAutoMinutos() { return tiempoAutoMinutos; }
    public void setTiempoAutoMinutos(int tiempoAutoMinutos) { this.tiempoAutoMinutos = tiempoAutoMinutos; }

    public String getTiempoAutoFormato() { return tiempoAutoFormato; }
    public void setTiempoAutoFormato(String tiempoAutoFormato) { this.tiempoAutoFormato = tiempoAutoFormato; }

    public boolean isAutoSupera120() { return autoSupera120; }
    public void setAutoSupera120(boolean autoSupera120) { this.autoSupera120 = autoSupera120; }

    public boolean isAdvertenciaVisualSupera120() { return advertenciaVisualSupera120; }
    public void setAdvertenciaVisualSupera120(boolean advertenciaVisualSupera120) { this.advertenciaVisualSupera120 = advertenciaVisualSupera120; }

    public boolean isDesdeCache() { return desdeCache; }
    public void setDesdeCache(boolean desdeCache) { this.desdeCache = desdeCache; }
}
