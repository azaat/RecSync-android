package com.azaat.smstereo

import java.lang.RuntimeException
import kotlin.Throws

class StereoController {
    val isCalibrationDataAvailable: Boolean
        get() = false

    /**
     * Records a stereo sequence, processes recorded frames
     */
    fun runStereoCalibration() {}

    /**
     *
     * @throws RuntimeException
     */
    @Throws(RuntimeException::class)
    fun runStereoRecordingWithDepth() {
        if (!isCalibrationDataAvailable) {
            throw RuntimeException("Calibration data unavailable")
        }
    }

    private fun runStereoRecordingSession() {}
}
