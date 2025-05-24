package nl.tudelft.trustchain.eurotoken.offlinePayment.transaction

import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import nl.tudelft.trustchain.eurotoken.offlinePayment.TransactionPhase
import nl.tudelft.trustchain.eurotoken.offlinePayment.bluetooth.BluetoothServerThread
import org.json.JSONObject
import java.util.UUID

class ReceiverTransactionManager(
    private val context: Context,
    private val serviceName: String,
    private val serviceUUID: UUID,
    private val onSocketReady: (BluetoothSocket) -> Unit,
    private val onConnectionError: (Exception) -> Unit
) : TransactionManager() {

    override fun execute(phase: TransactionPhase, socket: BluetoothSocket?) {
        when (phase) {
            TransactionPhase.CONNECTING -> { startServer() }
            TransactionPhase.CONNECTED -> {
                Log.i("Offline", "Sending seed")
                sendSeed(socket)
            }
            TransactionPhase.SENDING_SEED -> {}
            TransactionPhase.SENDING_TOKENS -> {}
            TransactionPhase.COMPLETED -> {
                sendCompleted(socket)
                Log.i("Offline", "Completing transaction")
            }
            TransactionPhase.ERROR -> {}
            else -> {

            }
        }
    }

    private fun sendCompleted(socket: BluetoothSocket?) {
        val json = JSONObject()
        json.put("type", "completed")
        json.put("completed", true)

        socket?.let {
            it.outputStream.write(json.toString().toByteArray())
            it.outputStream.flush()
        }
    }

    private fun sendSeed(socket: BluetoothSocket?) {
        val seed = 123456789

        val json = JSONObject()
        json.put("type", "seed")
        json.put("seed", seed)

        Log.d("Offline", "Sending seed on socket: $socket")
        socket?.let {
            it.outputStream.write(json.toString().toByteArray())
            it.outputStream.flush()
        }
    }

    private fun startServer() {
        serverThread = BluetoothServerThread(
            context,
            serviceName,
            serviceUUID,
            { socket ->
                onSocketReady(socket)
                setPhase(TransactionPhase.CONNECTED)
            },
            { error ->
                onConnectionError(error)
                setPhase(TransactionPhase.ERROR)
            }
        ).also { it.start() }
    }
}
