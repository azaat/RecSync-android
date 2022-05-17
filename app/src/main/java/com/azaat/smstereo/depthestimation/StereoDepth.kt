package com.azaat.smstereo.depthestimation

import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import com.googleresearch.capturesync.SynchronizedFrame
import me.timpushkin.sgbmandroid.DepthEstimator
//import me.timpushkin.sgbm_android_lib.SgbmAndroidLib.loadCalibrationParams
import java.io.ByteArrayOutputStream

class StereoDepth(paramsPath: String, val yuvOutputSize: Size) {
    private val depthEstimator : DepthEstimator
    init {
        depthEstimator = DepthEstimator(paramsPath)
    }

    fun onImagePairAvailable(
        clientFrame: SynchronizedFrame,
        leaderFrame: SynchronizedFrame
    ): Bitmap {
        var stream = ByteArrayOutputStream()
        clientFrame.bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        val byteArrayClient: ByteArray = stream.toByteArray()
        stream = ByteArrayOutputStream()
        leaderFrame.bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        val byteArrayLeader: ByteArray = stream.toByteArray()

        val depthArray = depthEstimator.estimateDepth(byteArrayClient, byteArrayLeader)
        val depthBitmap =  depthArrayToBitmap(depthArray)
        return depthBitmap
    }

}
