package nl.tudelft.trustchain.eurotoken.offlinePayment.bluetooth

import android.bluetooth.BluetoothSocket
import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class BluetoothSocketListener(
    private val socket: BluetoothSocket,
    private val onMessage: (JSONObject) -> Unit,
    private val onError: (Exception) -> Unit
) : Thread() {

    fun onMessageReceived(msg: String) {
        onMessage(parseJson(msg))
    }

    private fun parseJson(msg: String): JSONObject {
        try {
            return JSONObject(msg)
        } catch (e: JSONException) {
            Log.e("Offline", "Error while parsing Bluetooth message", e)
            throw e
        }
    }

    @Volatile private var isRunning = true

    override fun run() {
        try {
            val reader = socket.inputStream.bufferedReader()
            while (isRunning) {
                val line = reader.readLine() ?: break
                onMessageReceived(line)
            }
        } catch (e: IOException) {
            if (isRunning) onError(e)
        }
    }

    fun stopListening() {
        isRunning = false
        try {
            socket.close()
        } catch (e: IOException) {
            // Already closed
        }
    }
}

