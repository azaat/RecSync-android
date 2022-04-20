package com.azaat.smstereo.imagestreaming

import android.util.Log
import com.azaat.smstereo.ImagePairAvailableListener
import com.azaat.smstereo.depthestimation.StereoDepth
import com.googleresearch.capturesync.CameraActivity
import com.googleresearch.capturesync.SynchronizedFrame
import com.googleresearch.capturesync.softwaresync.SoftwareSyncBase
import java.io.FileInputStream

class ImageMatcher(private val context: CameraActivity, private val imagePairAvailableListener: ImagePairAvailableListener) {
    val stereoDepth: StereoDepth = StereoDepth(FileInputStream(context.getExternalFilesDir(null).toString() + "/calib_params.xml").readBytes())

    fun onClientImageAvailable(clientFrame: SynchronizedFrame, timeDomainConverter: SoftwareSyncBase) {
        // takes client frame with timestamp, finds a leader frame with a matching timestamp
        val timestamp = clientFrame.timestampNs
        val latestFrames = context.latestFrames
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
                val bitmap = stereoDepth.onImagePairAvailable(clientFrame, matchingFrame)
                context.displayStreamFrame(SynchronizedFrame(bitmap, clientFrame.timestampNs));
            } else {
                Log.d(TAG, "Match not found")
                Log.d(TAG, "Client: $timestamp")
                Log.d(TAG, "Leader ts: $latestFrames")
            }
        }



    }

    companion object {
        private const val MATCHING_THRESHOLD = 5000000L
        private const val TAG = "ImageMatcher"
    }
}
