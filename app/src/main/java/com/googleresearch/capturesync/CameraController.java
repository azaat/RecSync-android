/*
 * Copyright 2021 Mobile Robotics Lab. at Skoltech.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.googleresearch.capturesync;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCaptureSession.CaptureCallback;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.googleresearch.capturesync.ImageMetadataSynchronizer.CaptureRequestTag;
import com.googleresearch.capturesync.softwaresync.TimeDomainConverter;
import com.googleresearch.capturesync.softwaresync.TimeUtils;
import com.googleresearch.capturesync.softwaresync.phasealign.PeriodCalculator;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * High level camera controls.
 */
public class CameraController implements ImageReader.OnImageAvailableListener {
    private static final String TAG = "CameraController";

    // Thread on which to receive ImageReader callbacks.
    private HandlerThread imageThread;
    private Handler imageHandler;

    // Thread on which to receive Synchronized results.
    private HandlerThread syncThread;
    private Handler syncHandler;
    private PhaseAlignController phaseAlignController;
    // ImageReaders, which puts the images into
    // their respective queues.
    private final List<ImageReader> imageReaders;

//  private final ImageMetadataSynchronizer imageMetadataSynchronizer;

    private final ResultProcessor resultProcessor;
//
//  public CaptureCallback getSynchronizerCaptureCallback() {
//    return .getCaptureCallback();
//  }


    /**
     * Camera frames come in continuously and are thrown away. When a desired timestamp {@code
     * goalSynchronizedTimestampNs} is set, the first frame with synchronized timestamp at or after
     * the desired timestamp is saved to disk.
     */
    private long goalSynchronizedTimestampNs;

    private String goalOutputDirName;
    private TimeDomainConverter timeDomainConverter;
    private MainActivity context;

    private PeriodCalculator periodCalculator;

    private CaptureRequestFactory requestFactory;

    /**
     * Constructs the high level CameraController object.
     *
     * <p>If {@code rawImageResolution} is not null, it will create an ImageReader for the raw stream
     * and stream frames to it. If {@code yuvImageResolution} is not null, it will create an
     * ImageReader for the yuv stream and stream frames to it. If {@code viewfinderSurface} is not
     * null, it will stream frames to it.
     */
    @SuppressLint("DefaultLocale")
    public CameraController(
            CameraCharacteristics cameraCharacteristics,
            ImageReader imageReader,
            PhaseAlignController phaseAlignController,
            MainActivity context,
            TimeDomainConverter timeDomainConverter
    ) {
        this.timeDomainConverter = timeDomainConverter;
        imageThread = new HandlerThread("ImageThread");
        imageThread.start();
        imageHandler = new Handler(imageThread.getLooper());
        this.phaseAlignController = phaseAlignController;
        syncThread = new HandlerThread("SyncThread");
        syncThread.start();
        syncHandler = new Handler(syncThread.getLooper());
        this.context = context;
        resultProcessor =
                new ResultProcessor(
                        timeDomainConverter, context, Constants.SAVE_JPG_FROM_YUV, Constants.JPG_QUALITY);

        imageReaders = new ArrayList<>();
        final int imageBuffer = 1;
        imageReaders.add(
                imageReader);

        imageReader.setOnImageAvailableListener(
                this, imageHandler
        );

    }

    /* Check if given timestamp is or passed goal timestamp in the synchronized leader time domain. */
    private boolean shouldSaveFrame(long synchronizedTimestampNs) {
        return goalSynchronizedTimestampNs != 0
                && synchronizedTimestampNs >= goalSynchronizedTimestampNs;
    }

    private void resetGoal() {
        goalSynchronizedTimestampNs = 0;
    }

    public List<Surface> getOutputSurfaces() {
        List<Surface> surfaces = new ArrayList<>();
        for (ImageReader reader : imageReaders) {
            surfaces.add(reader.getSurface());
        }
        return surfaces;
    }

    public void configure(CameraDevice device) {
        requestFactory = new CaptureRequestFactory(device);
    }

    public void close() {
//    imageMetadataSynchronizer.close();
        imageReaders.get(0).close();
        imageThread.quitSafely();
        try {
            imageThread.join();
            imageThread = null;
            imageHandler = null;
        } catch (InterruptedException e) {
            Log.e(TAG, "Failed to join imageThread");
        }

        syncThread.quitSafely();
        try {
            syncThread.join();
            syncThread = null;
            syncHandler = null;
        } catch (InterruptedException e) {
            Log.e(TAG, "Failed to join syncThread");
        }
    }

    public CaptureRequestFactory getRequestFactory() {
        return requestFactory;
    }

    // Input desired capture time in leader time domain (first frame that >= that timestamp).
    public void setUpcomingCaptureStill(long desiredSynchronizedCaptureTimeNs) {
        goalOutputDirName = getTimeStr(desiredSynchronizedCaptureTimeNs);
        goalSynchronizedTimestampNs = desiredSynchronizedCaptureTimeNs;
        Log.i(
                TAG,
                String.format(
                        "Request sync still at %d to %s", goalSynchronizedTimestampNs, goalOutputDirName));
    }

    private String getTimeStr(long timestampNs) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS");
        simpleDateFormat.setTimeZone(TimeZone.getDefault());
        return simpleDateFormat.format(timestampNs / 1_000_000L);
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        Image image = reader.acquireLatestImage();
        if (image == null) {
            Log.w(TAG, "onImageAvailable: Skipping null image.");
            return;
        }

        long unSyncTimestampNs = image.getTimestamp();
        context.onTimestampNs(unSyncTimestampNs);
        long synchronizedTimestampNs =
                timeDomainConverter.leaderTimeForLocalTimeNs(
                        unSyncTimestampNs);

        long phaseNs = phaseAlignController.updateCaptureTimestamp(synchronizedTimestampNs);

        image.close();

    }
}