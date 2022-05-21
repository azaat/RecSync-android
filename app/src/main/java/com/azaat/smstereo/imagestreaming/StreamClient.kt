package com.azaat.smstereo.imagestreaming

import com.googleresearch.capturesync.SynchronizedFrame

abstract class StreamClient {
    abstract fun onVideoFrame(frame: SynchronizedFrame)
    abstract fun closeConnection()
}
