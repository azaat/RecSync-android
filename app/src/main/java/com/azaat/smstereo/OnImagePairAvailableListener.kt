package com.azaat.smstereo

import com.googleresearch.capturesync.SynchronizedFrame

interface OnImagePairAvailableListener {
    public fun onImagePairAvailable(clientFrame: SynchronizedFrame, leaderFrame: SynchronizedFrame)
}
