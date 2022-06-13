package com.lucho.keeprunning;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Logging {

    public void appendLog(KeepRunning kr) {
        String LOG_PATH = "/storage/emulated/0/KeepRunningLogs/";
        String logName = "KeepRunningWorkouts.log";
        File logFile =new File(LOG_PATH + logName);
        try {
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(kr.getId()).append(";");
            buf.append(kr.getFechaComienzo()).append(";");
            buf.append(kr.getHoraComienzo()).append(";");
            buf.append(kr.getHoraFin()).append(";");
            buf.append(String.valueOf(KeepRunning.round(kr.distancia_total_KM(), 2))).append(";");
            buf.append(kr.tiempo_total()).append(";");
            buf.append(kr.velocidad_promedio()).append(";");
            buf.append(kr.calorias_consumidas()).append(";");
            buf.append(kr.getCoorMaps());
            buf.newLine();
            buf.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
