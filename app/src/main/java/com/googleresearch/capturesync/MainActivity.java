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

import static android.os.Environment.DIRECTORY_DOCUMENTS;
import static android.os.Environment.DIRECTORY_PICTURES;

import static com.googleresearch.capturesync.VideoHelpers.SUBDIR_NAME;

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraDevice.StateCallback;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaRecorder;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.ar.core.RecordingConfig;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.googleresearch.capturesync.common.helpers.DisplayRotationHelper;
import com.googleresearch.capturesync.common.helpers.TapHelper;
import com.googleresearch.capturesync.common.helpers.TrackingStateHelper;
import com.googleresearch.capturesync.common.rendering.BackgroundRenderer;
import com.googleresearch.capturesync.common.rendering.ObjectRenderer;
import com.googleresearch.capturesync.common.rendering.PlaneRenderer;
import com.googleresearch.capturesync.common.rendering.PointCloudRenderer;
import com.googleresearch.capturesync.softwaresync.CSVLogger;
import com.googleresearch.capturesync.softwaresync.SoftwareSyncLeader;
import com.googleresearch.capturesync.softwaresync.TimeUtils;
import com.googleresearch.capturesync.softwaresync.phasealign.PeriodCalculator;
import com.googleresearch.capturesync.softwaresync.phasealign.PhaseConfig;
import com.googleresearch.capturesync.VideoHelpers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.json.JSONObject;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point.OrientationMode;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.SharedCamera;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


/**
 * Main activity for the libsoftwaresync demo app using the camera 2 API.
 */
public class MainActivity extends Activity
        implements GLSurfaceView.Renderer,
        SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "MainActivity";
    private static final int STATIC_LEN = 15_000;
    private PeriodCalculator periodCalculator;
    // ARCore session that supports camera sharing.
    private Session sharedSession;
    // ARCore shared camera instance, obtained from ARCore session that supports sharing.
    private SharedCamera sharedCamera;
    private CaptureRequest.Builder previewCaptureRequestBuilder;
    private ImageReader yuvReader;
    private ExecutorService saver;
    private View recButton;
    private String lastTimeStamp;

    public int getCurSequence() {
        return curSequence;
    }

    public void setLogger(CSVLogger mLogger) {
        this.mLogger = mLogger;
    }

    public CSVLogger getLogger() {
        return mLogger;
    }

    // Whether the app is currently in AR mode. Initial value determines initial state.
    private boolean arMode = false;
    // Whether the app has just entered non-AR mode.
    private final AtomicBoolean isFirstFrameWithoutArcore = new AtomicBoolean(true);

    private CSVLogger mLogger;
    private DisplayRotationHelper displayRotationHelper;
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
    private TapHelper tapHelper;

    // Whether ARCore is currently active.
    private boolean arcoreActive;

    // Whether the GL surface has been created.
    private boolean surfaceCreated;

    /**
     * Whether an error was thrown during session creation.
     */
    private boolean errorCreatingSession = false;

    // Renderers, see hello_ar_java sample to learn more.
    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
    private final ObjectRenderer virtualObject = new ObjectRenderer();
    private final ObjectRenderer virtualObjectShadow = new ObjectRenderer();
    private final PlaneRenderer planeRenderer = new PlaneRenderer();
    private final PointCloudRenderer pointCloudRenderer = new PointCloudRenderer();
    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private final float[] anchorMatrix = new float[16];
    private static final float[] DEFAULT_COLOR = new float[]{0f, 0f, 0f, 0f};

    // Anchors created from taps, see hello_ar_java sample to learn more.
    private final ArrayList<ColoredAnchor> anchors = new ArrayList<>();
    private int curSequence;

    // GL Surface used to draw camera preview image.
    private GLSurfaceView surfaceView;

    // Ensure GL surface draws only occur when new frames are available.
    protected final AtomicBoolean shouldUpdateSurfaceTexture = new AtomicBoolean(false);

    private boolean permissionsGranted = false;

    // Phase config file to use for phase alignment, configs are located in the raw folder.
    private final int phaseConfigFile = R.raw.default_phaseconfig;

    public MediaRecorder getMediaRecorder() {
        return mediaRecorder;
    }

    private MediaRecorder mediaRecorder = new MediaRecorder();
    private boolean isVideoRecording = false;

    // Camera controls.
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private Handler send2aHandler;
    private CameraManager cameraManager;

    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCharacteristics cameraCharacteristics;

    // Cached camera characteristics.
    private Size viewfinderResolution;
    private Size rawImageResolution;
    private Size yuvImageResolution;

    // Top level UI windows.
    private int lastOrientation = Configuration.ORIENTATION_UNDEFINED;

    // UI controls.
    private Button captureStillButton;
    private Button getPeriodButton;
    private Button phaseAlignButton;
    private SeekBar exposureSeekBar;
    private SeekBar sensitivitySeekBar;
    private TextView statusTextView;
    private TextView sensorExposureTextView;
    private TextView sensorSensitivityTextView;
    private TextView softwaresyncStatusTextView;
    private TextView phaseTextView;

    // Local variables tracking current manual exposure and sensitivity values.
    private long currentSensorExposureTimeNs = seekBarValueToExposureNs(10);
    private int currentSensorSensitivity = seekBarValueToSensitivity(3);

    // High level camera controls.
    private CameraController cameraController;
    private CameraCaptureSession captureSession;

    private VideoHelpers videoHelpers;

    /**
     * Manages SoftwareSync setup/teardown. Since softwaresync should only run when the camera is
     * running, it is instantiated in openCamera() and closed inside closeCamera().
     */
    private SoftwareSyncController softwareSyncController;

    private PhaseAlignController phaseAlignController;
    private int numCaptures;
    private Toast latestToast;
    private Surface surface;

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {

    }


    private static class ColoredAnchor {
        public final Anchor anchor;
        public final float[] color;

        public ColoredAnchor(Anchor a, float[] color4f) {
            this.anchor = a;
            this.color = color4f;
        }
    }


    public Integer getLastVideoSeqId() {
        return videoHelpers.lastVideoSeqId;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "onCreate");
        periodCalculator = new PeriodCalculator();
        videoHelpers = new VideoHelpers();
        checkPermissions();
        if (permissionsGranted) {
            onCreateWithPermission();
        }
    }

    private void onCreateWithPermission() {
        setContentView(R.layout.activity_main);
        send2aHandler = new Handler();
        tapHelper = new TapHelper(this);

        displayRotationHelper = new DisplayRotationHelper(this);
        createUi();
        setupPhaseAlignController();
        arMode = false;


        // GL surface view that renders camera preview image.
        surfaceView = findViewById(R.id.glsurfaceview);
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        surfaceView.setRenderer(this);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        maybeUpdateConfiguration(getResources().getConfiguration());
    }

    private void setupPhaseAlignController() {
        // Set up phase aligner.
        PhaseConfig phaseConfig;
        try {
            phaseConfig = loadPhaseConfigFile();
        } catch (JSONException e) {
            throw new IllegalArgumentException("Error reading JSON file: ", e);
        }
        phaseAlignController = new PhaseAlignController(phaseConfig, this);
    }

    /**
     * Called when "configuration" changes, as defined in the manifest. In our case, when the
     * orientation changes, screen size changes, or keyboard is hidden.
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        maybeUpdateConfiguration(getResources().getConfiguration());
    }

    private void maybeUpdateConfiguration(Configuration newConfig) {
        if (lastOrientation != newConfig.orientation) {
            lastOrientation = newConfig.orientation;
            updateViewfinderLayoutParams();
        }
    }

    /**
     * Resize the SurfaceView to be centered on screen.
     */
    private void updateViewfinderLayoutParams() {
        // displaySize is set by the OS: it's how big the display is.
        Point displaySize = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(displaySize);
        Log.i(TAG, String.format("display resized, now %d x %d", displaySize.x, displaySize.y));

    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        saver = Executors.newSingleThreadExecutor();

        super.onResume(); // Required.
        surfaceView.onResume();
        if (surfaceCreated) {
            openCamera();
            try {
                cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                cacheCameraCharacteristics();
            } catch (CameraAccessException e) {
                Toast.makeText(this, R.string.error_msg_cant_open_camera2, Toast.LENGTH_LONG).show();
                Log.e(TAG, String.valueOf(R.string.error_msg_cant_open_camera2));
                finish();
            } catch (UnavailableApkTooOldException | UnavailableDeviceNotCompatibleException | UnavailableSdkTooOldException | UnavailableArcoreNotInstalledException e) {
                e.printStackTrace();
            }
        }

        displayRotationHelper.onResume();
        startCameraThread();
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        shouldUpdateSurfaceTexture.set(false);
        surfaceView.onPause();
        closeCamera();
        stopCameraThread();
        displayRotationHelper.onPause();
        saver.shutdownNow();

        if (arMode) {
            pauseARCore();
        }
        super.onPause(); // required
    }

    private void pauseARCore() {
        if (arcoreActive) {
            // Pause ARCore.
            sharedSession.pause();
            isFirstFrameWithoutArcore.set(true);
            arcoreActive = false;
        }
    }

    private void startCameraThread() {
        cameraThread = new HandlerThread("CameraThread");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private void stopCameraThread() {
        cameraThread.quitSafely();
        try {
            cameraThread.join();
            cameraThread = null;
            cameraHandler = null;
        } catch (InterruptedException e) {
            Log.e(TAG, "Failed to stop camera thread", e);
        }
    }

    @SuppressLint("MissingPermission")
    private void openCamera() {
        Log.d(TAG, "resumeCamera");

        StateCallback cameraStateCallback =
                new StateCallback() {
                    @Override
                    public void onOpened(CameraDevice openedCameraDevice) {
                        cameraDevice = openedCameraDevice;
                        startSoftwareSync();
                        initCameraController();
                        configureCaptureSession(); // calls startPreview();
                    }

                    @Override
                    public void onDisconnected(CameraDevice cameraDevice) {

                    }

                    @Override
                    public void onError(CameraDevice cameraDevice, int i) {
                    }


                };

        // Make sure that ARCore is installed, up to date, and supported on this device.
        if (!isARCoreSupportedAndUpToDate()) {
            return;
        }

        if (sharedSession == null) {
            try {
                // Create ARCore session that supports camera sharing.
                sharedSession = new Session(this, EnumSet.of(Session.Feature.SHARED_CAMERA));
            } catch (Exception e) {
                Log.e(TAG, "Failed to create ARCore session that supports camera sharing", e);
                errorCreatingSession = true;
                return;
            }

            errorCreatingSession = false;

            // Enable auto focus mode while ARCore is running.
            Config config = sharedSession.getConfig();
            config.setFocusMode(Config.FocusMode.AUTO);

            boolean isDepthSupported = sharedSession.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY);
            Log.d("MROB", isDepthSupported + " depth");

            config.setDepthMode(Config.DepthMode.RAW_DEPTH_ONLY);
            sharedSession.configure(config);
        }

        // Store the ARCore shared camera reference.
        sharedCamera = sharedSession.getSharedCamera();

        // Store the ID of the camera used by ARCore.
        cameraId = sharedSession.getCameraConfig().getCameraId();

        Size desiredCpuImageSize = sharedSession.getCameraConfig().getImageSize();

        yuvReader =
                ImageReader.newInstance(
                        desiredCpuImageSize.getWidth(),
                        desiredCpuImageSize.getHeight(),
                        ImageFormat.YUV_420_888,
                        2
                );

        List<Surface> surfaces = new ArrayList<>();
        surfaces.add(yuvReader.getSurface());
        sharedCamera.setAppSurfaces(this.cameraId, surfaces);

        try {
            // Wrap our callback in a shared camera callback.
            StateCallback wrappedCallback =
                    sharedCamera.createARDeviceStateCallback(cameraStateCallback, cameraHandler);

            // Store a reference to the camera system service.
            cameraManager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);

            // Get the characteristics for the ARCore camera.
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(this.cameraId);

            // Open the camera device using the ARCore wrapped callback.
            cameraManager.openCamera(cameraId, wrappedCallback, cameraHandler);
        } catch (CameraAccessException | IllegalArgumentException | SecurityException e) {
            Log.e(TAG, "Failed to open camera", e);
        }
    }

    public void onTimestampNs(long timestampNs) {
        periodCalculator.onFrameTimestamp(timestampNs);
    }

    /* Set up UI controls and listeners based on if device is currently a leader of client. */
    private void setLeaderClientControls(boolean isLeader) {
        getPeriodButton.setOnClickListener(
                view -> {
                    Log.d(TAG, "Calculating frames period.");

                    FutureTask<Integer> periodTask = new FutureTask<Integer>(
                            () -> {
                                try {
                                    long periodNs = periodCalculator.getPeriodNs();
                                    Log.d(TAG, "Calculated period: " + periodNs);
                                    if (latestToast != null) {
                                        latestToast.cancel();
                                    }
                                    latestToast =
                                            Toast.makeText(
                                                    this,
                                                    "Calculated period: " + periodNs,
                                                    Toast.LENGTH_LONG);
                                    latestToast.show();
                                    phaseAlignController.setPeriodNs(periodNs);
                                } catch (InterruptedException e) {
                                    Log.d(TAG, "Failed calculating period");
                                    e.printStackTrace();
                                }
                                return 0;
                            }
                    );
                    periodTask.run();
                }
        );

        if (isLeader) {
            // Leader, all controls visible and set.
            captureStillButton.setVisibility(View.VISIBLE);
            phaseAlignButton.setVisibility(View.VISIBLE);
            getPeriodButton.setVisibility(View.VISIBLE);
            exposureSeekBar.setVisibility(View.VISIBLE);
            sensitivitySeekBar.setVisibility(View.VISIBLE);
            recButton.setVisibility(View.VISIBLE);

            captureStillButton.setOnClickListener(
                    view -> {
                        if (isVideoRecording) {
                            stopVideo();
                            ((SoftwareSyncLeader) softwareSyncController.softwareSync)
                                    .broadcastRpc(
                                            SoftwareSyncController.METHOD_STOP_RECORDING,
                                            "0");

                        } else {
                            startVideo(false);
                            ((SoftwareSyncLeader) softwareSyncController.softwareSync)
                                    .broadcastRpc(
                                            SoftwareSyncController.METHOD_START_RECORDING,
                                            "0");
                        }
                    });

            phaseAlignButton.setOnClickListener(
                    view -> {
                        Log.d(TAG, "Broadcasting phase alignment request.");
                        // Request phase alignment on all devices.
                        ((SoftwareSyncLeader) softwareSyncController.softwareSync)
                                .broadcastRpc(SoftwareSyncController.METHOD_DO_PHASE_ALIGN, "");
                    });

            exposureSeekBar.setOnSeekBarChangeListener(
                    new OnSeekBarChangeListener() {
                        @SuppressLint("SetTextI18n")
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                            currentSensorExposureTimeNs = seekBarValueToExposureNs(value);
                            sensorExposureTextView.setText(
                                    "Exposure: " + prettyExposureValue(currentSensorExposureTimeNs));
                            Log.i(
                                    TAG,
                                    "Exposure Seekbar "
                                            + value
                                            + " to set exposure "
                                            + currentSensorExposureTimeNs
                                            + " : "
                                            + prettyExposureValue(currentSensorExposureTimeNs));
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {
                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {
                            // Do it immediately on leader for immediate feedback, but doesn't update clients
                            // without
                            // clicking the 2A button.
                            startPreview();
                            scheduleBroadcast2a();
                        }
                    });

            sensitivitySeekBar.setOnSeekBarChangeListener(
                    new OnSeekBarChangeListener() {
                        @SuppressLint("SetTextI18n")
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                            currentSensorSensitivity = seekBarValueToSensitivity(value);
                            sensorSensitivityTextView.setText("Sensitivity: " + currentSensorSensitivity);
                            Log.i(
                                    TAG,
                                    "Sensitivity Seekbar "
                                            + value
                                            + " to set sensitivity "
                                            + currentSensorSensitivity);
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {
                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {
                            // Do it immediately on leader for immediate feedback, but doesn't update clients
                            // without
                            // clicking the 2A button.
                            startPreview();
                            scheduleBroadcast2a();
                        }
                    });
        } else {
            // Client. All controls invisible.
            captureStillButton.setVisibility(View.INVISIBLE);
            recButton.setVisibility(View.VISIBLE);
            phaseAlignButton.setVisibility(View.INVISIBLE);
            getPeriodButton.setVisibility(View.VISIBLE);
            exposureSeekBar.setVisibility(View.INVISIBLE);
            sensitivitySeekBar.setVisibility(View.INVISIBLE);

            recButton.setOnClickListener(
                    view -> {
                        if (isVideoRecording) {
                            stopVideo();
                            isVideoRecording = false;

                        } else {
                            startVideo(false);
                            isVideoRecording = true;
                        }
                    });

            captureStillButton.setOnClickListener(null);
            phaseAlignButton.setOnClickListener(null);
            exposureSeekBar.setOnSeekBarChangeListener(null);
            sensitivitySeekBar.setOnSeekBarChangeListener(null);
        }
    }

    private void startSoftwareSync() {
        // Start softwaresync, close it first if it's already running.
        if (softwareSyncController != null) {
            softwareSyncController.close();
            softwareSyncController = null;
        }
        try {
            softwareSyncController =
                    new SoftwareSyncController(this, phaseAlignController, softwaresyncStatusTextView);
            setLeaderClientControls(softwareSyncController.isLeader());
        } catch (IllegalStateException e) {
            // If wifi is disabled, start pick wifi activity.
            Log.e(
                    TAG,
                    "Couldn't start SoftwareSync due to " + e + ", requesting user pick a wifi network.");
            finish(); // Close current app, expect user to restart.
            startActivity(new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK));
        }
    }

    private PhaseConfig loadPhaseConfigFile() throws JSONException {
        // Load phase config file and pass to phase aligner.

        JSONObject json;
        try {
            InputStream inputStream = getResources().openRawResource(phaseConfigFile);
            byte[] buffer = new byte[inputStream.available()];
            //noinspection ResultOfMethodCallIgnored
            inputStream.read(buffer);
            inputStream.close();
            json = new JSONObject(new String(buffer, StandardCharsets.UTF_8));
        } catch (JSONException | IOException e) {
            throw new IllegalArgumentException("Error reading JSON file: ", e);
        }
        return PhaseConfig.parseFromJSON(json);
    }

    private void closeCamera() {
        stopPreview();
        captureSession = null;
//        surface.release();
        if (cameraController != null) {
            cameraController.close();
            cameraController = null;
        }

        if (cameraDevice != null) {
            Log.d(TAG, "Closing camera...");
            cameraDevice.close();
            Log.d(TAG, "Camera closed.");
        }

        // Close softwaresync whenever camera is stopped.
        if (softwareSyncController != null) {
            softwareSyncController.close();
            softwareSyncController = null;
        }
    }

    /**
     * Gathers useful camera characteristics like available resolutions and cache them so we don't
     * have to query the CameraCharacteristics struct again.
     */
    private void cacheCameraCharacteristics() throws CameraAccessException, UnavailableSdkTooOldException, UnavailableDeviceNotCompatibleException, UnavailableArcoreNotInstalledException, UnavailableApkTooOldException {
        // Create an ARCore session that supports camera sharing.
        sharedSession = new Session(this, EnumSet.of(Session.Feature.SHARED_CAMERA));
        cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
    }

    public void notifyCapturing(String name) {
        runOnUiThread(
                () -> {
                    if (latestToast != null) {
                        latestToast.cancel();
                    }
                    latestToast = Toast.makeText(this, "Capturing " + name + "...", Toast.LENGTH_SHORT);
                    latestToast.show();
                });
    }

    public void notifyCaptured(String name) {
        numCaptures++;
        runOnUiThread(
                () -> {
                    if (latestToast != null) {
                        latestToast.cancel();
                    }
                    latestToast = Toast.makeText(this, "Captured " + name, Toast.LENGTH_LONG);
                    latestToast.show();
                    statusTextView.setText(String.format("%d captures", numCaptures));
                });
    }

    public void deleteUnusedVideo() {
        videoHelpers.deleteUnusedVideo();
    }

    // GL surface created callback. Will be called on the GL thread.
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        surfaceCreated = true;

        // Set GL clear color to black.
        GLES20.glClearColor(0f, 0f, 0f, 1.0f);

        // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
        try {
            // Create the camera preview image texture. Used in non-AR and AR mode.
            backgroundRenderer.createOnGlThread(this);
            planeRenderer.createOnGlThread(this, "models/trigrid.png");
            pointCloudRenderer.createOnGlThread(this);

            virtualObject.createOnGlThread(this, "models/andy.obj", "models/andy.png");
            virtualObject.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

            virtualObjectShadow.createOnGlThread(
                    this, "models/andy_shadow.obj", "models/andy_shadow.png");
            virtualObjectShadow.setBlendMode(ObjectRenderer.BlendMode.Shadow);
            virtualObjectShadow.setMaterialProperties(1.0f, 0.0f, 0.0f, 1.0f);

            openCamera();
        } catch (IOException e) {
            Log.e(TAG, "Failed to read an asset file", e);
        }
    }

    // GL surface changed callback. Will be called on the GL thread.
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        displayRotationHelper.onSurfaceChanged(width, height);

    }

    // GL draw callback. Will be called each frame on the GL thread.
    @Override
    public void onDrawFrame(GL10 gl) {
        // Use the cGL clear color specified in onSurfaceCreated() to erase the GL surface.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (!shouldUpdateSurfaceTexture.get()) {
            // Not ready to draw.
            return;
        }

        // Handle display rotations.
        displayRotationHelper.updateSessionIfNeeded(sharedSession);

        try {
            if (arMode) {
                onDrawFrameARCore();
            } else {
                onDrawFrameCamera2();
            }
        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }
    }

    // Draw frame when in non-AR mode. Called on the GL thread.
    public void onDrawFrameCamera2() {
        SurfaceTexture texture = sharedCamera.getSurfaceTexture();

        // ARCore may attach the SurfaceTexture to a different texture from the camera texture, so we
        // need to manually reattach it to our desired texture.
        if (isFirstFrameWithoutArcore.getAndSet(false)) {
            try {
                texture.detachFromGLContext();
            } catch (RuntimeException e) {
                // Ignore if fails, it may not be attached yet.
            }
            texture.attachToGLContext(backgroundRenderer.getTextureId());
        }

        // Update the surface.
        texture.updateTexImage();

        // Account for any difference between camera sensor orientation and display orientation.
        int rotationDegrees = displayRotationHelper.getCameraSensorToDisplayRotation(cameraId);

        // Determine size of the camera preview image.
        Size size = sharedSession.getCameraConfig().getTextureSize();

        // Determine aspect ratio of the output GL surface, accounting for the current display rotation
        // relative to the camera sensor orientation of the device.
        float displayAspectRatio =
                displayRotationHelper.getCameraSensorRelativeViewportAspectRatio(cameraId);

        // Render camera preview image to the GL surface.
        backgroundRenderer.draw(size.getWidth(), size.getHeight(), displayAspectRatio, rotationDegrees);
    }

    // Draw frame when in AR mode. Called on the GL thread.
    public void onDrawFrameARCore() throws CameraNotAvailableException {
        if (!arcoreActive) {
            // ARCore not yet active, so nothing to draw yet.
            return;
        }

        if (errorCreatingSession) {
            // Session not created, so nothing to draw.
            return;
        }

        // Perform ARCore per-frame update.
        Frame frame = sharedSession.update();
        Camera camera = frame.getCamera();


        // Handle screen tap.
        handleTap(frame, camera);

        // If frame is ready, render camera preview image to the GL surface.
        backgroundRenderer.draw(frame);

        // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
        trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

        // If not tracking, don't draw 3D objects.
        if (camera.getTrackingState() == TrackingState.PAUSED) {
            return;
        }

        // Get projection matrix.
        float[] projmtx = new float[16];
        camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

        // Get camera matrix and draw.
        float[] viewmtx = new float[16];
        camera.getViewMatrix(viewmtx, 0);

        // Compute lighting from average intensity of the image.
        // The first three components are color scaling factors.
        // The last one is the average pixel intensity in gamma space.
        final float[] colorCorrectionRgba = new float[4];
        frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

        // Visualize tracked points.
        // Use try-with-resources to automatically release the point cloud.
        try (PointCloud pointCloud = frame.acquirePointCloud()) {
            pointCloudRenderer.update(pointCloud);
            pointCloudRenderer.draw(viewmtx, projmtx);
        }
        long unSyncTimestampNs = frame.getAndroidCameraTimestamp();

        long synchronizedTimestampNs =
                this.softwareSyncController.softwareSync.leaderTimeForLocalTimeNs(
                        unSyncTimestampNs);
        try {
            mLogger.logLine(String.valueOf(synchronizedTimestampNs));
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            Image depthImage = frame.acquireRawDepthImage();
            Log.d("MROB", "Depth should be saved");

            Image.Plane plane = depthImage.getPlanes()[0];
            int width = depthImage.getWidth();
            int height = depthImage.getHeight();
            int format = depthImage.getFormat();

            Log.d("MROB", "W " + width + " H " + height + " format " + format);

            ByteBuffer buffer = plane.getBuffer().order(ByteOrder.nativeOrder());
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);

            depthImage.close();
            saver.submit(
                    () -> {
                        Log.d("MROB", "Saving depth");
                        File sdcard = Environment.getExternalStorageDirectory();
                        try {
                            Path dir = Paths.get(sdcard.getAbsolutePath(), SUBDIR_NAME);
                            File file = new File(dir.toFile(), synchronizedTimestampNs + ".txt");

                            FileOutputStream fos = new FileOutputStream(file);
                            fos.write(data);
                            fos.close();
                        } catch (IOException e) {
                            Log.d("MROB", "Couldn't save depth");

                            e.printStackTrace();
                        }
                        // TODO: organize this mess with exceptions
                    }
            );
        } catch (NotYetAvailableException e) {
            if (!(camera.getTrackingState() == TrackingState.TRACKING)) {
                Log.d("MROB", "Not tracking");
                Log.d("MROB", camera.getTrackingFailureReason().name());
            }
            boolean isDepthSupported = sharedSession.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY);
        }

        // Visualize planes.
        planeRenderer.drawPlanes(
                sharedSession.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projmtx);

        // Visualize anchors created by touch.
        float scaleFactor = 1.0f;
        for (ColoredAnchor coloredAnchor : anchors) {
            if (coloredAnchor.anchor.getTrackingState() != TrackingState.TRACKING) {
                continue;
            }
            // Get the current pose of an Anchor in world space. The Anchor pose is updated
            // during calls to sharedSession.update() as ARCore refines its estimate of the world.
            coloredAnchor.anchor.getPose().toMatrix(anchorMatrix, 0);

            // Update and draw the model and its shadow.
            virtualObject.updateModelMatrix(anchorMatrix, scaleFactor);
            virtualObjectShadow.updateModelMatrix(anchorMatrix, scaleFactor);
            virtualObject.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
            virtualObjectShadow.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
        }
    }

    // Handle only one tap per frame, as taps are usually low frequency compared to frame rate.
    private void handleTap(Frame frame, Camera camera) {
        MotionEvent tap = tapHelper.poll();

        if (tap != null && camera.getTrackingState() == TrackingState.TRACKING) {
            for (HitResult hit : frame.hitTest(tap)) {
                // Check if any plane was hit, and if it was hit inside the plane polygon
                Trackable trackable = hit.getTrackable();
                // Creates an anchor if a plane or an oriented point was hit.
                if ((trackable instanceof Plane
                        && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())
                        && (PlaneRenderer.calculateDistanceToPlane(hit.getHitPose(), camera.getPose()) > 0))
                        || (trackable instanceof com.google.ar.core.Point
                        && ((com.google.ar.core.Point) trackable).getOrientationMode()
                        == OrientationMode.ESTIMATED_SURFACE_NORMAL)) {
                    // Hits are sorted by depth. Consider only closest hit on a plane or oriented point.
                    // Cap the number of objects created. This avoids overloading both the
                    // rendering system and ARCore.
                    if (anchors.size() >= 20) {
                        anchors.get(0).anchor.detach();
                        anchors.remove(0);
                    }

                    // Assign a color to the object for rendering based on the trackable type
                    // this anchor attached to. For AR_TRACKABLE_POINT, it's blue color, and
                    // for AR_TRACKABLE_PLANE, it's green color.
                    float[] objColor;
                    if (trackable instanceof com.google.ar.core.Point) {
                        objColor = new float[]{66.0f, 133.0f, 244.0f, 255.0f};
                    } else if (trackable instanceof Plane) {
                        objColor = new float[]{139.0f, 195.0f, 74.0f, 255.0f};
                    } else {
                        objColor = DEFAULT_COLOR;
                    }

                    // Adding an Anchor tells ARCore that it should track this position in
                    // space. This anchor is created on the Plane to place the 3D model
                    // in the correct position relative both to the world and to the plane.
                    anchors.add(new ColoredAnchor(hit.createAnchor(), objColor));
                    break;
                }
            }
        }
    }


    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum(
                    (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    public Handler getCameraHandler() {
        return cameraHandler;
    }


    // Repeating camera capture session capture callback.
    private final CameraCaptureSession.CaptureCallback cameraCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(
                        @NonNull CameraCaptureSession session,
                        @NonNull CaptureRequest request,
                        @NonNull TotalCaptureResult result) {
                    shouldUpdateSurfaceTexture.set(true);
                }

                @Override
                public void onCaptureBufferLost(
                        @NonNull CameraCaptureSession session,
                        @NonNull CaptureRequest request,
                        @NonNull Surface target,
                        long frameNumber) {
                    Log.e(TAG, "onCaptureBufferLost: " + frameNumber);
                }

                @Override
                public void onCaptureFailed(
                        @NonNull CameraCaptureSession session,
                        @NonNull CaptureRequest request,
                        @NonNull CaptureFailure failure) {
                    Log.e(TAG, "onCaptureFailed: " + failure.getFrameNumber() + " " + failure.getReason());
                }

                @Override
                public void onCaptureSequenceAborted(
                        @NonNull CameraCaptureSession session, int sequenceId) {
                    Log.e(TAG, "onCaptureSequenceAborted: " + sequenceId + " " + session);
                }
            };


    public void injectFrame(long desiredExposureTimeNs) {
        try {
            CaptureRequest.Builder builder =
                    cameraController
                            .getRequestFactory()
                            .makeFrameInjectionRequest(
                                    desiredExposureTimeNs, cameraController.getOutputSurfaces());
            captureSession.capture(
                    builder.build(), cameraCaptureCallback, cameraHandler);
        } catch (CameraAccessException e) {
            throw new IllegalStateException("Camera capture failure during frame injection.", e);
        }
    }


    /**
     * Create {@link #cameraController}, and subscribe to status change events.
     */
    private void initCameraController() {
        cameraController =
                new CameraController(
                        cameraCharacteristics,
                        yuvReader,
                        phaseAlignController,
                        this,
                        softwareSyncController.softwareSync);
    }

    private void configureCaptureSession() {
        Log.d(TAG, "Creating capture session.");

        if (cameraController.getOutputSurfaces().isEmpty()) {
            Log.e(TAG, "No output surfaces found.");
        }

        Log.d(TAG, "Outputs " + cameraController.getOutputSurfaces());

        try {
            CameraCaptureSession.StateCallback sessionCallback =
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            Log.d(TAG, "Camera capture session configured.");
                            captureSession = cameraCaptureSession;
                            if (arMode) {
                                startPreview();
                                // Note, resumeARCore() must be called in onActive(), not here.
                            } else {
                                // Calls `setRepeatingCaptureRequest()`.
                                resumeCamera2();
                            }
                        }

                        @Override
                        public void onActive(@NonNull CameraCaptureSession session) {
                            Log.d(TAG, "Camera capture session active.");
                            if (arMode && !arcoreActive) {
                                resumeARCore();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                            Log.d(TAG, "camera capture configure failed.");
                        }
                    };
//            sharedSession.setCameraTextureName(backgroundRenderer.getTextureId());
//            sharedCamera.getSurfaceTexture().setOnFrameAvailableListener(this);
            sharedSession.setCameraTextureName(backgroundRenderer.getTextureId());
            sharedCamera.getSurfaceTexture().setOnFrameAvailableListener(this);
            // Create an ARCore compatible capture request using `TEMPLATE_RECORD`.
            previewCaptureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

            // Build surfaces list, starting with ARCore provided surfaces.
            List<Surface> surfaceList = sharedCamera.getArCoreSurfaces();

            surfaceList.add(yuvReader.getSurface());

            // Add ARCore surfaces and CPU image surface targets.
            for (Surface surface : surfaceList) {
                previewCaptureRequestBuilder.addTarget(surface);
            }

//            previewCaptureRequestBuilder.setTag(new ImageMetadataSynchronizer.CaptureRequestTag(Collections.singletonList(0), null));
            // Wrap our callback in a shared camera callback.
            CameraCaptureSession.StateCallback wrappedCallback =
                    sharedCamera.createARSessionStateCallback(sessionCallback, cameraHandler);

            // Create camera capture session for camera preview using ARCore wrapped callback.
            cameraDevice.createCaptureSession(surfaceList, wrappedCallback, cameraHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Unable to reconfigure capture request", e);
        }
    }

    private void startPreview(boolean wantAutoExp) {
        Log.d(TAG, "Starting preview.");

        try {
            captureSession.setRepeatingRequest(
                    previewCaptureRequestBuilder.build(),
                    cameraCaptureCallback,
                    cameraHandler);

        } catch (CameraAccessException e) {
            Log.w(TAG, "Unable to create preview.");
        }
    }

    private void startPreview() {
        startPreview(false);
    }

    public void setVideoRecording(boolean videoRecording) {
        isVideoRecording = videoRecording;
    }

    public boolean isVideoRecording() {
        return isVideoRecording;
    }

    public void startVideo(boolean wantAutoExp) {
        Log.d(TAG, "Starting video.");
        Toast.makeText(this, "Started recording video", Toast.LENGTH_LONG).show();

        try {
            String path = getOutputMediaFilePath();
            Uri destination = Uri.fromFile(new File(path));
            RecordingConfig recordingConfig =
                    new RecordingConfig(sharedSession)
                            .setMp4DatasetUri(destination)
                            .setAutoStopOnPause(true);

            String filename = lastTimeStamp + ".csv";

            // Creates frame timestamps logger
            try {
                mLogger = new CSVLogger(SUBDIR_NAME, filename, this);
            } catch (IOException e) {
                e.printStackTrace();
            }

            sharedSession.startRecording(recordingConfig);

            arMode = true;
            resumeARCore();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Create directory and return file
     * returning video file
     */
    private String getOutputMediaFilePath() throws IOException {
        File sdcard = Environment.getExternalStorageDirectory();

        Path dir = Files.createDirectories(Paths.get(sdcard.getAbsolutePath(), SUBDIR_NAME, "VID"));

        lastTimeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date());
        String mediaFile;
        mediaFile = dir.toString() + File.separator + "VID_" + lastTimeStamp + ".mp4";
        return mediaFile;

    }


    public void stopVideo() {
        // Switch to preview again
        arMode = false;
        pauseARCore();
        resumeCamera2();
        mLogger.close();
    }

    private void resumeCamera2() {
        startPreview();
        sharedCamera.getSurfaceTexture().setOnFrameAvailableListener(this);
    }

    private void resumeARCore() {
        if (sharedSession == null) {
            return;
        }

        if (!arcoreActive) {
            try {
                // To avoid flicker when resuming ARCore mode inform the renderer to not suppress rendering
                // of the frames with zero timestamp.
                backgroundRenderer.suppressTimestampZeroRendering(false);
                // Resume ARCore.
                sharedSession.resume();
                arcoreActive = true;

                // Set capture session callback while in AR mode.
                sharedCamera.setCaptureCallback(cameraCaptureCallback,
                        cameraHandler);
            } catch (CameraNotAvailableException e) {
                Log.e(TAG, "Failed to resume ARCore session", e);
                return;
            }
        }
    }

    private void stopPreview() {
        Log.d(TAG, "Stopping preview.");
        if (captureSession == null) {
            return;
        }
        try {
            captureSession.stopRepeating();
            Log.d(TAG, "Done: session is now ready.");
        } catch (CameraAccessException e) {
            Log.d(TAG, "Could not stop repeating.");
        }
    }

    //!-------------END OF CAMERA2-RELATED STUFF

    private void createUi() {
        Window appWindow = getWindow();
        appWindow.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // Disable sleep / screen off.
        appWindow.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//
//        // Create the SurfaceView.
//        surfaceView = findViewById(R.id.viewfinder_surface_view);

        // TextViews.
        statusTextView = findViewById(R.id.status_text);
        softwaresyncStatusTextView = findViewById(R.id.softwaresync_text);
        sensorExposureTextView = findViewById(R.id.sensor_exposure);
        sensorSensitivityTextView = findViewById(R.id.sensor_sensitivity);
        phaseTextView = findViewById(R.id.phase);

        // Controls.
        captureStillButton = findViewById(R.id.capture_still_button);
        recButton = findViewById(R.id.rec_button);
        phaseAlignButton = findViewById(R.id.phase_align_button);
        getPeriodButton = findViewById(R.id.get_period_button);

        exposureSeekBar = findViewById(R.id.exposure_seekbar);
        sensitivitySeekBar = findViewById(R.id.sensitivity_seekbar);
        sensorExposureTextView.setText("Exposure: " + prettyExposureValue(currentSensorExposureTimeNs));
        sensorSensitivityTextView.setText("Sensitivity: " + currentSensorSensitivity);
    }

    private void scheduleBroadcast2a() {
        send2aHandler.removeCallbacks(null); // Replace delayed callback with latest 2a values.
        send2aHandler.postDelayed(
                () -> {
                    Log.d(TAG, "Broadcasting current 2A values.");
                    String payload =
                            String.format("%d,%d", currentSensorExposureTimeNs, currentSensorSensitivity);
                    // Send 2A values to all devices
                    ((SoftwareSyncLeader) softwareSyncController.softwareSync)
                            .broadcastRpc(SoftwareSyncController.METHOD_SET_2A, payload);
                },
                500);
    }

    void set2aAndUpdatePreview(long sensorExposureTimeNs, int sensorSensitivity) {
        currentSensorExposureTimeNs = sensorExposureTimeNs;
        currentSensorSensitivity = sensorSensitivity;
        sensorExposureTextView.setText("Exposure: " + prettyExposureValue(currentSensorExposureTimeNs));
        sensorSensitivityTextView.setText("Sensitivity: " + currentSensorSensitivity);
        Log.i(
                TAG,
                String.format(
                        " Updating 2A to Exposure %d (%s), Sensitivity %d",
                        currentSensorExposureTimeNs,
                        prettyExposureValue(currentSensorExposureTimeNs),
                        currentSensorSensitivity));
        startPreview();
    }

    void updatePhaseTextView(long phaseErrorNs) {
        phaseTextView.setText(
                String.format("Phase Error: %.2f ms", TimeUtils.nanosToMillis((double) phaseErrorNs)));
    }

    private long seekBarValueToExposureNs(int value) {
        // Convert 0-10 values ranging from 1/32 to 1/16,000 of a second.
        int[] steps = {32, 60, 125, 250, 500, 1000, 2000, 4000, 8000, 12000, 16000};
        int denominator = steps[10 - value];
        double exposureSec = 1. / denominator;
        return (long) (exposureSec * 1_000_000_000);
    }

    private String prettyExposureValue(long exposureNs) {
        return String.format("1/%.0f", 1. / TimeUtils.nanosToSeconds((double) exposureNs));
    }

    private int seekBarValueToSensitivity(int value) {
        // Convert 0-10 values to 0-800 sensor sensitivities.
        return (value * 800) / 10;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            if (Constants.USE_FULL_SCREEN_IMMERSIVE) {
                findViewById(android.R.id.content)
                        .setSystemUiVisibility(
                                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
        }
    }

    private void checkPermissions() {
        List<String> requests = new ArrayList<>(3);

        if (checkSelfPermission(permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requests.add(permission.CAMERA);
        }
        if (checkSelfPermission(permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requests.add(permission.READ_EXTERNAL_STORAGE);
        }
        if (checkSelfPermission(permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requests.add(permission.WRITE_EXTERNAL_STORAGE);
        }
        if (checkSelfPermission(permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            requests.add(permission.INTERNET);
        }
        if (checkSelfPermission(permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            requests.add(permission.ACCESS_WIFI_STATE);
        }

        if (requests.size() > 0) {
            String[] requestsArray = new String[requests.size()];
            requestsArray = requests.toArray(requestsArray);
            requestPermissions(requestsArray, /*requestCode=*/ 0);
        } else {
            permissionsGranted = true;
        }
    }

    /**
     * Wait for permissions to continue onCreate.
     */
    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        if (grantResults.length < 3) {
            Log.e(TAG, "Wrong number of permissions returned: " + grantResults.length);
            Toast.makeText(this, R.string.error_msg_no_permission, Toast.LENGTH_LONG).show();
            finish();
        }
        for (int grantResult : grantResults) {
            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                permissionsGranted = false;
                Log.e(TAG, "Permission not granted");
                Toast.makeText(this, R.string.error_msg_no_permission, Toast.LENGTH_LONG).show();
                return;
            }
        }

        // All permissions granted. Continue startup.
        onCreateWithPermission();
    }

    @Override
    protected void onDestroy() {
        if (sharedSession != null) {
            // Explicitly close ARCore Session to release native resources.
            // Review the API reference for important considerations before calling close() in apps with
            // more complicated lifecycle requirements:
            // https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Session#close()
            sharedSession.close();
            sharedSession = null;
        }

        super.onDestroy();
    }


    private boolean isARCoreSupportedAndUpToDate() {
        // Make sure ARCore is installed and supported on this device.
        ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this);
        switch (availability) {
            case SUPPORTED_INSTALLED:
                break;
            case SUPPORTED_APK_TOO_OLD:
            case SUPPORTED_NOT_INSTALLED:
                try {
                    // Request ARCore installation or update if needed.
                    ArCoreApk.InstallStatus installStatus =
                            ArCoreApk.getInstance().requestInstall(this, /*userRequestedInstall=*/ true);
                    switch (installStatus) {
                        case INSTALL_REQUESTED:
                            Log.e(TAG, "ARCore installation requested.");
                            return false;
                        case INSTALLED:
                            break;
                    }
                } catch (UnavailableException e) {
                    Log.e(TAG, "ARCore not installed", e);
                    runOnUiThread(
                            () ->
                                    Toast.makeText(
                                            getApplicationContext(), "ARCore not installed\n" + e, Toast.LENGTH_LONG)
                                            .show());
                    finish();
                    return false;
                }
                break;
            case UNKNOWN_ERROR:
            case UNKNOWN_CHECKING:
            case UNKNOWN_TIMED_OUT:
            case UNSUPPORTED_DEVICE_NOT_CAPABLE:
                Log.e(
                        TAG,
                        "ARCore is not supported on this device, ArCoreApk.checkAvailability() returned "
                                + availability);
                runOnUiThread(
                        () ->
                                Toast.makeText(
                                        getApplicationContext(),
                                        "ARCore is not supported on this device, "
                                                + "ArCoreApk.checkAvailability() returned "
                                                + availability,
                                        Toast.LENGTH_LONG)
                                        .show());
                return false;
        }
        return true;
    }
}
