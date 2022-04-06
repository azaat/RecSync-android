package com.azaat.smstereo.imagestreaming

import android.graphics.Bitmap
import android.util.Log
import com.googleresearch.capturesync.SynchronizedFrame
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.Executors


/**
 * A basic streaming client implementation sending images
 * over sockets
 */
class BasicStreamClient(private var address: InetAddress, var utils: FileTransferUtils) : StreamClient() {
    private val frameProcessor = Executors.newSingleThreadExecutor()
    lateinit var clientSocket: Socket
    override fun onVideoFrame(frame: SynchronizedFrame) {
        // send frame over the channel
        if (!frameProcessor.isShutdown) {
            frameProcessor.execute {
                try {
                    Log.d(TAG, "Sending frame")
                    clientSocket = Socket(address, PORT)
                    val bitmap = frame.bitmap
                    var width = bitmap.width
                    var height = bitmap.height

                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    val byteArray: ByteArray = stream.toByteArray()
                    bitmap.recycle()
                    utils.sendBuffer(byteArray, frame.timestampNs, clientSocket)
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
