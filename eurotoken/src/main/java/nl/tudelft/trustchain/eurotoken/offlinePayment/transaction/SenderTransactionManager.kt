package nl.tudelft.trustchain.eurotoken.offlinePayment.transaction

import android.annotation.SuppressLint
import android.bluetooth.BluetoothSocket
import android.content.Context
import nl.tudelft.trustchain.eurotoken.offlinePayment.TransactionPhase
import nl.tudelft.trustchain.eurotoken.offlinePayment.bluetooth.BluetoothClientThread
import org.json.JSONObject
import java.util.UUID

class SenderTransactionManager(
    private val context: Context,
    private val deviceName: String,
    private val serviceUUID: UUID,
    private val onSocketReady: (BluetoothSocket) -> Unit,
    private val onConnectionError: (Exception) -> Unit
) : TransactionManager() {

    override fun execute(phase: TransactionPhase, socket: BluetoothSocket?) {
        when (phase) {
            TransactionPhase.CONNECTING -> { connectToServer() }
            TransactionPhase.CONNECTED -> {}
            TransactionPhase.SENDING_SEED -> {}
            TransactionPhase.SENDING_TOKENS -> {
                sendTokens(socket)
            }
            TransactionPhase.COMPLETED -> {}
            TransactionPhase.ERROR -> {}
            else -> {

            }
        }
    }

    private fun sendTokens(socket: BluetoothSocket?) {
        val tokens = listOf(
            "Token-1",
            "Token-2",
            "Token-3"
        )
        val json = JSONObject()
        json.put("type", "tokens")
        json.put("tokens", tokens)

        socket?.let {
            it.outputStream.write(json.toString().toByteArray())
            it.outputStream.flush()
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToServer() {
        clientThread = BluetoothClientThread(context, deviceName, serviceUUID,
            { socket ->
                onSocketReady(socket)
                setPhase(TransactionPhase.CONNECTED)
            }, { error ->
                onConnectionError(error)
                setPhase(TransactionPhase.ERROR)
            }).also { it.start() }
    }
}

