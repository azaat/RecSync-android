package com.googleresearch.capturesync.sensorlogging;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Log;

import com.googleresearch.capturesync.MainActivity;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles frame images and timestamps saving during video recording,
 * sequential Executor is used to queue saving tasks in the background thread.
 * Images get saved every EVERY_N_FRAME-th time if shouldSaveFrame is true
 */
public class VideoFrameInfo implements Closeable {
    private final static String TAG = "FrameInfo";
    private final static String TIMESTAMP_FILE_SUFFIX = "_timestamps";
    /*
    Value used to save frames for debugging and matching frames with video
    TODO: in future versions make sure this value is big enough not to cause frame rate drop / buffer allocation problems on devices other than already tested
     */
    private final static int EVERY_N_FRAME = 60;
    private final static int PHASE_CALC_N_FRAMES = 60;

    //Sequential executor for frame and timestamps saving queue
    private final ExecutorService frameProcessor = Executors.newSingleThreadExecutor();
    private final MainActivity mContext;
    private final BlockingQueue<VideoPhaseInfo> mPhaseInfoReporter;
    private final List<Long> durationsNs;
    private long mLastTimestamp = 0;

    private int mFrameNumber = 0;

    public BlockingQueue<VideoPhaseInfo> getPhaseInfoReporter() {
        return mPhaseInfoReporter;
    }

    public VideoFrameInfo(
            MainActivity context,
            boolean shouldSaveFrames,
            BlockingQueue<VideoPhaseInfo> videoPhaseInfoReporter
    ) throws IOException {
        mContext = context;
        mPhaseInfoReporter = videoPhaseInfoReporter;
        mPhaseInfoReporter.clear();
        durationsNs = new ArrayList<>();
    }

    public void submitProcessFrame(long timestamp) {
        if (!frameProcessor.isShutdown()) {
            frameProcessor.execute(
                    () -> {
                        // TODO: here we assume that video has more frames than PHASE_CALC_N_FRAMES
                        if (mFrameNumber < PHASE_CALC_N_FRAMES) {
                            // Should calculate phase
                            if (mLastTimestamp != 0) {
                                long duration = timestamp - mLastTimestamp;
                                // add frame duration
                                Log.d(TAG, "new frame duration, value: " + duration);
                                durationsNs.add(duration);
                            }
                            mLastTimestamp = timestamp;
                        } else if (mFrameNumber == PHASE_CALC_N_FRAMES) {
                            // Should report phase

                            long exposureTime = 0;
                            mPhaseInfoReporter.add(
                                    new VideoPhaseInfo(timestamp, durationsNs, exposureTime)
                            );
                        }

                        mFrameNumber++;
                    }
            );
        } else {
            Log.e(TAG, "Received new frame after frameProcessor executor shutdown");
        }
    }


    private void writeFrameJpeg(Bitmap bitmap, File frameFile, int rotation) throws IOException {
        FileOutputStream fos = new FileOutputStream(frameFile);
        // Apply rotation
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
        fos.close();
    }

    @Override
    public void close() {
        // Clear current phase info to avoid it being reported in the next recordings
        mPhaseInfoReporter.clear();

        if (frameProcessor != null) {
            Log.d(TAG, "Attempting to shutdown frame processor");
            // should let all assigned tasks finish execution
            frameProcessor.shutdown();
        }

        Log.d(TAG, "Closing frame info, frame number: " + mFrameNumber);

    }
}
