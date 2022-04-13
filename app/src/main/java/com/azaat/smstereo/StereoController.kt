package com.azaat.smstereo

import android.util.Log
import com.googleresearch.capturesync.CameraView
import com.googleresearch.capturesync.SynchronizedFrame
import java.io.File
import java.util.*

/**
 * Should be instantiated only on leader;
 * handles events associated with stereo processing
 */
class StereoController (private val cameraView: CameraView) : ImagePairAvailableListener{
    /**
     * TODO: modifications in UI based on the state of StereoController?
     */
    var stereoControllerState = StereoControllerStates.UNCALIBRATED
        private set

    private val latestFrames: ArrayDeque<SynchronizedFrame> = ArrayDeque()

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
        clientFrameTimestampNs: Long,
        leaderFrameTimestampNs: Long,
        clientFrame: SynchronizedFrame
    ) {
        Log.d(TAG, "$clientFrameTimestampNs $leaderFrameTimestampNs")
        cameraView.displayStreamFrame(clientFrame)
    }


    companion object {
        const val TAG = "StereoController"

        enum class StereoControllerStates {
            CALIBRATING, CALIBRATED, UNCALIBRATED, RECORDING_WITH_DEPTH
        }
    }

}
