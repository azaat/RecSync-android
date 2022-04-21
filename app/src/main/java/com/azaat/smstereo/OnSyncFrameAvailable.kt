package com.azaat.smstereo

import com.googleresearch.capturesync.SynchronizedFrame

interface OnSyncFrameAvailable {
    fun onSyncFrameAvailable(frame: SynchronizedFrame)
}
