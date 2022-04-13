package com.azaat.smstereo

import com.googleresearch.capturesync.SynchronizedFrame

interface OnImagePairAvailableListener {
    fun onImagePairAvailable(
            clientFrameTimestampNs: Long,
            leaderFrameTimestampNs: Long,
            clientFrame: SynchronizedFrame
    )
}
