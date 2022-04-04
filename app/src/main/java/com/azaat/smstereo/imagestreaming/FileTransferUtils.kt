package com.azaat.smstereo.imagestreaming

import android.R.attr.data
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.googleresearch.capturesync.SynchronizedFrame
import java.io.*
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Paths


/**
 * Provides methods for file transfer with TCP sockets
 */
class FileTransferUtils(var mContext: Context) {
    /**
     * Sends specified file with provided TCP socket
     *
     * @param file
     * @param sendSocket
     * @throws IOException
     */
    @Throws(IOException::class)
    fun sendFile(
        file: File, sendSocket: Socket
    ) {
        val out = BufferedOutputStream(sendSocket.getOutputStream())
        DataOutputStream(out).use { dataOutputStream ->
            dataOutputStream.writeUTF(file.name)
            Files.copy(file.toPath(), dataOutputStream)
        }
    }

    fun sendBuffer(
        byteArray: ByteArray, timestampNs: Long, sendSocket: Socket
    ) {
        val dOut = DataOutputStream(sendSocket.getOutputStream())

        dOut.writeLong(timestampNs) // write length of the message

        dOut.write(byteArray) // write the message
        dOut.close()
    }

    fun receiveBuffer(
        receiveSocket: Socket
    ): SynchronizedFrame {
        val dIn = DataInputStream(receiveSocket.getInputStream())

        val timestampNs = dIn.readLong()
        val byteArray = dIn.readBytes()
        dIn.close()

        val bmp = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
        return SynchronizedFrame(bmp, timestampNs)
    }

    /**
     * Handles receiving file with specified TCP socket,
     * saves it
     *
     * @throws IOException
     */
    @Throws(IOException::class)
    fun receiveFile(filePath: String, receiveSocket: Socket): File {
        Log.d(TAG, "Now receiving file...")
        Log.d(TAG, "File Name : $filePath")
        val bufferedInputStream = BufferedInputStream(receiveSocket.getInputStream())
        DataInputStream(bufferedInputStream).use { dataInputStream ->
            val fileName = dataInputStream.readUTF()
            Files.copy(dataInputStream, Paths.get(filePath, fileName))
            return Paths.get(filePath, fileName).toFile()
        }
    }

    companion object {
        private const val TAG = "FileTransferUtils"
    }
}
