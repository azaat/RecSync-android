package com.googleresearch.capturesync.sensorlogging;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;


import com.googleresearch.capturesync.MainActivity;
import com.googleresearch.capturesync.softwaresync.SoftwareSyncBase;
import com.googleresearch.capturesync.softwaresync.TimeDomainConverter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Handles gyroscope and accelerometer raw info recording
 * Assumes all the used sensor types are motion or position sensors
 * and output [x, y, z] values -- the class should be updated if that changes
 */
public class RawSensorInfo implements SensorEventListener, LocationListener {
    private static final String TAG = "RawSensorInfo";
    private static final String CSV_SEPARATOR = ",";
    public static final int TYPE_GPS = 0xabcd;
    private static final List<Integer> SENSOR_TYPES = Collections.unmodifiableList(
            Arrays.asList(
                    TYPE_GPS, Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE,
                    Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_GRAVITY, Sensor.TYPE_ROTATION_VECTOR)
    );

    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];

    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    private static final Map<Integer, String> SENSOR_TYPE_NAMES;
    static {
        SENSOR_TYPE_NAMES = new HashMap<>();
        SENSOR_TYPE_NAMES.put(Sensor.TYPE_ACCELEROMETER, "accel");
        SENSOR_TYPE_NAMES.put(Sensor.TYPE_GYROSCOPE, "gyro");
        SENSOR_TYPE_NAMES.put(Sensor.TYPE_MAGNETIC_FIELD, "magnetic");
        SENSOR_TYPE_NAMES.put(Sensor.TYPE_GRAVITY, "gravity");
        SENSOR_TYPE_NAMES.put(Sensor.TYPE_ROTATION_VECTOR, "rotation");
        SENSOR_TYPE_NAMES.put(TYPE_GPS, "location");
    }

    final private SensorManager mSensorManager;
    private final LocationManager mLocationManager;
    private final MainActivity mContext;
    /*    final private Sensor mSensorGyro;
        final private Sensor mSensorAccel;
        final private Sensor mSensorMagnetic;
        private PrintWriter mGyroBufferedWriter;
        private PrintWriter mAccelBufferedWriter;*/
    private boolean mIsRecording;
    private final Map<Integer, Object> mUsedSensorMap;
    private final Map<Integer, PrintWriter> mSensorWriterMap;
    private final Map<Integer, File> mLastSensorFilesMap;

    public Map<Integer, File> getLastSensorFilesMap() {
        return mLastSensorFilesMap;
    }

    public boolean isSensorAvailable(int sensorType) {
        return mUsedSensorMap.get(sensorType) != null;
    }

    public RawSensorInfo(MainActivity context) {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mLocationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
        mUsedSensorMap = new HashMap<>();
        mSensorWriterMap = new HashMap<>();
        mContext = context;
        mLastSensorFilesMap = new HashMap<>();

        for (Integer sensorType : SENSOR_TYPES) {
            if (sensorType != TYPE_GPS) {
                mUsedSensorMap.put(sensorType, mSensorManager.getDefaultSensor(sensorType));
            } else {
                mUsedSensorMap.put(sensorType, new Object());
            }
        }
/*      mSensorGyro = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mContext = context;
        mSensorMagnetic = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        if (MyDebug.LOG) {
            Log.d(TAG, "RawSensorInfo");
            if (mSensorGyro == null) {
                Log.d(TAG, "Gyroscope not available");
            }
            if (mSensorAccel == null) {
                Log.d(TAG, "Accelerometer not available");
            }
        }*/
    }

    public int getSensorMinDelay(int sensorType) {
        Object sensor = mUsedSensorMap.get(sensorType);
        if (sensor != null) {
            if (sensor instanceof Sensor) {
                return ((Sensor)sensor).getMinDelay();
            }
        }
        // Unsupported sensorType
        Log.d(TAG, "Unsupported sensor type was provided");
        return 0;
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    private class MyEvent {
        public int accuracy;
        public int type;
//        public Sensor sensor;
        public long timestamp;
        public float[] values;
    }

    @Override
    public void onLocationChanged(Location location) {
        if( location != null && ( location.getLatitude() != 0.0d || location.getLongitude() != 0.0d ) ) {
            MyEvent event = new MyEvent();
            event.type = TYPE_GPS;
            event.timestamp = location.getElapsedRealtimeNanos();
            event.values = new float[]{(float) location.getLatitude(), (float) location.getLongitude(), (float) location.getAltitude()};
            _onSensorChanged(event);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        {
            MyEvent e = new MyEvent();
            e.accuracy = event.accuracy;
            e.type = event.sensor.getType();
            e.timestamp = event.timestamp;
            e.values = event.values;
            _onSensorChanged(e);
        }
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading);
            SensorManager.getOrientation(rotationMatrix, orientationAngles);

            MyEvent e = new MyEvent();
            e.accuracy = event.accuracy;
            e.type = Sensor.TYPE_ROTATION_VECTOR;
            e.timestamp = event.timestamp;
            e.values = orientationAngles;
            _onSensorChanged(e);

        }
    }

    private void _onSensorChanged(MyEvent event) {
        if (mIsRecording) {
            if (event.type == Sensor.TYPE_ACCELEROMETER) {
                System.arraycopy(event.values, 0, accelerometerReading,
                        0, accelerometerReading.length);
            } else if (event.type == Sensor.TYPE_MAGNETIC_FIELD) {
                System.arraycopy(event.values, 0, magnetometerReading,
                        0, magnetometerReading.length);
            }

            StringBuilder sensorData = new StringBuilder();
            for (int j = 0; j < 3; j++) {
                sensorData.append(event.values[j]).append(CSV_SEPARATOR);
            }
            Log.d(TAG, "Converting time");
            long timestamp = event.timestamp;
            if (mContext.getTimeConverter() != null) {
                timestamp = mContext.getTimeConverter().leaderTimeForLocalTimeNs(timestamp);
            }
            sensorData.append(timestamp).append("\n");

            Object sensor = mUsedSensorMap.get(event.type);
            if (sensor != null) {
                PrintWriter sensorWriter = mSensorWriterMap.get(event.type);
                if (sensorWriter != null) {
                    sensorWriter.write(sensorData.toString());
                } else {
                    Log.d(TAG, "Sensor writer for the requested type wasn't initialized");
                }
            }
            /*if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER && mAccelBufferedWriter != null) {
                mAccelBufferedWriter.write(sensorData.toString());
            } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE && mGyroBufferedWriter != null) {
                mGyroBufferedWriter.write(sensorData.toString());
            }*/
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO: Add logs for when sensor accuracy decreased
    }

    /**
     * Handles sensor info file creation, uses StorageUtils to work both with SAF and standard file
     * access.
     */
    private FileWriter getRawSensorInfoFileWriter(MainActivity mainActivity, Integer sensorType, String sensorName,
                                                  Date lastVideoDate) throws IOException {
        FileWriter fileWriter;
        try {
            File sdcard = Environment.getExternalStorageDirectory();
            Path dir = Files.createDirectories(Paths.get(sdcard.getAbsolutePath(), MainActivity.SUBDIR_NAME));
            File saveFile = new File(dir.toFile(), lastVideoDate + "_" + sensorName + ".csv");
            fileWriter = new FileWriter(saveFile);
            Log.d(TAG, "save to: " + saveFile.getAbsolutePath());
            mLastSensorFilesMap.put(sensorType, saveFile);
            return fileWriter;
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "failed to open raw sensor info files");
            throw new IOException(e);
        }
    }

    private PrintWriter setupRawSensorInfoWriter(MainActivity mainActivity, Integer sensorType, String sensorName,
            Date currentVideoDate) throws IOException {
        FileWriter rawSensorInfoFileWriter = getRawSensorInfoFileWriter(
                mainActivity, sensorType, sensorName, currentVideoDate
        );
        return new PrintWriter(
                new BufferedWriter(rawSensorInfoFileWriter)
        );
    }

    public void startRecording(MainActivity mainActivity, Date currentVideoDate) {
        Map<Integer, Boolean> wantSensorRecordingMap = new HashMap<>();
        for (Integer sensorType : SENSOR_TYPES) {
            wantSensorRecordingMap.put(sensorType, true);
        }
        startRecording(mainActivity, currentVideoDate, wantSensorRecordingMap);
    }

    public void startRecording(MainActivity mainActivity, Date currentVideoDate, Map<Integer, Boolean> wantSensorRecordingMap) {
        mLastSensorFilesMap.clear();
        try {
/*            if (wantGyroRecording && mSensorGyro != null) {
                mGyroBufferedWriter = setupRawSensorInfoWriter(
                        mainActivity, SENSOR_TYPE_GYRO, currentVideoDate
                );
            }
            if (wantAccelRecording && mSensorAccel != null) {
                mAccelBufferedWriter = setupRawSensorInfoWriter(
                        mainActivity, SENSOR_TYPE_ACCEL, currentVideoDate
                );
            }*/
            for (Integer sensorType : wantSensorRecordingMap.keySet()) {
                Boolean wantRecording = wantSensorRecordingMap.get(sensorType);
                if (sensorType != null &&
                        wantRecording != null &&
                        wantRecording == true
                ) {
                    mSensorWriterMap.put(
                            sensorType,
                            setupRawSensorInfoWriter(mainActivity, sensorType, SENSOR_TYPE_NAMES.get(sensorType), currentVideoDate)
                    );
                }
            }
            mIsRecording = true;
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Unable to setup sensor info writer");
        }
    }

    public void stopRecording() {
        Log.d(TAG, "Close all files");
        for (PrintWriter sensorWriter : mSensorWriterMap.values()) {
            if (sensorWriter != null) {
                sensorWriter.close();
            }
        }
        /*if (mGyroBufferedWriter != null) {
            mGyroBufferedWriter.flush();
            mGyroBufferedWriter.close();
        }
        if (mAccelBufferedWriter != null) {
            mAccelBufferedWriter.flush();
            mAccelBufferedWriter.close();
        }*/
        mIsRecording = false;
    }

    public boolean isRecording() {
        return mIsRecording;
    }

    public void enableSensors(Map<Integer, Integer> sampleRateMap) {

        Log.d(TAG, "enableSensors");
        for (Integer sensorType : mUsedSensorMap.keySet()) {
            Integer sampleRate = sampleRateMap.get(sensorType);
            if (sampleRate == null) {
                // Assign default value if not provided
                sampleRate = 0;
            }

            if (sensorType != null) {
                enableSensor(sensorType, sampleRate);
            }

        }
        /*enableSensor(Sensor.TYPE_GYROSCOPE, gyroSampleRate);
        enableSensor(Sensor.TYPE_ACCELEROMETER, accelSampleRate);*/
    }


    /**
     * Enables sensor with specified frequency
     * @return Returns false if sensor isn't available
     */
    public boolean enableSensor(int sensorType, int sampleRate) {
        Log.d(TAG, "enableSensor");

        Object sensor = mUsedSensorMap.get(sensorType);
        if (sensor != null) {
            mSensorManager.registerListener(this, (Sensor)sensor, sampleRate);

            return true;
        } else {
            return false;
        }

    }

    public void disableSensors() {
        Log.d(TAG, "disableSensors");
        mSensorManager.unregisterListener(this);
        mLocationManager.removeUpdates(this);
    }
}
