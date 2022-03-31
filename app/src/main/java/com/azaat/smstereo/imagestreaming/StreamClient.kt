package com.azaat.smstereo.imagestreaming

import java.io.File

abstract class StreamClient {
    abstract fun onVideoFrame(frame: File, timestampNs: Long)
    abstract fun closeConnection()
}