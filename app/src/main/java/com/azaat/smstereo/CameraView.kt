package com.azaat.smstereo

import android.graphics.Bitmap

interface CameraView {
    fun displayFrame(frameBitmap: Bitmap)
}
