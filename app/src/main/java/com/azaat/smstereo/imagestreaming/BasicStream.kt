package com.azaat.smstereo.imagestreaming

import com.azaat.smstereo.OnStreamImageAvailableListener

abstract class BasicStream: OnStreamImageAvailableListener, Thread() {
    abstract fun closeConnection()
}
