package com.azaat.smstereo;

public class StereoController {

    public boolean isCalibrationDataAvailable() {
        return false;
    }

    /**
     * Records a stereo sequence, processes recorded frames
     */
    public void runStereoCalibration() {

    }

    /**
     *
     * @throws RuntimeException
     */
    public void runStereoRecordingWithDepth() throws RuntimeException {
        if (!isCalibrationDataAvailable()) {
            throw new RuntimeException("Calibration data unavailable");
        }
    }

    private void runStereoRecordingSession() {

    }

}
