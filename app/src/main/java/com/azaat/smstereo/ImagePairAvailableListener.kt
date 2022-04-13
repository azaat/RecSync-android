package com.azaat.smstereo

import com.googleresearch.capturesync.SynchronizedFrame

interface ImagePairAvailableListener {
    fun onImagePairAvailable(
            clientFrameTimestampNs: Long,
            leaderFrameTimestampNs: Long,
            clientFrame: SynchronizedFrame
    )
}
