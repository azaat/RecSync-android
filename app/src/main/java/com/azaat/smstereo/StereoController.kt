package com.azaat.smstereo

import android.util.Log
import java.io.File

/**
 * Should be instantiated only on leader;
 * handles events associated with stereo processing
 */
class StereoController () : ImagePairAvailableListener{
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

    public fun onStereoRecordingSessionComplete(recordedDataDir: File) {
        if (stereoControllerState == StereoControllerStates.CALIBRATING) {
            runCalibration(recordedDataDir)
        }
    }

    private fun runCalibration(calibrationDataDir: File) {

    }

    public override fun onImagePairAvailable(
        clientFrameTimestampNs: Long,
        leaderFrameTimestampNs: Long
    ) {
        Log.d(TAG, "$clientFrameTimestampNs $leaderFrameTimestampNs")
    }

    companion object {
        const val TAG = "StereoController"

        enum class StereoControllerStates {
            CALIBRATING, CALIBRATED, UNCALIBRATED, RECORDING_WITH_DEPTH
        }
    }

}
