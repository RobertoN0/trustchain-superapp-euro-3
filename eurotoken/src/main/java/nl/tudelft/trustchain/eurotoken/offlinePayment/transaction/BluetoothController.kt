package nl.tudelft.trustchain.eurotoken.offlinePayment.transaction

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.UUID

class BluetoothController(
    private val context: Context,
    private val activityStarter: (Intent) -> Unit
) {

    fun isBluetoothSupported(): Boolean {
        val manager = context.getSystemService(BluetoothManager::class.java)
        return manager?.adapter != null
    }

    fun isBluetoothEnabled(): Boolean {
        val manager = context.getSystemService(BluetoothManager::class.java)
        return manager?.adapter?.isEnabled == true
    }

    fun requestEnableBluetooth() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        activityStarter(enableBtIntent)
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val connectPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            val scanPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

            connectPermission && scanPermission
        } else {
            // For API < 31, these permissions are implicitly granted if declared
            true
        }
    }

    @SuppressLint("MissingPermission")
    fun startServer(uuid: UUID, onClientConnected: (BluetoothSocket) -> Unit) {
        if (!hasBluetoothPermissions()) {
            throw SecurityException("Bluetooth permissions not granted")
        }

        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager?.adapter
            ?: throw UnsupportedOperationException("Bluetooth not supported")

        val serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("EuroToken", uuid)

        Thread {
            try {
                val socket = serverSocket.accept()
                onClientConnected(socket)
            } catch (e: IOException) {
                Log.e("BluetoothServer", "Socket accept() failed", e)
            } finally {
                try {
                    serverSocket.close()
                } catch (e: IOException) {
                    Log.e("BluetoothServer", "Could not close server socket", e)
                }
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    fun connectToServer(serverUuid: UUID, serverAddress: String, onConnected: (BluetoothSocket) -> Unit) {
        if (!hasBluetoothPermissions()) {
            throw SecurityException("Bluetooth permissions not granted")
        }
        val manager = context.getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter ?: return
        val device = adapter.getRemoteDevice(serverAddress)
        val socket = device.createRfcommSocketToServiceRecord(serverUuid)
        Thread {
            try {
                socket.connect()
                onConnected(socket)
            } catch (e: IOException) {
                Log.e("BluetoothClient", "Failed to connect", e)
                try {
                    socket.close()
                } catch (_: IOException) { }
                    Log.e("BluetoothClient", "Could not close socket", e)
            }
        }.start()
    }
}
