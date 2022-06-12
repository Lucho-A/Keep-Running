package com.lucho.keeprunning;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static java.lang.System.exit;

public class GPSLocationService extends Service{
    private static final String LOG_PATH = "/storage/emulated/0/Log/";
    private static final String CHANNEL_ID = "Channel_GPSLocationService";
    private static final int NOTIFICATION_ID = 12345678;
    private static final double VEL_PROM = 2.84561118987206; // ajustado de acuerdo a carrera 20210531
    private static final double DIST_PROM = 3.49850390902629; // ajustado de acuerdo a carrera 20210531
    private static final double DESVIO_DELTA = 1.3; // OK de acuerdo a la carrera del 20210531
    private static final double LIMIT_VEL_MAX = VEL_PROM + DESVIO_DELTA;
    private static final double LIMIT_VEL_MIN = VEL_PROM - DESVIO_DELTA;
    private static final double LIMIT_DIST_MAX = DIST_PROM + DESVIO_DELTA;
    private static final double LIMIT_DIST_MIN = DIST_PROM - DESVIO_DELTA;
    private static final int LIMIT_MIN_SAT = 3;
    private static final int LIMIT_DIST_MAP = 50;
    private static final int LIMIT_DIST_PAR = 1000;
    private static final int LIMIT_INT_UBI_INICIAL = 3;
    private static final double R_TIERRA = 6371.0;
    private static final long LOCATION_INTERVAL = 1000;
    private static final float LOCATION_DISTANCE = (float) 0.1;
    private final SimpleDateFormat timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
    private final LocationServiceBinder binder = new LocationServiceBinder();
    private LocationListener mLocationListener;
    private LocationManager mLocationManager;
    private Context mContext;
    private TextToSpeech tts;
    private Date fechaHoraComienzo;
    private Date fechaHoraFin;
    private Date horaAnterior;
    private Location locInicio;
    private Location locActual;
    private Location locAnterior;
    private int contLocInicio;
    private int kmsParciales;
    private double distanciaTotal;
    private double distanciaParcial;
    private double distanciaParcialMap;
    private Boolean isRunning;
    private String coordMap;
    private String log_name;
    private Notification.Builder builder;
    private NotificationManager notificationManager;

    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void onCreate() {
        mContext=this;
        tts =new TextToSpeech(getApplicationContext(), status -> tts.setLanguage(new Locale("es","LA")));
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        initializeLocationManager();
        startForeground(NOTIFICATION_ID, crearNotification());
        isRunning = false;
        coordMap = "|";
        contLocInicio = 0;
        locInicio = new Location("locInicio");
        locActual = new Location("locActual");
        locAnterior = new Location("locAnterior");
        kmsParciales = 0;
        distanciaTotal = 0;
        distanciaParcial = 0;
        distanciaParcialMap = 0;
        log_name =timeStamp.format(Calendar.getInstance().getTime());
        appendLog("Running comenzado: " + Calendar.getInstance().getTime(),0);
        tts.speak("Running comenzado", TextToSpeech.QUEUE_FLUSH, null);
        appendLog("Fecha/Hora;Distancia(m);Latitud;Longitud;Velocidad (GPS)",0);
        fechaHoraComienzo= Calendar.getInstance().getTime();
        horaAnterior=Calendar.getInstance().getTime();
        startListening();
        return START_NOT_STICKY;
    }

    public void stopService(){
        if (mLocationManager != null) {
            try {
                mLocationManager.removeUpdates(mLocationListener);
            } catch (Exception ex) {
                appendLog(timeStamp.format(Calendar.getInstance().getTime()) + "Error al remover Location Listener - onDestroy()",-1);
            }
        }
        fechaHoraFin=Calendar.getInstance().getTime();
        appendLog("Running finalizado: " + Calendar.getInstance().getTime(),0);
        loguearInfoCarrera();
        isRunning = false;
        actualizarNotification();
        tts.speak("Running finalizado", TextToSpeech.QUEUE_FLUSH, null);
        stopForeground(true);
        onDestroy();
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
    }

    private void initializeLocationManager() {
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
            mLocationManager.getProvider(LocationManager.GPS_PROVIDER);
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            criteria.setPowerRequirement(Criteria.POWER_HIGH);
            criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);
            criteria.setSpeedRequired(true);
            String providerName = mLocationManager.getBestProvider(criteria, true);
            if (providerName == null) {
                appendLog(timeStamp.format(Calendar.getInstance().getTime()) + "No hay proovedores GPS con estos criterios",-1);
                exit(0);
            }
        }
    }

    public void startListening() {
        mLocationListener = new LocationListener();
        try {
            mLocationManager.requestLocationUpdates( LocationManager.GPS_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE, mLocationListener );
            isLocationEnabled();
        } catch (java.lang.SecurityException | IllegalArgumentException ex) {
            appendLog(timeStamp.format(Calendar.getInstance().getTime()) + "Falló el start Listening",-1);
            exit(0);
        }
        isRunning = true;
        actualizarNotification();
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

    public double getDistanciaTotalKM() { return distanciaTotal/1000; }

    public String getTiempoTotal() {
        long millse = fechaHoraFin.getTime() - fechaHoraComienzo.getTime();
        long mills = Math.abs(millse);
        int hours = (int) (mills/(1000 * 60 * 60));
        int mins = (int) (mills/(1000*60)) % 60;
        long secs = (int) (mills / 1000) % 60;
        return hours + ":" + mins + ":" + secs;
    }

    public String getPuntoInicio() { return locInicio.getLatitude() + "," + locInicio.getLongitude(); }
    public String getPuntoFin() { return locActual.getLatitude() + "," + locActual.getLongitude(); }

    public String getVelProm() {
        long millse = fechaHoraFin.getTime() - fechaHoraComienzo.getTime();
        long mills = Math.abs(millse);
        double velProm=mills/getDistanciaTotalKM();
        return (int)(velProm/(1000*60))%60 + "'" + (int)(velProm/1000)%60 + "'' min/km";
    }

    public class LocationServiceBinder extends Binder {
        public GPSLocationService getService() {
            return GPSLocationService.this;
        }
    }

    private void isLocationEnabled() {
        if(!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            appendLog(timeStamp.format(Calendar.getInstance().getTime()) + "No hay GPS",-1);
            exit(0);
        }
        else{
            Toast.makeText(mContext, "GPS activo", Toast.LENGTH_LONG).show();
        }
    }

    public String getCaloriesBurned(){
        long millse = fechaHoraFin.getTime() - fechaHoraComienzo.getTime();
        long mills = Math.abs(millse);
        int mins = (int) (mills/(1000*60));
        double calBurn= ((43 * 0.2017) + (70 * 2.20462 * 0.09036) + (150 * 0.6309) - 55.0969) * (mins / 4.184);
        return round(calBurn,2) + " cal.";
    }

    public void loguearInfoCarrera(){
        long millse = fechaHoraFin.getTime() - fechaHoraComienzo.getTime();
        long mills = Math.abs(millse);
        int hours = (int) (mills/(1000 * 60 * 60));
        int mins = (int) (mills/(1000*60)) % 60;
        long secs = (int) (mills / 1000) % 60;
        appendLog("Hora Inicio: " + timeFormat.format(fechaHoraComienzo),1);
        appendLog("Hora Fin: " + timeFormat.format(fechaHoraFin),1);
        appendLog("Distancia Total: " + round(distanciaTotal/1000,2) + " km",1);
        appendLog("Tiempo Total: " + hours + ":" + mins + ":" + secs,1);
        appendLog("Velocidad promedio: " + getVelProm(),1);
        appendLog("Calorías consumidas: " + getCaloriesBurned() ,1);
        appendLog("Coordenadas de origen: " + locInicio.getLatitude() + "," + locInicio.getLongitude() ,1);
        appendLog("Coordenadas finales: " + locActual.getLatitude() + "," + locActual.getLongitude() ,1);
    }

    double distance_between() {
        double lat1=locActual.getLatitude();
        double lon1=locActual.getLongitude();
        double lat2=locAnterior.getLatitude();
        double lon2=locAnterior.getLongitude();
        double dLat = (lat2-lat1)*Math.PI/180.0;
        double dLon = (lon2-lon1)*Math.PI/180.0;
        lat1 = lat1*Math.PI/180.0;
        lat2 = lat2*Math.PI/180.0;
        double a = Math.sin(dLat/2.0) * Math.sin(dLat/2.0) +
                Math.sin(dLon/2.0) * Math.sin(dLon/2.0) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return (R_TIERRA * c * 1000.0);
    }

    private void tiempoPorKM(int km){
        Date horaActual = Calendar.getInstance().getTime();
        long difTime= horaActual.getTime() - horaAnterior.getTime();
        long mills = Math.abs(difTime);
        int mins = (int) (mills/(1000*60)) % 60;
        long secs = (int) (mills / 1000) % 60;
        textoAvoz(km + " kilometros, a " + mins + ", " + secs);
        horaAnterior = horaActual;
    }

    private void textoAvoz(String msj){
        tts.speak(msj, TextToSpeech.QUEUE_FLUSH, null);
        pausa(5);
    }

    public static BigDecimal round(double d, int decimalPlace) {
        return new BigDecimal(String.valueOf(d)).setScale(decimalPlace, BigDecimal.ROUND_HALF_UP);
    }

    public void appendLog(String text, int tipo) {
        File logFile;
        switch (tipo){
            case -1:
                logFile = new File(LOG_PATH + log_name + "_ERROR.log");
                break;
            case 0:
                logFile = new File(LOG_PATH + log_name + ".log");
                break;
            case 1:
                logFile = new File(LOG_PATH + log_name + "_INFO.log");
                break;
            case 2:
                logFile = new File(LOG_PATH + log_name + "_MAP.log");
                break;
            default:
                logFile = new File(LOG_PATH + log_name + "_DEBUG.log");
                break;
        }
        try {
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(text);
            buf.newLine();
            buf.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Notification crearNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addCategory(Intent. CATEGORY_LAUNCHER ) ;
            intent.setAction(Intent.ACTION_MAIN);
            intent.setFlags(Intent. FLAG_ACTIVITY_CLEAR_TOP | Intent. FLAG_ACTIVITY_SINGLE_TOP );
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
            builder= new Notification.Builder(getApplicationContext(), CHANNEL_ID)
                    .setSmallIcon(R.drawable.descarga)
                    .setContentTitle("Running")
                    .setContentIntent(pendingIntent)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(false);
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Notificación para el Servicio", NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
        return builder.build();
    }

    private void actualizarNotification() {
        if(isRunning) {
            builder.setContentTitle("Running: comenzado...");
            builder.setContentText("Distancia Total: " + round(getDistanciaTotalKM(),2) + " km");
        }else{
            builder.setContentTitle("Running: detenido...");
            builder.setContentText("");
        }
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    public String getCoorMaps() { return coordMap; }

    public void pausa(int sec){
        try {
            TimeUnit.SECONDS.sleep(sec);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private class LocationListener implements android.location.LocationListener {

        public LocationListener() {
        }

        public void onLocationChanged(Location location) {
            locActual = location;
            double distanciaEntreLoc = distance_between();
            if(locInicio.getLatitude()==0) {
                if(contLocInicio==LIMIT_INT_UBI_INICIAL) {
                    locInicio = location;
                    String coord=locInicio.getLatitude() + "," + locInicio.getLongitude();
                    appendLog(coord, 2);
                    coordMap = "|" + coord;
                    textoAvoz("Ubicación actual localizada...");
                }else{
                    contLocInicio++;
                }
            }else {
                if (distanciaEntreLoc < LIMIT_DIST_MAX &&
                        distanciaEntreLoc > LIMIT_DIST_MIN &&
                        locActual.getExtras().getInt("satellites") > LIMIT_MIN_SAT &&
                        locActual.hasSpeed() &&
                        locActual.getSpeed() > LIMIT_VEL_MIN &&
                        locActual.getSpeed() < LIMIT_VEL_MAX) {
                    distanciaTotal += distanciaEntreLoc;
                    distanciaParcial += distanciaEntreLoc;
                    distanciaParcialMap += distanciaEntreLoc;
                    String logging=timeStamp.format(Calendar.getInstance().getTime()) +
                            ";" + round(distanciaEntreLoc,2) +
                            ";" + locActual.getLatitude() +
                            ";" + locActual.getLongitude() +
                            ";" + locActual.getSpeed();
                    appendLog(logging,0);
                    if (distanciaParcial > LIMIT_DIST_PAR) {
                        kmsParciales++;
                        tiempoPorKM(kmsParciales);
                        distanciaParcial = 0;
                    }
                    if(distanciaParcialMap > LIMIT_DIST_MAP){
                        String coord=locActual.getLatitude()+","+locActual.getLongitude();
                        appendLog(coord,2);
                        coordMap=coordMap+"|"+coord;
                        distanciaParcialMap = 0;
                        actualizarNotification();
                    }
                }
            }
            locAnterior=locActual;
            String logging=timeStamp.format(Calendar.getInstance().getTime()) +
                    ";" + round(distanciaEntreLoc,2) +
                    ";" + round(distanciaParcial,2) +
                    ";" + round(distanciaTotal/1000.0,2) +
                    ";" + locActual.getLatitude() + ";" + locActual.getLongitude() +
                    ";" + locActual.getSpeed() + ";" +
                    locActual.getExtras().getInt("satellites");
            appendLog(logging,-5);
        }

        public void onProviderDisabled(String provider) {
            textoAvoz("Sin señal GPS");
        }

        public void onProviderEnabled(String provider) {
            textoAvoz("Con señal GPS");
        }

        public void onStatusChanged(String provider, int status, Bundle extras){
        }
    }
}