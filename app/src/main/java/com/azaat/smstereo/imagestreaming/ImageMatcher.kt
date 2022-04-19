package com.azaat.smstereo.imagestreaming

import android.util.Log
import com.azaat.smstereo.ImagePairAvailableListener
import com.googleresearch.capturesync.FrameInfo
import com.googleresearch.capturesync.SynchronizedFrame
import com.googleresearch.capturesync.softwaresync.SoftwareSyncBase
import java.io.File

class ImageMatcher(private val frameInfo: FrameInfo, private val imagePairAvailableListener: ImagePairAvailableListener) {
    fun onClientImageAvailable(clientFrame: SynchronizedFrame, timeDomainConverter: SoftwareSyncBase) {
        // takes client frame with timestamp, finds a leader frame with a matching timestamp
        val timestamp = clientFrame.timestampNs
        val latestFrames = frameInfo.latestFrames
        if (!latestFrames.isEmpty()) {
            val matchingFrame = latestFrames.stream()
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

                imagePairAvailableListener.onImagePairAvailable(timestamp, matchingFrame.timestampNs)
            } else {
                Log.d(TAG, "Match not found")
                Log.d(TAG, "Client: $timestamp")
                Log.d(TAG, "Leader ts: $latestFrames")
            }
        }



        frameInfo.displayStreamFrame(clientFrame);
    }

    companion object {
        private const val MATCHING_THRESHOLD = 5000000L
        private const val TAG = "ImageMatcher"
    }
}
