package com.googleresearch.capturesync

import android.graphics.Bitmap

class SynchronizedFrame(
    val bitmap: Bitmap,
    val timestampNs: Long
) {

    fun close() {
        bitmap.recycle()
    }
}
