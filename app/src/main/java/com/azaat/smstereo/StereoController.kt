package com.azaat.smstereo

import android.util.Log
import android.util.Size
import com.azaat.smstereo.depthestimation.StereoDepth
import com.googleresearch.capturesync.FileOperations
import com.googleresearch.capturesync.SynchronizedFrame
import java.io.File
import java.io.FileInputStream

/**
 * Should be instantiated only on leader;
 * handles events associated with stereo processing
 */
class StereoController (val cameraView: CameraView, val fileOperations: FileOperations, yuvOutputSize: Size) : OnImagePairAvailableListener {
    /**
     * TODO: modifications in UI based on the state of StereoController?
     */
    var stereoControllerState = StereoControllerStates.UNCALIBRATED
        private set
    val stereoDepth: StereoDepth = StereoDepth(
        FileInputStream("${fileOperations.getExternalDir()}/calib_params.xml").readBytes(),
        yuvOutputSize
    )

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

    public fun onStereoRecordingSessionComplete(recordedDataDir: File) {
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
        Log.d(TAG, "${leaderFrame.timestampNs} ${clientFrame.timestampNs}")
        val depthBitmap = stereoDepth.onImagePairAvailable(clientFrame, leaderFrame)
        cameraView.displayFrame(depthBitmap)
    }

    companion object {
        const val TAG = "StereoController"

        enum class StereoControllerStates {
            CALIBRATING, CALIBRATED, UNCALIBRATED, RECORDING_WITH_DEPTH
        }
    }

}
