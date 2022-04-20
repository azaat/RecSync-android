package com.azaat.smstereo

import android.graphics.Bitmap

interface OnDepthImageAvailableListener {
    fun onDepthImageAvailable(depthImage: Bitmap)
}
