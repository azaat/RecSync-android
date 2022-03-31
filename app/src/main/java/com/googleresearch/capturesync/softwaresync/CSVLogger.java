package com.googleresearch.capturesync.softwaresync;

import android.os.Environment;

import com.googleresearch.capturesync.CameraActivity;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CSVLogger {
    private final BufferedWriter writer;

    public boolean isClosed() {
        return isClosed;
    }

    private volatile boolean isClosed;

    public CSVLogger(String dirName, String filename, CameraActivity context) throws IOException {
        isClosed = true;
        File sdcard = Environment.getExternalStorageDirectory();
        Path dir = Files.createDirectories(Paths.get(sdcard.getAbsolutePath(), dirName));
        File file = new File(dir.toFile(), filename);
        writer = new BufferedWriter(new FileWriter(file, true));

        // Important: adding comment with metadata before isClosed is changed
//        writer.write("# " + Build.MODEL);
//        writer.write("\n");
//        writer.write("# " + Build.VERSION.SDK_INT);
//        writer.write("\n");
//
//
//        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
//        try {
//            String[] idList = manager.getCameraIdList();
//
//            Map<Integer, String> levels = new HashMap<Integer, String>() {{
//                put(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY, "LEGACY");
//                put(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED, "LIMITED");
//                put(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL, "FULL");
//                put(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL, "EXTERNAL");
//                put(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3, "LEVEL_3");
//            }};
//
//
//            int maxCameraCnt = idList.length;
//            writer.write("# " + maxCameraCnt);
//            writer.write("\n");
//            for (int index = 0; index < maxCameraCnt; index++) {
//                String cameraId = manager.getCameraIdList()[index];
//                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
//                String deviceLevel = levels.get(characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL));
//                String source = characteristics.get(
//                        CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE
//                ) == CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME ? "REALTIME" : "UNKNOWN";
//                writer.write("# " + source + " " + deviceLevel);
//                writer.write("\n");
//            }
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }


        isClosed = false;
    }

    public void logLine(String line) throws IOException {
        writer.write(line);
        writer.write("\n");
    }

    public void close() {
        try {
            isClosed = true;
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
