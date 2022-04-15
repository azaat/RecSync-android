package com.azaat.smstereo

import android.util.Log
import com.azaat.smstereo.depthestimation.StereoDepth
import com.googleresearch.capturesync.CameraView
import com.googleresearch.capturesync.SoftwareSyncController
import com.googleresearch.capturesync.SynchronizedFrame
import java.io.File
import java.util.*

/**
 * Should be instantiated only on leader;
 * handles events associated with stereo processing
 */

class StereoController @JvmOverloads constructor(
    private val cameraView: CameraView,
    private val softwareSyncController: SoftwareSyncController,
    private val stereoDepth: StereoDepth = StereoDepth()
) : OnImagePairAvailableListener by stereoDepth,
    OnStreamImageAvailableListener by softwareSyncController.basicStream {
    /**
     * TODO: modifications in UI based on the state of StereoController?
     */
    var stereoControllerState = StereoControllerStates.UNCALIBRATED
        private set


    /**
     * Records a stereo sequence, processes recorded frames
     */
    fun runStereoCalibration() {

    }

    /**
     *
     * @throws RuntimeException
     */
    @Throws(RuntimeException::class)
    fun runStereoRecordingWithDepth() {


        if (stereoControllerState == StereoControllerStates.UNCALIBRATED) {
            throw RuntimeException("Calibration data unavailable")
        }
    }

    private fun runStereoRecordingSession() {}

    fun onStereoRecordingSessionComplete(recordedDataDir: File) {
        if (stereoControllerState == StereoControllerStates.CALIBRATING) {
            runCalibration(recordedDataDir)
        }
    }

    private fun runCalibration(calibrationDataDir: File) {

    }

    override fun onImagePairAvailable(
        clientFrame: SynchronizedFrame,
        leaderFrame: SynchronizedFrame
    ) {
        Log.d(TAG, "${clientFrame.timestampNs} ${leaderFrame.timestampNs}")
        cameraView.displayStreamFrame(clientFrame)
        stereoDepth.onImagePairAvailable(clientFrame, leaderFrame)
    }


    companion object {
        const val TAG = "StereoController"

        enum class StereoControllerStates {
            CALIBRATING, CALIBRATED, UNCALIBRATED, RECORDING_WITH_DEPTH
        }
    }

}
