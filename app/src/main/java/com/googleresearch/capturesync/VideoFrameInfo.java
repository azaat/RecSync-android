package com.googleresearch.capturesync;

import static com.googleresearch.capturesync.VideoHelpers.SUBDIR_NAME;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Environment;
import android.util.Log;


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
    private final static int EVERY_N_FRAME = 30;

    //Sequential executor for frame and timestamps saving queue
    private final ExecutorService frameProcessor = Executors.newSingleThreadExecutor();
    private final String mVideoDate;
    private final boolean mShouldSaveFrames;
    private final MainActivity mContext;
    private final YuvImageUtils mYuvUtils;
    private final List<Long> durationsNs;
    private long mLastTimestamp = 0;

    private int mFrameNumber = 0;


    public VideoFrameInfo(
            String videoDate,
            MainActivity context,
            boolean shouldSaveFrames
    ) throws IOException {
        mVideoDate = videoDate;
        mShouldSaveFrames = shouldSaveFrames;
        mContext = context;
        mYuvUtils = new YuvImageUtils(mContext);
        durationsNs = new ArrayList<>();

    }

    public void submitProcessFrame(long timestamp, byte[] imageData, int width, int height) {
        // Submit image data (only if needed)
        if (!frameProcessor.isShutdown()) {
            frameProcessor.execute(
                    () -> {
                        try {
                            if (mShouldSaveFrames && mFrameNumber % EVERY_N_FRAME == 0) {
                                Bitmap bitmap = mYuvUtils.yuv420ToBitmap(imageData, width, height, mContext);
                                File sdcard = Environment.getExternalStorageDirectory();
                                File pdir = new File(sdcard.getAbsolutePath(), SUBDIR_NAME);
                                File dir = new File(pdir, mContext.getLastTimeStamp());
                                File frameFile = new File(dir, timestamp + ".jpg");

                                writeFrameJpeg(bitmap, frameFile);
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Failed to write frame info, timestamp: " + timestamp);
                            e.printStackTrace();
                            this.close();
                        }
                    }
            );
        } else {
            Log.e(TAG, "Received new frame after frameProcessor executor shutdown");
        }

    }

    private void writeFrameJpeg(Bitmap bitmap, File frameFile) throws IOException {
        FileOutputStream fos = new FileOutputStream(frameFile);
        // Apply rotation
        Matrix matrix = new Matrix();
//        matrix.postRotate(rotation);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
        fos.close();
    }

    @Override
    public void close() {
        if (frameProcessor != null) {
            Log.d(TAG, "Attempting to shutdown frame processor");

            // should let all assigned tasks finish execution
            frameProcessor.shutdown();
        }

        Log.d(TAG, "Closing frame info, frame number: " + mFrameNumber);


    }
}