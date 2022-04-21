package com.azaat.smstereo.imagestreaming

import com.azaat.smstereo.OnSyncFrameAvailable

abstract class StreamServer : Thread(), OnSyncFrameAvailable {
    abstract val isExecuting: Boolean
    abstract fun stopExecuting()
}
