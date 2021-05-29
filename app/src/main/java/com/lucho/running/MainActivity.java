package com.lucho.running;

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
import android.os.Build;
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


public class MainActivity extends AppCompatActivity {
    private final static int REQUEST_CODE_ASK_PERMISSIONS = 1;
    private static final String[] REQUIRED_SDK_PERMISSIONS = new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE };
    private static final String API_KEY = "AIzaSyDOV_bXWDPpAEYPjYs6bL0UowQe1TAflMg";
    private GPSLocationService gpsService;
    private Button btnComenzar;
    private TextView txtFecha;
    private TextView txtHoraInicio;
    private TextView txtHoraFin;
    private TextView txtDistanciaTotal;
    private TextView txtTiempoTotal;
    private ImageView imgMap;
    private Boolean isRunning;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Intent intent = new Intent(this.getApplication(), GPSLocationService.class);
        this.getApplication().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        checkPermissions();
        imgMap=findViewById(R.id.imgMap);
        txtFecha=findViewById(R.id.txtFecha);
        txtHoraInicio=findViewById(R.id.txtHoraInicio);
        txtHoraFin=findViewById(R.id.txtHoraFin);
        txtDistanciaTotal=findViewById(R.id.txtDistanciaTotal);
        txtTiempoTotal=findViewById(R.id.txtTiempoTotal);
        txtFecha.setVisibility(View.GONE);
        txtHoraFin.setVisibility(View.GONE);
        txtHoraInicio.setVisibility(View.GONE);
        txtDistanciaTotal.setVisibility(View.GONE);
        txtTiempoTotal.setVisibility(View.GONE);
        imgMap.setVisibility(View.GONE);
        isRunning=false;
        btnComenzar= findViewById(R.id.btnComenzar);
        btnComenzar.setOnClickListener(v -> {
            if(!isRunning) {
                btnComenzar.setText("Detener");
                btnComenzar.setBackgroundColor(Color.parseColor("#FF0000"));
                txtFecha.setVisibility(View.GONE);
                txtHoraFin.setVisibility(View.GONE);
                txtHoraInicio.setVisibility(View.GONE);
                txtDistanciaTotal.setVisibility(View.GONE);
                txtTiempoTotal.setVisibility(View.GONE);
                imgMap.setVisibility(View.GONE);
                gpsService.onStartCommand(intent, 0, 0);
            }else{
                btnComenzar.setText("Comenzar");
                btnComenzar.setBackgroundColor(Color.parseColor("#673AB7"));
                String puntoInicio=gpsService.getPuntoInicio();
                String url = "http://maps.google.com/maps/api/staticmap?path=color:0xff000080|weight:1" + gpsService.getCoorMaps() +
                        "&size=200x200&markers=color:blue%7Clabel:Punto de Inicio|" + puntoInicio +"&scale=2&sensor=false&key=" + API_KEY;
                Log.e("coord: ", gpsService.getCoorMaps());
                new DownloadImageTask(findViewById(R.id.imgMap))
                        .execute(url);
                gpsService.stopService();
                imgMap.setVisibility(View.VISIBLE);
                txtFecha.setVisibility(View.VISIBLE);
                txtHoraFin.setVisibility(View.VISIBLE);
                txtHoraInicio.setVisibility(View.VISIBLE);
                txtDistanciaTotal.setVisibility(View.VISIBLE);
                txtTiempoTotal.setVisibility(View.VISIBLE);
                txtFecha.setText("Fecha: " + gpsService.getFecha());
                txtHoraInicio.setText("Hora Inicio: " + gpsService.getHoraInicio());
                txtHoraFin.setText("Hora Fin: " + gpsService.getHoraFin());
                txtDistanciaTotal.setText("Distancia Total: " + gpsService.round(gpsService.getDistanciaTotalKM(),2) + " km");
                txtTiempoTotal.setText("Tiempo Total: " + gpsService.getTiempoTotal());
            }
            isRunning=!isRunning;
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
                e.printStackTrace();
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
            if (name.endsWith("GPSLocationService")) {
                gpsService = ((GPSLocationService.LocationServiceBinder) service).getService();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            if (className.getClassName().equals("BackgroundService")) {
                gpsService = null;
            }
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
            if (result != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        if (!missingPermissions.isEmpty()) {
            final String[] permissions = missingPermissions
                    .toArray(new String[missingPermissions.size()]);
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_ASK_PERMISSIONS);
        } else {
            final int[] grantResults = new int[REQUIRED_SDK_PERMISSIONS.length];
            Arrays.fill(grantResults, PackageManager.PERMISSION_GRANTED);
            onRequestPermissionsResult(REQUEST_CODE_ASK_PERMISSIONS, REQUIRED_SDK_PERMISSIONS,
                    grantResults);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_CODE_ASK_PERMISSIONS:
                for (int index = permissions.length - 1; index >= 0; --index) {
                    if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "Required permission '" + permissions[index]
                                + "' not granted, exiting", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                }
                break;
        }
    }
}