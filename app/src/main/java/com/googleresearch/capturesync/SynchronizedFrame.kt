package com.googleresearch.capturesync

import android.graphics.Bitmap

class SynchronizedFrame(
    val bitmap: Bitmap, val timestampNs: Long, val byteArray: ByteArray
) {

    fun close() {
        bitmap.recycle()
    }
}
