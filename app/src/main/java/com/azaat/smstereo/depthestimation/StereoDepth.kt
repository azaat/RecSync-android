package com.azaat.smstereo.depthestimation

import android.graphics.Bitmap
import android.util.Log
import com.googleresearch.capturesync.SynchronizedFrame
import me.timpushkin.sgbm_android_lib.SgbmAndroidLib.getDepthMap
import me.timpushkin.sgbm_android_lib.SgbmAndroidLib.loadCalibrationParams
import java.io.ByteArrayOutputStream

// TODO: move constants or switch to dynamic values
private const val WIDTH = 720
private const val HEIGHT = 480

class StereoDepth() {
    constructor(params: ByteArray) : this() {
        loadCalibrationParams(params)
    }

    fun onImagePairAvailable(
        clientFrame: SynchronizedFrame,
        leaderFrame: SynchronizedFrame
    ): Bitmap {
        // TODO: left-right disambiguation
        var stream = ByteArrayOutputStream()
        clientFrame.bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        val byteArrayClient: ByteArray = stream.toByteArray()
        stream = ByteArrayOutputStream()
        leaderFrame.bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        val byteArrayLeader: ByteArray = stream.toByteArray()

        val depthBitmap =  depthArrayToBitmap(
            getDepthMap(byteArrayLeader,byteArrayClient, WIDTH, HEIGHT),
            WIDTH,
            HEIGHT
        )
        return depthBitmap
    }

}
