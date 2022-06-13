package com.lucho.keeprunning;

import android.content.Context;
import android.location.Location;
import android.speech.tts.TextToSpeech;

import androidx.appcompat.app.AppCompatActivity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class KeepRunning extends AppCompatActivity {
    private static final double VEL_PROM = 2.84561118987206; // ajustado de acuerdo a carrera 20210531
    private static final double DIST_PROM = 3.49850390902629; // ajustado de acuerdo a carrera 20210531
    private static final double DESVIO_DELTA = 1.3; // OK de acuerdo a la carrera del 20210531
    private static final double LIMIT_VEL_MAX = VEL_PROM + DESVIO_DELTA;
    private static final double LIMIT_VEL_MIN = VEL_PROM - DESVIO_DELTA;
    private static final double LIMIT_DIST_MAX = DIST_PROM + DESVIO_DELTA;
    private static final double LIMIT_DIST_MIN = DIST_PROM - DESVIO_DELTA;
    private static final int LIMIT_MIN_SAT = 3;
    private static final int LIMIT_DIST_PAR = 1000;
    private static final double R_TIERRA = 6371.0;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
    private final GPSLocationService gpsService;
    private TextToSpeech tts;
    private Date fechaHoraComienzo;
    private Date fechaHoraFin;
    private Date horaAnterior;
    private String coordMap;
    private double distanciaTotal = 0.0;
    private Location locInicio;
    private Location locActual;
    private Location locAnterior;
    private static final int LIMIT_DIST_MAP = 50;
    private int kmsParciales;
    private Boolean isFirstLocation;
    private Boolean isRunning=false;

    public KeepRunning(GPSLocationService gpsService, Context applicationContext) {
        this.gpsService = gpsService;
        tts = new TextToSpeech(applicationContext, status -> tts.setLanguage(new Locale("es", "LA")));
        isFirstLocation = true;
        Timer timer = new Timer();
        timer.schedule(timerTask, 0, 1000);
    }

    public void iniciar_carrera(){
        this.fechaHoraComienzo = Calendar.getInstance().getTime();
        textoAvoz("Running iniciado");
        gpsService.actualizarNotification("Running iniciado.", "Distancia parcial: " + round(getDistanciaTotalKM(), 2) + " km");
        isRunning=true;
    }

    public void finalizar_carrera() {
        fechaHoraFin = Calendar.getInstance().getTime();
        textoAvoz("Running finalizado");
        gpsService.actualizarNotification("Running finalizado.", "Distancia Total: " + round(getDistanciaTotalKM(), 2) + " km");
        isFirstLocation = true;
        isRunning=false;
    }

    public String getFechaComienzo() {
        return dateFormat.format(fechaHoraComienzo);
    }

    public String getHoraComienzo() {
        return timeFormat.format(fechaHoraComienzo);
    }

    public String getHoraFin() {
        return timeFormat.format(fechaHoraFin);
    }

    public String getCoorMaps(){ return coordMap; }

    public double getDistanciaTotalKM(){ return distanciaTotal / 1000; }

    public String getTiempoTotal() {
        long millse = fechaHoraFin.getTime() - fechaHoraComienzo.getTime();
        long mills = Math.abs(millse);
        int hours = (int) (mills / (1000 * 60 * 60));
        int mins = (int) (mills / (1000 * 60)) % 60;
        long secs = (int) (mills / 1000) % 60;
        return hours + ":" + mins + ":" + secs;
    }

    public String getPuntoInicio() {
        if (locInicio != null) {
            return locInicio.getLatitude() + "," + locInicio.getLongitude();
        } else {
            return "";
        }
    }

    public String getPuntoFin() {
        if (locActual != null) {
            return locActual.getLatitude() + "," + locActual.getLongitude();
        } else {
            return "";
        }
    }

    public String getCaloriesBurned() {
        long millse = fechaHoraFin.getTime() - fechaHoraComienzo.getTime();
        long mills = Math.abs(millse);
        int mins = (int) (mills / (1000 * 60));
        double calBurn = ((43 * 0.2017) + (70 * 2.20462 * 0.09036) + (150 * 0.6309) - 55.0969) * (mins / 4.184);
        return round(calBurn, 2) + " cal.";
    }

    double distance_between() {
        double lat1 = locActual.getLatitude();
        double lon1 = locActual.getLongitude();
        double lat2 = locAnterior.getLatitude();
        double lon2 = locAnterior.getLongitude();
        double dLat = (lat2 - lat1) * Math.PI / 180.0;
        double dLon = (lon2 - lon1) * Math.PI / 180.0;
        lat1 = lat1 * Math.PI / 180.0;
        lat2 = lat2 * Math.PI / 180.0;
        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0) +
                Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (R_TIERRA * c * 1000.0);
    }

    private void tiempoPorKM(int km) {
        Date horaActual = Calendar.getInstance().getTime();
        long difTime = horaActual.getTime() - horaAnterior.getTime();
        long mills = Math.abs(difTime);
        int mins = (int) (mills / (1000 * 60)) % 60;
        long secs = (int) (mills / 1000) % 60;
        textoAvoz(km + " kilometros, a " + mins + ", " + secs);
        horaAnterior = horaActual;
    }

    public static BigDecimal round(double d, int decimalPlace) {
        return new BigDecimal(String.valueOf(d)).setScale(decimalPlace, RoundingMode.HALF_UP);
    }

    public String getVelProm() {
        long millse = fechaHoraFin.getTime() - fechaHoraComienzo.getTime();
        long mills = Math.abs(millse);
        double velProm = mills / getDistanciaTotalKM();
        return (int) (velProm / (1000 * 60)) % 60 + "'" + (int) (velProm / 1000) % 60 + "'' min/km";
    }

    TimerTask timerTask = new TimerTask() {
        @Override
        public void run() {
            runOnUiThread(() -> {
                locActual = gpsService.getCurrentLocation();
                if(isRunning) {
                    if (isFirstLocation) {
                        locInicio = locActual;
                        locAnterior = locActual;
                        kmsParciales = 0;
                        fechaHoraComienzo = Calendar.getInstance().getTime();
                        horaAnterior = Calendar.getInstance().getTime();
                        isFirstLocation = false;
                        String coord = locInicio.getLatitude() + "," + locInicio.getLongitude();
                        coordMap = "|" + coord;
                    } else {
                        double distanciaEntreLoc;
                        double distanciaParcial = 0.0;
                        double distanciaParcialMap = 0.0;
                        distanciaEntreLoc = distance_between();
                        if (distanciaEntreLoc < LIMIT_DIST_MAX &&
                                distanciaEntreLoc > LIMIT_DIST_MIN &&
                                locActual.getExtras().getInt("satellites") > LIMIT_MIN_SAT &&
                                locActual.hasSpeed() &&
                                locActual.getSpeed() > LIMIT_VEL_MIN &&
                                locActual.getSpeed() < LIMIT_VEL_MAX) {
                            distanciaTotal += distanciaEntreLoc;
                            distanciaParcial += distanciaEntreLoc;
                            distanciaParcialMap += distanciaEntreLoc;
                            if (distanciaParcial > LIMIT_DIST_PAR) {
                                kmsParciales++;
                                tiempoPorKM(kmsParciales);
                            }
                            if (distanciaParcialMap > LIMIT_DIST_MAP) {
                                String coord = locActual.getLatitude() + "," + locActual.getLongitude();
                                coordMap = coordMap + "|" + coord;
                                gpsService.actualizarNotification("Running iniciado.", "Distancia parcial: " + round(getDistanciaTotalKM(), 2) + " km");
                            }
                        }
                        locAnterior = locActual;
                    }
                }
            });
        }
    };

    private void textoAvoz(String msj) {
        tts.speak(msj, TextToSpeech.QUEUE_FLUSH, null);
    }
}
