package com.lucho.keeprunning;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    private final static int REQUEST_CODE_ASK_PERMISSIONS = 1;
    private static final String[] REQUIRED_SDK_PERMISSIONS = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private static final String API_KEY = "AIzaSyDOV_bXWDPpAEYPjYs6bL0UowQe1TAflMg";
    private GPSLocationService gpsService;
    private KeepRunning kr;
    private Button btnComenzar;
    private Button btnSalir;
    private TextView txtFecha;
    private TextView txtHoraInicio;
    private TextView txtHoraFin;
    private TextView txtDistanciaTotal;
    private TextView txtTiempoTotal;
    private TextView txtVelProm;
    private TextView txtCalConsum;
    private ImageView imgMap;
    private Boolean isRunning = false;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Intent intent = new Intent(this.getApplication(), GPSLocationService.class);
        this.getApplication().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        checkPermissions();
        startService(intent);
        imgMap = findViewById(R.id.imgMap);
        txtFecha = findViewById(R.id.txtFecha);
        txtHoraInicio = findViewById(R.id.txtHoraInicio);
        txtHoraFin = findViewById(R.id.txtHoraFin);
        txtDistanciaTotal = findViewById(R.id.txtDistanciaTotal);
        txtTiempoTotal = findViewById(R.id.txtTiempoTotal);
        txtVelProm = findViewById(R.id.txtVelProm);
        txtCalConsum = findViewById(R.id.txtCaloriasConsum);
        txtFecha.setVisibility(View.GONE);
        txtHoraFin.setVisibility(View.GONE);
        txtHoraInicio.setVisibility(View.GONE);
        txtDistanciaTotal.setVisibility(View.GONE);
        txtTiempoTotal.setVisibility(View.GONE);
        txtVelProm.setVisibility(View.GONE);
        txtCalConsum.setVisibility(View.GONE);
        imgMap.setVisibility(View.GONE);
        btnSalir = findViewById(R.id.btnSalir);
        btnSalir.setText("Salir");
        btnSalir.setEnabled(true);
        btnSalir.setBackgroundColor(Color.parseColor("#FF0000"));
        btnComenzar = findViewById(R.id.btnComenzar);
        btnComenzar.setText("Esperando ubicación");
        btnComenzar.setEnabled(false);
        btnComenzar.setBackgroundColor(Color.parseColor("#A4A7AB"));
        Timer timer = new Timer();
        timer.schedule(timerTask, 0, 1000);
        btnComenzar.setOnClickListener(v -> {
            if (!isRunning) {
                btnComenzar.setText("Detener");
                btnComenzar.setBackgroundColor(Color.parseColor("#FF0000"));
                txtFecha.setVisibility(View.GONE);
                txtHoraFin.setVisibility(View.GONE);
                txtHoraInicio.setVisibility(View.GONE);
                txtDistanciaTotal.setVisibility(View.GONE);
                txtTiempoTotal.setVisibility(View.GONE);
                txtVelProm.setVisibility(View.GONE);
                txtCalConsum.setVisibility(View.GONE);
                imgMap.setVisibility(View.GONE);
                btnSalir.setBackgroundColor(Color.parseColor("#A4A7AB"));
                btnSalir.setEnabled(false);
                kr.iniciar_carrera();
                gpsService.onStartCommand(intent, 0, 0);
            } else {
                btnComenzar.setText("Comenzar");
                btnComenzar.setBackgroundColor(Color.parseColor("#673AB7"));
                kr.finalizar_carrera();
                String url = "http://maps.google.com/maps/api/staticmap?path=color:0xff000080|weight:1" + kr.getCoorMaps() +
                        "&size=200x200" +
                        "&markers=size:tiny%7Ccolor:blue%7C" + kr.getPuntoInicio() +
                        "&markers=size:tiny%7Ccolor:red%7C" + kr.getPuntoFin() +
                        "&scale=2&sensor=false&key=" + API_KEY;
                new DownloadImageTask(findViewById(R.id.imgMap)).execute(url);
                imgMap.setVisibility(View.VISIBLE);
                txtFecha.setVisibility(View.VISIBLE);
                txtHoraFin.setVisibility(View.VISIBLE);
                txtHoraInicio.setVisibility(View.VISIBLE);
                txtDistanciaTotal.setVisibility(View.VISIBLE);
                txtTiempoTotal.setVisibility(View.VISIBLE);
                txtVelProm.setVisibility(View.VISIBLE);
                txtCalConsum.setVisibility(View.VISIBLE);
                txtFecha.setText("Fecha: " + kr.getFechaComienzo());
                txtHoraInicio.setText("Hora Inicio: " + kr.getHoraComienzo());
                txtHoraFin.setText("Hora Fin: " + kr.getHoraFin());
                txtDistanciaTotal.setText("Distancia Total: " + kr.round(kr.distancia_total_KM(), 2) + " km");
                txtTiempoTotal.setText("Tiempo Total: " + kr.tiempo_total());
                txtVelProm.setText("Vel. Prom: " + kr.velocidad_promedio());
                txtCalConsum.setText("Calorías Consum.: " + kr.calorias_consumidas());
                btnSalir.setEnabled(true);
                btnSalir.setBackgroundColor(Color.parseColor("#FF0000"));
            }
            isRunning = !isRunning;
        });
        btnSalir.setOnClickListener(v -> {
            gpsService.stopService();
            Intent intentSalir = new Intent(Intent.ACTION_MAIN);
            intentSalir.addCategory(Intent.CATEGORY_HOME);
            startActivity(intentSalir);
        });
    }

    private static class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
        ImageView bmImage;

        public DownloadImageTask(ImageView bmImage) {
            this.bmImage = bmImage;
        }

        protected Bitmap doInBackground(String... urls) {
            String urldisplay = urls[0];
            Bitmap mIcon11 = null;
            try {
                InputStream in = new java.net.URL(urldisplay).openStream();
                mIcon11 = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                Log.e("Error", e.getMessage());
            }
            return mIcon11;
        }

        protected void onPostExecute(Bitmap result) {
            bmImage.setImageBitmap(result);
        }
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            String name = className.getClassName();
            if (name.endsWith("GPSLocationService"))
                gpsService = ((GPSLocationService.LocationServiceBinder) service).getService();
        }

        public void onServiceDisconnected(ComponentName className) {
            if (className.getClassName().equals("BackgroundService")) gpsService = null;
        }
    };

    TimerTask timerTask = new TimerTask() {
        @Override
        public void run() {
            runOnUiThread(() -> {
                if (gpsService != null) {
                    if (gpsService.getReady()) {
                        btnComenzar.setText("Comenzar");
                        btnComenzar.setBackgroundColor(Color.parseColor("#673AB7"));
                        btnComenzar.setEnabled(true);
                        kr = new KeepRunning(gpsService, getApplicationContext());
                        timerTask.cancel();
                    }
                }
            });
        }
    };

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            moveTaskToBack(true);
            return true;
        }
        return false;
    }

    protected void checkPermissions() {
        final List<String> missingPermissions = new ArrayList<String>();
        for (final String permission : REQUIRED_SDK_PERMISSIONS) {
            final int result = ContextCompat.checkSelfPermission(this, permission);
            if (result != PackageManager.PERMISSION_GRANTED) missingPermissions.add(permission);
        }
        if (!missingPermissions.isEmpty()) {
            final String[] permissions = missingPermissions.toArray(new String[missingPermissions.size()]);
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_ASK_PERMISSIONS);
        } else {
            final int[] grantResults = new int[REQUIRED_SDK_PERMISSIONS.length];
            Arrays.fill(grantResults, PackageManager.PERMISSION_GRANTED);
            onRequestPermissionsResult(REQUEST_CODE_ASK_PERMISSIONS, REQUIRED_SDK_PERMISSIONS, grantResults);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode==REQUEST_CODE_ASK_PERMISSIONS) {
            for (int index = permissions.length - 1; index >= 0; --index) {
                if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Required permission '" + permissions[index] + "' not granted, exiting", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
            }
        }
    }
}