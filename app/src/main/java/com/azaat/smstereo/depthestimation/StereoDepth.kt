package com.azaat.smstereo.depthestimation

import com.azaat.smstereo.OnImagePairAvailableListener
import com.googleresearch.capturesync.SynchronizedFrame
import me.timpushkin.sgbm_android_lib.SgbmAndroidLib.getDepthMap
import me.timpushkin.sgbm_android_lib.SgbmAndroidLib.loadCalibrationParams

// TODO: move constants or switch to dynamic values
private const val WIDTH = 640
private const val HEIGHT = 360

class StereoDepth : OnImagePairAvailableListener {
    override fun onImagePairAvailable(
        clientFrame: SynchronizedFrame,
        leaderFrame: SynchronizedFrame
    ) {
        // TODO: left-right disambiguation
        val depthBitmap = depthArrayToBitmap(
            getDepthMap(clientFrame.byteArray, leaderFrame.byteArray, WIDTH, HEIGHT),
            WIDTH,
            HEIGHT
        )
    }

}
