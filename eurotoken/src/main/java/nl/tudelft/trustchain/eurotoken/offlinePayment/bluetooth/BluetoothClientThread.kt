package nl.tudelft.trustchain.eurotoken.offlinePayment.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import java.io.IOException
import java.util.*

class BluetoothClientThread(
    private val context: Context,
    private val targetDeviceName: String,
    private val serviceUUID: UUID,
    private val onConnected: (BluetoothSocket) -> Unit,
    private val onError: (Exception) -> Unit
) {

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter = bluetoothManager?.adapter
        ?: throw UnsupportedOperationException("Bluetooth not supported")
    private var isRunning = true

    private val receiver = object : BroadcastReceiver() {

        @RequiresPermission(allOf = [
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        ])
        override fun onReceive(c: Context, intent: Intent) {
            if (BluetoothDevice.ACTION_FOUND == intent.action) {
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                if (device != null && device.name == targetDeviceName && isRunning) {
                    Log.d("Offline", "Found matching device: ${device.name}")
                    bluetoothAdapter.cancelDiscovery()
                    connectToDevice(device)
                    context.unregisterReceiver(this)
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun start() {
        if (!bluetoothAdapter.isDiscovering) {
            Log.i("Offline", "Entering Discovery mode")
            Log.d("Offline", "Searching for device `$targetDeviceName`, uuid: `$serviceUUID`")
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            context.registerReceiver(receiver, filter)
            bluetoothAdapter.startDiscovery()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stop() {
        isRunning = false
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // Already unregistered
        }
        bluetoothAdapter.cancelDiscovery()
        Log.i("Offline", "Scanning stopped")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectToDevice(device: BluetoothDevice) {
        Log.i("Offline", "Connecting to device")
        Thread {
            try {
                val socket = device.createRfcommSocketToServiceRecord(serviceUUID)
                socket.connect()
                Log.d("Offline", "Connected to ${device.name}")
                onConnected(socket)
            } catch (e: IOException) {
                Log.e("Offline", "Connection failed", e)
                onError(e)
            }
        }.start()
    }
}

