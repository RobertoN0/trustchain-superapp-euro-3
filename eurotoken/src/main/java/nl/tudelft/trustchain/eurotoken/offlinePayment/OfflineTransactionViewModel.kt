package nl.tudelft.trustchain.eurotoken.offlinePayment

import android.Manifest
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.trustchain.eurotoken.offlinePayment.bluetooth.BluetoothSocketListener
import nl.tudelft.trustchain.eurotoken.offlinePayment.transaction.ReceiverTransactionManager
import nl.tudelft.trustchain.eurotoken.offlinePayment.transaction.SenderTransactionManager
import nl.tudelft.trustchain.eurotoken.offlinePayment.transaction.TransactionManager
import nl.tudelft.trustchain.eurotoken.ui.offline.ReceiverDetailsFragment.Companion.ARG_AMOUNT
import nl.tudelft.trustchain.eurotoken.ui.offline.ReceiverDetailsFragment.Companion.ARG_NAME
import nl.tudelft.trustchain.eurotoken.ui.offline.ReceiverDetailsFragment.Companion.ARG_TYPE
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

data class TransactionDetails(
    val name: String,
    val key: PublicKey,
    val amount: Int,
    val uuid: UUID,
    val deviceName: String
)

enum class TransactionPhase {
    NOT_STARTED,
    CONNECTING,
    CONNECTED,
    SENDING_SEED,
    SENDING_TOKENS,
    COMPLETED,
    ERROR
}

class OfflineTransactionViewModel: ViewModel() {
    private val _transactionDetails = MutableLiveData<TransactionDetails?>()
    val transactionDetails: LiveData<TransactionDetails?> = _transactionDetails

    private val _bluetoothSocket = MutableLiveData<BluetoothSocket?>()
    val bluetoothSocket: LiveData<BluetoothSocket?> = _bluetoothSocket

    private val _phase = MutableLiveData(TransactionPhase.NOT_STARTED)
    val phase: LiveData<TransactionPhase> = _phase

    fun setDetails(details: TransactionDetails) {
        _transactionDetails.value = details
    }

    private var transactionManager: TransactionManager? = null
    private var bluetoothListener: BluetoothSocketListener? = null

    fun setPhase(phase: TransactionPhase) {
        _phase.postValue(phase)
        transactionManager?.execute(phase, bluetoothSocket.value)
    }

    fun reset() {
        transactionManager = null
        _transactionDetails.value = null
        _bluetoothSocket.value = null
        _phase.value = TransactionPhase.NOT_STARTED
    }

    private fun onMessage(payload: JSONObject) {
        when (payload.get("type")) {
            "seed" -> {
                Log.i("Offline", "Seed: ${payload.get("seed")}")
                setPhase(TransactionPhase.SENDING_TOKENS)
            }
            "tokens" -> {
                Log.i("Offline", "Tokens: ${payload.get("tokens")}")
                setPhase(TransactionPhase.COMPLETED)
            }
            "completed" -> {
                Log.i("Offline", "Completed: ${payload.get("completed")}")
                setPhase(TransactionPhase.COMPLETED)
            }
            else -> {
                Log.d("Offline", "Unknown Bluetooth message type")
            }
        }
    }

    private fun onError(exception: Exception) {
        Log.e("Offline", "Error while listening for Bluetooth messages", exception)
        throw exception
    }

    @RequiresPermission(allOf = [
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    ])
    fun startTransactionAsReceiver(
        context: Context,
        serviceName: String,
        serviceUUID: UUID,
        onConnected: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val manager = ReceiverTransactionManager(
            context,
            serviceName,
            serviceUUID,
            onSocketReady = { socket ->
                Log.d("Offline", "Receiver has established connection with sender, socket: $socket")
                _bluetoothSocket.postValue(socket)
                bluetoothListener = BluetoothSocketListener(socket, ::onMessage, ::onError)
                onConnected()
            },
            onConnectionError = onError
        )
        manager.setPhaseCallback { phase -> setPhase(phase) }
        transactionManager = manager
        setPhase(TransactionPhase.CONNECTING)
    }

    @RequiresPermission(allOf = [
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    ])
    fun startTransactionAsSender(
        context: Context,
        deviceName: String,
        serviceUUID: UUID,
        onConnected: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val manager = SenderTransactionManager(
            context,
            deviceName,
            serviceUUID,
            onSocketReady = { socket ->
                _bluetoothSocket.postValue(socket)
                bluetoothListener = BluetoothSocketListener(socket, ::onMessage, ::onError)
                onConnected()
            },
            onConnectionError = onError
        )
        manager.setPhaseCallback { phase -> setPhase(phase) }
        transactionManager = manager
        setPhase(TransactionPhase.CONNECTING)
    }

    @RequiresPermission(allOf = [
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    ])
    fun stopAll() {
        transactionManager?.stop()
        bluetoothListener?.stopListening()
        try {
            _bluetoothSocket.value?.close()
        } catch (e: IOException) {
            Log.e("TransactionVM", "Error closing socket", e)
        }
        _bluetoothSocket.value = null
    }

    @RequiresPermission(allOf = [
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    ])
    override fun onCleared() {
        Log.i("Offline", "Clearing OfflineTransactionViewModel")
        super.onCleared()
        stopAll()
    }

    fun setDetailsFromQRPayload(jsonObject: JSONObject) {
        val type = jsonObject.optString(ARG_TYPE)
        if (type != "transfer") {
            Log.e("Offline", "QR code is not of type transfer")
            throw IllegalArgumentException("Invalid QR code type")
        }
        val name = jsonObject.optString(ARG_NAME, "")
        if (name.isEmpty()) {
            Log.e("Offline", "Name not found in QR code")
            throw IllegalArgumentException("Invalid name in QR code")
        }
        val amount = jsonObject.optInt(ARG_AMOUNT, -1)
        if (amount < 1) {
            Log.e("Offline", "QR code contains an invalid amount: $amount")
            throw IllegalArgumentException("Invalid amount in QR code")
        }
        val uuid = jsonObject.optString("uuid", "")
        if (uuid.isEmpty()) {
            Log.e("Offline", "UUID not found in QR code")
            throw IllegalArgumentException("Invalid uuid in QR code")
        }
        val publicKey = jsonObject.optString("public_key", "")
        if (publicKey.isEmpty()) {
            Log.e("Offline", "Public key not found in QR code")
            throw IllegalArgumentException("Invalid public key in QR code")
        }
        val deviceName = jsonObject.optString("device_name", "")
        if (deviceName.isEmpty()) {
            Log.e("Offline", "Device name not found in QR code")
            throw IllegalArgumentException("Invalid device name in QR code")
        }

        val key = defaultCryptoProvider.keyFromPublicBin(publicKey.hexToBytes())
        setDetails(
            TransactionDetails(
                name,
                key,
                amount,
                UUID.fromString(uuid),
                deviceName
            )
        )
    }
}
