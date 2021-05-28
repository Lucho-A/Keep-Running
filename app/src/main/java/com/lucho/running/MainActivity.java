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

import java.io.InputStream;


public class MainActivity extends AppCompatActivity {
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
        isReadStoragePermissionGranted();
        isWriteStoragePermissionGranted();
        isLocationPermissionGranted();
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
        btnComenzar.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
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
            }
        });
    }

    private class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
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
        switch(keyCode) {
            case KeyEvent.KEYCODE_BACK:
                moveTaskToBack(true);
                return true;
        }
        return false;
    }

    private boolean isReadStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.v("App","Permission is granted");
                return true;
            } else {
                Log.v("App","Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 3);
                return false;
            }
        }
        else {
            Log.v("App","Permission is granted");
            return true;
        }
    }

    private boolean isWriteStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.v("App","Permission is granted");
                return true;
            } else {
                Log.v("App","Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
                return false;
            }
        }
        else {
            Log.v("App","Permission is granted");
            return true;
        }
    }

    private boolean isLocationPermissionGranted() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.v("App","Permission is granted");
                return true;
            } else {
                Log.v("App","Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 2);
                return false;
            }
        }
        else {
            Log.v("App","Permission is granted");
            return true;
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            Log.v("App","Permission: "+permissions[0]+ "was "+grantResults[0]);
        }
    }
}