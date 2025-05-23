package nl.tudelft.trustchain.eurotoken.offlinePayment.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
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

    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var isRunning = true

    private val receiver = object : BroadcastReceiver() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
        override fun onReceive(c: Context, intent: Intent) {
            if (BluetoothDevice.ACTION_FOUND == intent.action) {
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                if (device != null && device.name == targetDeviceName && isRunning) {
                    Log.d("BluetoothClient", "Found matching device: ${device.name}")
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
    }

    private fun connectToDevice(device: BluetoothDevice) {
        Thread {
            try {
                val socket = device.createRfcommSocketToServiceRecord(serviceUUID)
                socket.connect()
                Log.d("BluetoothClient", "Connected to ${device.name}")
                onConnected(socket)
            } catch (e: IOException) {
                Log.e("BluetoothClient", "Connection failed", e)
                onError(e)
            }
        }.start()
    }
}

