package com.azaat.smstereo.imagestreaming

import android.content.Context
import android.util.Log
import java.io.*
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.Throws

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