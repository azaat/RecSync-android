package com.azaat.smstereo

import com.googleresearch.capturesync.SynchronizedFrame

interface OnImagePairAvailableListener {
    fun onImagePairAvailable(
        clientFrame: SynchronizedFrame,
        leaderFrame: SynchronizedFrame
    )
}
