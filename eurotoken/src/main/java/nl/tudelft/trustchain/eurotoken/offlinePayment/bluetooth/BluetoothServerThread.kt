package nl.tudelft.trustchain.eurotoken.offlinePayment.bluetooth

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import java.io.IOException
import java.util.UUID


class BluetoothServerThread(
    context: Context,
    private val serviceName: String,
    private val serviceUUID: UUID,
    private val onClientConnected: (BluetoothSocket) -> Unit,
    private val onError: (Exception) -> Unit
) : Thread() {

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter = bluetoothManager?.adapter
        ?: throw UnsupportedOperationException("Bluetooth not supported")
    private var serverSocket: BluetoothServerSocket? = null
    @Volatile private var isRunning = true

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun run() {
        Log.i("Offline", "Starting Bluetooth server")
        try {
            serverSocket =
                bluetoothAdapter.listenUsingRfcommWithServiceRecord(serviceName, serviceUUID)
            Log.d("Offline", "Server started, waiting for client connection...")

            val socket = serverSocket?.accept()

            socket?.let {
                Log.d("Offline", "Client connected: ${it.remoteDevice.name}")
                onClientConnected(it)
            } ?: throw IOException("Socket is null after accept.")
        } catch (e: IOException) {
            if (isRunning) {
                Log.e("Offline", "Connection error", e)
                onError(e)
            }
        }
//        finally {
//            close()
//        }
    }

    fun cancel() {
        isRunning = false
        close()
    }

    private fun close() {
        try {
            serverSocket?.close()
            Log.d("Offline", "Server socket closed")
        } catch (e: IOException) {
            Log.e("Offline", "Error closing server socket", e)
        }
    }
}

