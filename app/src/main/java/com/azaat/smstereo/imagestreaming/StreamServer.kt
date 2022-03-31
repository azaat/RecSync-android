package com.azaat.smstereo.imagestreaming

abstract class StreamServer : Thread() {
    abstract val isExecuting: Boolean
    abstract fun stopExecuting()
}
