package com.azaat.smstereo.imagestreaming

import android.util.Log
import com.azaat.smstereo.OnImagePairAvailableListener
import com.googleresearch.capturesync.FrameInfo
import com.googleresearch.capturesync.SynchronizedFrame
import com.googleresearch.capturesync.softwaresync.SoftwareSyncBase

class ImageMatcher(private val latestFrames: java.util.ArrayDeque<SynchronizedFrame>, private val onImagePairAvailableListener: OnImagePairAvailableListener) {
    fun onClientImageAvailable(clientFrame: SynchronizedFrame, timeDomainConverter: SoftwareSyncBase) {
        // takes client frame with timestamp, finds a leader frame with a matching timestamp
        val timestamp = clientFrame.timestampNs
        val latestFrames = latestFrames
        if (latestFrames.isEmpty()) {
            return
        }

        var matchingFrame = latestFrames.stream()
            .filter { leaderFrame: SynchronizedFrame -> leaderFrame.timestampNs - timestamp < MATCHING_THRESHOLD }
            .min(Comparator.comparingLong { leaderFrame: SynchronizedFrame ->
                Math.abs(
                    leaderFrame.timestampNs - timestamp
                )
            }
            ).orElse(null)
        when {
            matchingFrame != null -> {
                Log.d(TAG, "Found match for the client frame: ${matchingFrame.timestampNs} $timestamp")
                val delay = timestamp - timeDomainConverter.leaderTimeNs
                Log.d(TAG, "Delay: $delay")

                onImagePairAvailableListener.onImagePairAvailable(clientFrame, matchingFrame)
            }
            FALLBACK_TO_UNSYNC -> {
                matchingFrame = latestFrames.last()
                onImagePairAvailableListener.onImagePairAvailable(clientFrame, matchingFrame)
            }
            else -> {
                Log.d(TAG, "Match not found")
                Log.d(TAG, "Client: $timestamp")
                Log.d(TAG, "Leader ts: $latestFrames")
            }
        }
    }

    companion object {
        private const val MATCHING_THRESHOLD = 5000000L
        private const val TAG = "ImageMatcher"
        private const val FALLBACK_TO_UNSYNC = true
    }
}
