package com.azaat.smstereo.imagestreaming

import android.util.Log
import com.googleresearch.capturesync.FrameInfo
import com.googleresearch.capturesync.softwaresync.SoftwareSyncBase
import java.io.File

class ImageMatcher(private val frameInfo: FrameInfo) {
    fun onClientImageAvailable(clientFrame: File, timeDomainConverter: SoftwareSyncBase) {
        // takes client frame with timestamp, finds a leader frame with a matching timestamp
        val timestamp =
            clientFrame.name.split("_").dropLastWhile { it.isEmpty() }.toTypedArray()[0].toLong()
        val latestFrames = frameInfo.latestFrames
        if (!latestFrames.isEmpty()) {
            val matchingTimestamp = latestFrames.stream()
                .filter { leaderTimestamp: Long -> leaderTimestamp - timestamp < MATCHING_THRESHOLD }
                .min(Comparator.comparingLong { leaderTimestamp: Long ->
                    Math.abs(
                        leaderTimestamp - timestamp
                    )
                }
                ).orElse(null)
            if (matchingTimestamp != null) {
                Log.d(TAG, "Found match for the client frame: $matchingTimestamp $timestamp")
                val delay = timestamp - timeDomainConverter.leaderTimeNs
                Log.d(TAG, "Delay: $delay")
            } else {
                Log.d(TAG, "Match not found")
                Log.d(TAG, "Client: $timestamp")
                Log.d(TAG, "Leader ts: $latestFrames")
            }
        }

        frameInfo.displayStreamFrame(clientFrame)
        // reports image pair to the depth estimator?
    }

    companion object {
        private const val MATCHING_THRESHOLD = 5000000L
        private const val TAG = "ImageMatcher"
    }
}
