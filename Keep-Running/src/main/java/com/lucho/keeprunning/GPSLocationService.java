package com.lucho.keeprunning;

import android.annotation.SuppressLint;
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
import android.util.Log;

import static java.lang.System.exit;

public class GPSLocationService extends Service {
    private static final String CHANNEL_ID = "Channel_GPSLocationService";
    private static final int NOTIFICATION_ID = 12345678;
    private static final int LIMIT_INT_UBI_INICIAL = 3;
    private static final long LOCATION_INTERVAL = 1000;
    private static final float LOCATION_DISTANCE = (float) 0.1;
    private final LocationServiceBinder binder = new LocationServiceBinder();
    private LocationListener mLocationListener;
    private LocationManager mLocationManager;
    private Location currentLocation;
    private int contLocInicio;
    private Boolean isReady;
    private Notification.Builder builder;
    private NotificationManager notificationManager;
    private TextToVoice ttv;

    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void onCreate() {
        initializeLocationManager();
        startForeground(NOTIFICATION_ID, crear_notification());
        ttv=new TextToVoice(this);
        actualizar_notification("Esperando ubicación...", "");
        isReady = false;
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        startListening();
        return START_NOT_STICKY;
    }

    public void stopService() {
        if (mLocationManager != null) {
            try {
                mLocationManager.removeUpdates(mLocationListener);
            } catch (Exception ex) {
                Log.e("stopService: ", ex.getMessage());
            }
        }
        stopForeground(true);
        onDestroy();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void initializeLocationManager() {
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
            mLocationManager.getProvider(LocationManager.GPS_PROVIDER);
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            criteria.setPowerRequirement(Criteria.POWER_HIGH);
            criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);
            criteria.setSpeedRequired(true);
            String providerName = mLocationManager.getBestProvider(criteria, true);
            if (providerName == null) {
                Log.e("initLocationManager: ", "No hay proovedores GPS con estos criterios");
                exit(0);
            }
        }
    }

    public void startListening() {
        mLocationListener = new LocationListener();
        try {
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE, mLocationListener);
            isLocationEnabled();
        } catch (java.lang.SecurityException | IllegalArgumentException ex) {
            Log.e("startListening: ", "Falló el start Listening");
            exit(0);
        }
    }

    public class LocationServiceBinder extends Binder {
        public GPSLocationService getService() {return GPSLocationService.this;}
    }

    private void isLocationEnabled() {
        if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            ttv.text_to_voice("Sin servicio de localización.");
            Log.e("isLocationEnabled: ", "false");
        } else {
            ttv.text_to_voice("Servicio de localización activado");
        }
    }

    public Boolean getReady() {
        return isReady;
    }

    public Location getCurrentLocation() {
        return currentLocation;
    }

    private class LocationListener implements android.location.LocationListener {

        public LocationListener() {
        }

        public void onLocationChanged(Location location) {
            currentLocation = location;
            if (!isReady) {
                contLocInicio++;
                if (contLocInicio == LIMIT_INT_UBI_INICIAL){
                    ttv.text_to_voice("Ubicación localizada");
                    actualizar_notification("Listo", "");
                    isReady = true;
                }
            }
        }

        public void onProviderDisabled(String provider) { ttv.text_to_voice("Se perdió la señal GPS");}

        public void onProviderEnabled(String provider) { ttv.text_to_voice("Señal GPS encontrada");}

        public void onStatusChanged(String provider, int status, Bundle extras) {}
    }

    private Notification crear_notification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setAction(Intent.ACTION_MAIN);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            @SuppressLint("UnspecifiedImmutableFlag") PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
            builder = new Notification.Builder(getApplicationContext(), CHANNEL_ID)
                    .setSmallIcon(R.drawable.descarga)
                    .setContentTitle("Keep Running")
                    .setContentIntent(pendingIntent)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(false);
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Notificación para el Servicio", NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
        return builder.build();
    }

    public void actualizar_notification(String title, String body) {
        builder.setContentTitle(title);
        builder.setContentText(body);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }
}