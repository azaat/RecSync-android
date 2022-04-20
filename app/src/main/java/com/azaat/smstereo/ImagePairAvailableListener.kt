package com.azaat.smstereo

interface ImagePairAvailableListener {
    public fun onImagePairAvailable(clientFrameTimestampNs : Long, leaderFrameTimestampNs : Long)
}
