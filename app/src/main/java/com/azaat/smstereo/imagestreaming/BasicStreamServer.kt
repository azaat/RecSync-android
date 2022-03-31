package com.azaat.smstereo.imagestreaming

import android.os.Environment
import android.util.Log
import com.googleresearch.capturesync.CameraActivity
import com.googleresearch.capturesync.FrameInfo
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
    frameInfo: FrameInfo,
    private val timeDomainConverter: SoftwareSyncBase
) : StreamServer() {
    @Volatile
    override var isExecuting = false
        private set
    private val imageMatcher: ImageMatcher = ImageMatcher(frameInfo)
    override fun run() {
        isExecuting = true
        Log.d(TAG, "waiting to accept connection from client...")
        try {
            ServerSocket(PORT).use { rpcSocket ->
                val sdcard = Environment.getExternalStorageDirectory()
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
                            val clientFrame = utils.receiveFile(outputDir.toString(), clientSocket)
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

    /**
     * Safe to call even when not executing
     */
    override fun stopExecuting() {
        isExecuting = false
    }

    companion object {
        const val TAG = "StreamServer"
        private const val SOCKET_WAIT_TIME_MS = 1000
        private const val PORT = 6969
        private const val tmpPath = "clientFrames"
    }

}