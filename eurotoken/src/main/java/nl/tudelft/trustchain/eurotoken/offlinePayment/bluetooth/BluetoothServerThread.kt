package nl.tudelft.trustchain.eurotoken.offlinePayment.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import androidx.annotation.RequiresPermission
import java.io.IOException
import java.util.UUID


class BluetoothServerThread(
    private val serviceName: String,
    private val serviceUUID: UUID,
    private val onClientConnected: (BluetoothSocket) -> Unit,
    private val onError: (Exception) -> Unit
) : Thread() {

    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var serverSocket: BluetoothServerSocket? = null
    @Volatile private var isRunning = true

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun run() {
        try {
            serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(serviceName, serviceUUID)
            Log.d("BluetoothServer", "Server started, waiting for client connection...")

            val socket = serverSocket?.accept()

            socket?.let {
                Log.d("BluetoothServer", "Client connected: ${it.remoteDevice.name}")
                onClientConnected(it)
            } ?: throw IOException("Socket is null after accept.")
        } catch (e: IOException) {
            if (isRunning) {
                Log.e("BluetoothServer", "Connection error", e)
                onError(e)
            }
        } finally {
            close()
        }
    }

    fun cancel() {
        isRunning = false
        close()
    }

    private fun close() {
        try {
            serverSocket?.close()
            Log.d("BluetoothServer", "Server socket closed")
        } catch (e: IOException) {
            Log.e("BluetoothServer", "Error closing server socket", e)
        }
    }
}

