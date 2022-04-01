package com.azaat.smstereo.imagestreaming

import android.util.Log
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.Executors

/**
 * A basic streaming client implementation sending images
 * over sockets
 */
class BasicStreamClient
    // open socket connection
    (private var address: InetAddress, var utils: FileTransferUtils) : StreamClient() {
    private val frameProcessor = Executors.newSingleThreadExecutor()
    lateinit var clientSocket: Socket
    override fun onVideoFrame(frame: File, timestampNs: Long) {
        // send frame over the channel
        if (!frameProcessor.isShutdown) {
            frameProcessor.execute {
                try {
                    Log.d(TAG, "Sending frame")
                    clientSocket = Socket(address, PORT)
                    utils.sendFile(frame, clientSocket)
                } catch (e: IOException) {
                    e.printStackTrace()
                    Log.d(TAG, "Error sending frame")
                }
            }
        }
    }

    override fun closeConnection() {
        try {
            if (::clientSocket.isInitialized) {
                clientSocket.close()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    companion object {
        const val TAG = "BasicStreamClient"
        const val PORT = 6969
    }
}
