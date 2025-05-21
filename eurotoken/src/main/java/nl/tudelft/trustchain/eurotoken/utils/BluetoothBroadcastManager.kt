package nl.tudelft.trustchain.eurotoken.utils

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import java.util.UUID
import kotlin.random.Random

/**
 * Manager class for Bluetooth broadcasting in EuroToken.
 * Uses standard Android Bluetooth API.
 */
class BluetoothBroadcastManager(private val context: Context) {

    private val TAG = "BluetoothBroadcastManager"

    // Service UUID for our broadcasts
    private val SERVICE_UUID = ParcelUuid(UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E"))

    // Get Bluetooth adapter
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter = bluetoothManager?.adapter

    // Callback for BLE advertising
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "BLE Advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE Advertising failed with error code: $errorCode")
        }
    }

    /**
     * Check if we can use Bluetooth broadcasting
     */
    fun canBroadcast(): Boolean {
        // Check if Bluetooth is available and enabled
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported on this device")
            return false
        }

        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled")
            return false
        }

        // Check if we have the necessary permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "Bluetooth advertise permission not granted")
                return false
            }
        }

        return true
    }

    /**
     * Broadcast random data to nearby devices using Bluetooth LE
     * @return true if broadcasting started successfully, false otherwise
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun broadcastRandomData(): Boolean {
        if (!canBroadcast()) return false

        try {
            // Generate random data (simulating transaction data)
            val randomData = Random.nextBytes(16)
            Log.d(TAG, "Broadcasting random data: ${randomData.joinToString(", ") { it.toString() }}")

            // Check for Bluetooth permissions (for Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_ADVERTISE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return false
                }
            }

            // Set up advertising settings
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .setTimeout(0) // 0 = no timeout
                .build()

            // Set up advertising data with our random payload
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(SERVICE_UUID)
                .addServiceData(SERVICE_UUID, randomData)
                .build()

            // Start advertising using BluetoothLeAdvertiser
            bluetoothAdapter?.bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting data", e)
            return false
        }
    }

    /**
     * Broadcast a simple "HELLO" message
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun broadcastHello(): Boolean {
        if (!canBroadcast()) return false

        try {
            // Convert "HELLO" to a byte array
            val helloData = "HELLO".toByteArray(Charsets.UTF_8)
            Log.d(TAG, "Broadcasting HELLO message")

            // Check for Bluetooth permissions (for Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_ADVERTISE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return false
                }
            }

            // Set up advertising settings
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .setTimeout(0) // 0 = no timeout
                .build()

            // Set up advertising data with our HELLO message
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(SERVICE_UUID)
                .addServiceData(SERVICE_UUID, helloData)
                .build()

            // Start advertising using BluetoothLeAdvertiser
            bluetoothAdapter?.bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting HELLO message", e)
            return false
        }
    }

    /**
     * Stop broadcasting
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun stopBroadcasting() {
        if (!canBroadcast()) return

        try {
            // Check for Bluetooth permissions (for Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_ADVERTISE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
            }

            bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            Log.d(TAG, "Bluetooth broadcasting stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop Bluetooth broadcast", e)
        }
    }
}
