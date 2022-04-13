package com.azaat.smstereo.imagestreaming

import android.util.Log
import com.azaat.smstereo.StereoController
import com.googleresearch.capturesync.CameraActivity
import com.googleresearch.capturesync.SynchronizedFrame
import com.googleresearch.capturesync.softwaresync.SoftwareSyncBase
import java.io.IOException
import java.net.ServerSocket

/**
 * Receives images sent from the client smartphone
 * with BasicStreamClient
 */
class BasicStreamServer(
    private val utils: FileTransferUtils,
    private val timeDomainConverter: SoftwareSyncBase,
    stereoController: StereoController
) : BasicStream() {
    @Volatile
    var isExecuting = false
        private set

    private val latestFrames: java.util.ArrayDeque<SynchronizedFrame> = java.util.ArrayDeque()

    private val imageMatcher: ImageMatcher = ImageMatcher(latestFrames, stereoController)
    override fun run() {
        isExecuting = true
        Log.d(TAG, "waiting to accept connection from client...")
        try {
            ServerSocket(PORT).use { rpcSocket ->
                rpcSocket.reuseAddress = true
                rpcSocket.soTimeout = SOCKET_WAIT_TIME_MS
                while (isExecuting) {
                    try {
                        rpcSocket.accept().use { clientSocket ->
                            clientSocket.keepAlive = true
                            Log.d(TAG, "accepted connection from client")

                            // receive frame
                            val clientFrame = utils.receiveBuffer(clientSocket)
                            Log.d(TAG, "File received")
                            imageMatcher.onClientImageAvailable(clientFrame, timeDomainConverter)
                        }
                    } catch (e: IOException) {
                        Log.d(TAG, "socket timed out, waiting for new connection to client")
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onStreamImageAvailable(frame: SynchronizedFrame) {
        // save frame to ram buffer

        latestFrames.add(SynchronizedFrame(frame.bitmap, frame.timestampNs))
        if (latestFrames.size > CameraActivity.LATEST_FRAMES_CAP) {
            latestFrames.first.close()
            latestFrames.removeFirst()
        }
    }

    override fun closeConnection() {
        isExecuting = false
    }

    companion object {
        const val TAG = "BasicStreamServer"
        private const val SOCKET_WAIT_TIME_MS = 10000
        private const val PORT = 6969
        private const val tmpPath = "clientFrames"
    }

}
