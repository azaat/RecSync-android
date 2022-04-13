package com.azaat.smstereo

import com.googleresearch.capturesync.SynchronizedFrame

interface OnStreamImageAvailableListener {
    fun onStreamImageAvailable(frame: SynchronizedFrame)
}