package com.azaat.smstereo.imagestreaming

import android.util.Log
import com.azaat.smstereo.OnImagePairAvailableListener
import com.googleresearch.capturesync.CameraActivity
import com.googleresearch.capturesync.SynchronizedFrame
import com.googleresearch.capturesync.softwaresync.SoftwareSyncBase

class ImageMatcher(private val latestFramesBuffer: ArrayDeque<SynchronizedFrame>, private val imagePairAvailableListener: OnImagePairAvailableListener) {
    fun onClientImageAvailable(clientFrame: SynchronizedFrame, timeDomainConverter: SoftwareSyncBase) {
        // takes client frame with timestamp, finds a leader frame with a matching timestamp
        val timestamp = clientFrame.timestampNs
        if (!latestFramesBuffer.isEmpty()) {
            val matchingFrame = latestFramesBuffer.stream()
                .filter { leaderFrame: SynchronizedFrame -> leaderFrame.timestampNs - timestamp < MATCHING_THRESHOLD }
                .min(Comparator.comparingLong { leaderFrame: SynchronizedFrame ->
                    Math.abs(
                        leaderFrame.timestampNs - timestamp
                    )
                }
                ).orElse(null)
            if (matchingFrame != null) {
                Log.d(TAG, "Found match for the client frame: ${matchingFrame.timestampNs} $timestamp")
                val delay = timestamp - timeDomainConverter.leaderTimeNs
                Log.d(TAG, "Delay: $delay")

                imagePairAvailableListener.onImagePairAvailable(leaderFrame = matchingFrame, clientFrame = clientFrame)
            } else {
                Log.d(TAG, "Match not found")
                Log.d(TAG, "Client: $timestamp")
                Log.d(TAG, "Leader ts: $latestFramesBuffer")
            }
        }
    }

    companion object {
        private const val MATCHING_THRESHOLD = 5000000L
        private const val TAG = "ImageMatcher"
    }
}
