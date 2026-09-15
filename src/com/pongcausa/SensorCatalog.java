package com.pongcausa;

import java.util.ArrayList;
import java.util.List;

/** Catálogo de sensores disponíveis no dispositivo (equivalente à escolha do Polar H7 na monografia). */
public class SensorCatalog {
    public static class SensorInfo {
        public final String name;
        public final String type;
        public final String vendor;
        public final float resolution;
        public final float maxRange;
        public final float power;
        public final int minDelay;
        public SensorInfo(String name, String type, String vendor, float resolution, float maxRange, float power, int minDelay) {
            this.name = name; this.type = type; this.vendor = vendor;
            this.resolution = resolution; this.maxRange = maxRange; this.power = power; this.minDelay = minDelay;
        }
    }
    public final List<SensorInfo> available = new ArrayList<>();
    public final boolean accelerometer, gyroscope, magnetometer, heartRate, stepCounter, proximity, light;
    public final int count;

    public SensorCatalog(android.hardware.SensorManager sm) {
        List<android.hardware.Sensor> all = sm.getSensorList(android.hardware.Sensor.TYPE_ALL);
        boolean acc=false, gyr=false, mag=false, hr=false, st=false, px=false, li=false;
        for (android.hardware.Sensor s : all) {
            String t = typeName(s.getType());
            available.add(new SensorInfo(s.getName(), t, s.getVendor(), s.getResolution(), s.getMaximumRange(), s.getPower(), s.getMinDelay()));
            switch (s.getType()) {
                case android.hardware.Sensor.TYPE_ACCELEROMETER: acc=true; break;
                case android.hardware.Sensor.TYPE_GYROSCOPE: gyr=true; break;
                case android.hardware.Sensor.TYPE_MAGNETIC_FIELD: mag=true; break;
                case android.hardware.Sensor.TYPE_HEART_RATE: hr=true; break;
                case android.hardware.Sensor.TYPE_STEP_COUNTER: st=true; break;
                case android.hardware.Sensor.TYPE_PROXIMITY: px=true; break;
                case android.hardware.Sensor.TYPE_LIGHT: li=true; break;
            }
        }
        count = all.size();
        accelerometer=acc; gyroscope=gyr; magnetometer=mag; heartRate=hr; stepCounter=st; proximity=px; light=li;
    }

    static String typeName(int t) {
        switch (t) {
            case android.hardware.Sensor.TYPE_ACCELEROMETER: return "accelerometer";
            case android.hardware.Sensor.TYPE_GYROSCOPE: return "gyroscope";
            case android.hardware.Sensor.TYPE_MAGNETIC_FIELD: return "magnetometer";
            case android.hardware.Sensor.TYPE_HEART_RATE: return "heart_rate";
            case android.hardware.Sensor.TYPE_STEP_COUNTER: return "step_counter";
            case android.hardware.Sensor.TYPE_PROXIMITY: return "proximity";
            case android.hardware.Sensor.TYPE_LIGHT: return "light";
            case android.hardware.Sensor.TYPE_LINEAR_ACCELERATION: return "linear_acceleration";
            case android.hardware.Sensor.TYPE_GRAVITY: return "gravity";
            case android.hardware.Sensor.TYPE_ROTATION_VECTOR: return "rotation_vector";
            case android.hardware.Sensor.TYPE_GAME_ROTATION_VECTOR: return "game_rotation_vector";
            case android.hardware.Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR: return "geomagnetic_rotation_vector";
            case android.hardware.Sensor.TYPE_ORIENTATION: return "orientation";
            case android.hardware.Sensor.TYPE_PRESSURE: return "pressure";
            case android.hardware.Sensor.TYPE_AMBIENT_TEMPERATURE: return "ambient_temperature";
            case android.hardware.Sensor.TYPE_RELATIVE_HUMIDITY: return "relative_humidity";
            case android.hardware.Sensor.TYPE_SIGNIFICANT_MOTION: return "significant_motion";
            case android.hardware.Sensor.TYPE_STEP_DETECTOR: return "step_detector";
            default: return "type_" + t;
        }
    }
}
