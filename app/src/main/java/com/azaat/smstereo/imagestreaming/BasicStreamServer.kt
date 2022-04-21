package com.azaat.smstereo.imagestreaming

import android.util.Log
import com.azaat.smstereo.StereoController
import com.googleresearch.capturesync.CameraActivity
import com.googleresearch.capturesync.Frame
import com.googleresearch.capturesync.softwaresync.SoftwareSyncBase
import java.io.IOException
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Receives images sent from the client smartphone
 * with BasicStreamClient
 */
class BasicStreamServer(
    private val utils: FileTransferUtils,
    private val context: CameraActivity,
    private val timeDomainConverter: SoftwareSyncBase,
    private val stereoController: StereoController
) : StreamServer() {
    @Volatile
    override var isExecuting = false
        private set

    var latestFramesBuffer: ArrayDeque<Frame> = ArrayDeque()
        private set

    private val imageMatcher: ImageMatcher = ImageMatcher(context, stereoController)
    override fun run() {
        isExecuting = true
        Log.d(TAG, "waiting to accept connection from client...")
        try {
            ServerSocket(PORT).use { rpcSocket ->
                val sdcard = context.externalDir
                val outputDir = Files.createDirectories(
                    Paths.get(
                        sdcard.absolutePath,
                        CameraActivity.SUBDIR_NAME,
                        tmpPath
                    )
                )
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

    fun onStreamFrame(streamFrame: Frame) {
        // save frame to ram buffer

        latestFramesBuffer.add(streamFrame)
        if (latestFramesBuffer.size > CameraActivity.LATEST_FRAMES_CAP) {
            latestFramesBuffer.removeFirst()
        }
    }

    /**
     * Safe to call even when not executing
     */
    override fun stopExecuting() {
        isExecuting = false
    }

    companion object {
        const val TAG = "BasicStreamServer"
        private const val SOCKET_WAIT_TIME_MS = 10000
        private const val PORT = 6969
        private const val tmpPath = "clientFrames"
    }

}
