package com.lucho.running;

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
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;
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
import java.util.Random;
import static java.lang.System.exit;

public class GPSLocationService extends Service{
    //private static final String LOG_PATH = "/storage/B6CD-858A/Log/";
    private static final String MUSIC_PATH = "/storage/B6CD-858A/Musica/House/HC/";
    private static final String LOG_PATH = "/storage/emulated/0/Log/";
    private static final String CHANNEL_ID = "Channel_GPSLocationService";
    private static final int NOTIFICATION_ID = 12345678;
    private static final double VEL_LIMIT_MAX = 6.0;
    private static final double VEL_LIMIT_MIN = 1.0;
    private static final int MIN_SAT = 3;
    private static final double DIST_MAX = 10.0;
    private static final double R_TIERRA = 6371.0;
    private static final long LOCATION_INTERVAL = 1000;
    private static final float LOCATION_DISTANCE = (float) 0.1;
    private static final int LIMIT_DIST_MAP = 50;
    private static final int LIMIT_DIST_PAR = 1000;
    private final SimpleDateFormat timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
    private final LocationServiceBinder binder = new LocationServiceBinder();
    private LocationListener mLocationListener;
    private LocationManager mLocationManager;
    private Context mContext;
    private TextToSpeech tts;
    private MediaPlayer mPlayer;
    private AudioManager mAudioManager;
    private Date fechaHoraComienzo;
    private Date fechaHoraFin;
    private Date horaAnterior;
    private Location locInicio;
    private Location locActual;
    private Location locAnterior;
    private int kmsParciales;
    private double distanciaTotal;
    private double distanciaParcial;
    private double distanciaParcialMap;
    private Boolean isRunning;
    private String coordMap;
    private String log_name;
    private Notification.Builder builder;
    private NotificationManager notificationManager;
    private int contLocInicio;

    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void onCreate() {
        mContext=this;
        tts =new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            public void onInit(int status) {
                tts.setLanguage(new Locale("es","LA"));
            }
        });
        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        initializeLocationManager();
        startForeground(NOTIFICATION_ID, crearNotification());
        isRunning = false;
        coordMap="|";
        contLocInicio=0;
        locInicio=new Location("locInicio");
        locActual = new Location("locActual");
        locAnterior = new Location("locAnterior");
        kmsParciales =0;
        distanciaTotal =0;
        distanciaParcial =0;
        distanciaParcialMap =0;
        log_name =timeStamp.format(Calendar.getInstance().getTime());
        appendLog("Running comenzado: " + Calendar.getInstance().getTime(),0);
        appendLog("Fecha/Hora;Distancia(m);Distancia Parcial(m);Distancia Total(km);Latitud;Longitud;Velocidad;Velocidad GPS",0);
        play();
        fechaHoraComienzo= Calendar.getInstance().getTime();
        horaAnterior=Calendar.getInstance().getTime();
        tts.speak("¡Comenzando el running!", TextToSpeech.QUEUE_FLUSH, null);
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
        if(mPlayer!=null) {
            mPlayer.stop();
        }
        fechaHoraFin=Calendar.getInstance().getTime();
        appendLog("Running finalizado: " + Calendar.getInstance().getTime(),0);
        loguearInfoCarrera();
        isRunning = false;
        actualizarNotification();
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
        mLocationListener = new LocationListener(LocationManager.GPS_PROVIDER);
        try {
            mLocationManager.requestLocationUpdates( LocationManager.GPS_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE, mLocationListener );
            isLocationEnabled();
        } catch (java.lang.SecurityException | IllegalArgumentException ex) {
            appendLog(timeStamp.format(Calendar.getInstance().getTime()) + "FallÃ³ el start Listening",-1);
            exit(0);
        }
        isRunning = true;
        actualizarNotification();
    }

    public String getFecha() {
        return dateFormat.format(fechaHoraComienzo);
    }

    public String getHoraInicio() {
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
        return hours + "." + mins + "." + secs;
    }

    public String getPuntoInicio() { return locInicio.getLatitude() + "," + locInicio.getLongitude(); }

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

    private Uri proxTema(){
        File file = new File(MUSIC_PATH + getRandomFile());
        return Uri.fromFile(file);
    }

    protected void play() {
        mPlayer = MediaPlayer.create(this, proxTema());
        mPlayer.setLooping(false);
        mPlayer.start();
        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                mediaPlayer.reset();
                try {
                    mediaPlayer.setDataSource(mContext, proxTema());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    mediaPlayer.prepareAsync();
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
                mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mediaPlayer) {
                        mediaPlayer.start();
                    }
                });
            }
        });
    }

    private String getRandomFile() {
        File dir = new File(MUSIC_PATH);
        String[] files = dir.list();
        Random rand = new Random();
        int random_index = rand.nextInt(files.length);
        return (files[random_index]);
    }

    public void loguearInfoCarrera(){
        long millse = fechaHoraFin.getTime() - fechaHoraComienzo.getTime();
        long mills = Math.abs(millse);
        int hours = (int) (mills/(1000 * 60 * 60));
        int mins = (int) (mills/(1000*60)) % 60;
        long secs = (int) (mills / 1000) % 60;
        appendLog("Hora Inicio: " + timeFormat.format(fechaHoraComienzo),1);
        appendLog("Hora Fin: " + timeFormat.format(fechaHoraFin),1);
        appendLog("Distancia Total: " + distanciaTotal/1000 + " km",1);
        appendLog("Tiempo Total: " + hours + "." + mins + "." + secs ,1);
        appendLog("Coordenadas de origen: " + locInicio.getLatitude() + "," + locInicio.getLongitude() ,1);
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
        tts.speak(km + " kilometros, a " + mins + ", " + secs, TextToSpeech.QUEUE_FLUSH, null);
        horaAnterior = horaActual;
    }

    public static BigDecimal round(double d, int decimalPlace) {
        BigDecimal bd = new BigDecimal(String.valueOf(d)).setScale(decimalPlace, BigDecimal.ROUND_HALF_UP);
        return bd;
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
        if (!logFile.exists()){
            try{
                logFile.createNewFile();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
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
                    .setContentTitle("Running v6")
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(false);
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "NotificaciÃ³n para el Servicio", NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
        return builder.build();
    }

    private void actualizarNotification() {
        if(isRunning) {
            builder.setContentTitle("Running v6: comenzado...");
            builder.setContentText("Distancia Total: " + round(getDistanciaTotalKM(),2) + " km");
        }else{
            builder.setContentTitle("Running v6: detenido...");
            builder.setContentText("");
        }
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    public String getCoorMaps() { return coordMap; }


    private class LocationListener implements android.location.LocationListener {

        public LocationListener(String provider) {
        }

        public double calcularSegundos(Location last) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                //J7 PRIME Yeahhh (appendLog("Post 17>=JELLY_BEAN_MR1",-5);)
                long difNanos = (SystemClock.elapsedRealtimeNanos() - last.getElapsedRealtimeNanos());
                return difNanos / 100000000.0;
            }else {
                return System.currentTimeMillis() - last.getTime();
            }
        }

        public void onLocationChanged(Location location) {
            locActual = location;
            Boolean esMedidaValida = false;
            double distanciaEntreLoc = distance_between();
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
            if (distanciaEntreLoc<DIST_MAX &&
                    locActual.getExtras().getInt("satellites")>MIN_SAT &&
                    locActual.hasSpeed() &&
                    locActual.getSpeed() > VEL_LIMIT_MIN &&
                    locActual.getSpeed() < VEL_LIMIT_MAX) {
                esMedidaValida = true;
            }
            double vel=locActual.getSpeed();
            if(locInicio.getLatitude()==0) {
                if(contLocInicio==3) {
                    locInicio = location;
                    coordMap = coordMap + locInicio.getLatitude() + "," + locInicio.getLongitude();
                    appendLog(coordMap, 2);
                    coordMap = coordMap + "|" + coordMap;
                }else{
                    contLocInicio++;
                }
            }else {
                //double segundos = calcularSegundos(locActual);
                //double vel = distanciaEntreLoc / segundos;
                if(esMedidaValida) {
                    distanciaTotal += distanciaEntreLoc;
                    distanciaParcial += distanciaEntreLoc;
                    distanciaParcialMap += distanciaEntreLoc;
                    String logging=timeStamp.format(Calendar.getInstance().getTime()) + ";" +
                            distanciaEntreLoc + ";" + distanciaParcial + ";" + distanciaTotal/1000.0 +
                            locActual.getLatitude() + ";" + locActual.getLongitude() + ";" + locActual.getSpeed();
                    appendLog(logging,0);
                }
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
            locAnterior=locActual;
        }

        public void onProviderDisabled(String provider) {
            tts.speak("Sin seÃ±al GPS", TextToSpeech.QUEUE_FLUSH, null);
        }

        public void onProviderEnabled(String provider) {
            tts.speak("Con seÃ±al GPS", TextToSpeech.QUEUE_FLUSH, null);
        }

        public void onStatusChanged(String provider, int status, Bundle extras){
        }
    }
}