package nl.tudelft.trustchain.eurotoken.offlinePayment.transaction

import android.bluetooth.BluetoothSocket
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.util.toHex
import org.json.JSONObject
import java.util.UUID


class Receiver(
    private val bluetoothController: BluetoothController
) {

    /**
     * Starts a Bluetooth server.
     *
     * Usage:
     * ```
     * val controller = AndroidBluetoothController(
     *     context = this,
     *     activityStarter = { intent -> startActivityForResult(intent, REQUEST_ENABLE_BT) }
     * )
     *
     * val receiver = Receiver(controller)
     * receiver.start()
     * ```
     */
    fun start(myPeer: Peer, amount: Int, onClientConnected: (BluetoothSocket) -> Unit): JSONObject {
        if (!bluetoothController.isBluetoothSupported()) {
            throw UnsupportedOperationException("Device does not support Bluetooth")
        }

        if (!bluetoothController.isBluetoothEnabled()) {
            bluetoothController.requestEnableBluetooth()
        }

        val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        bluetoothController.startServer(uuid, onClientConnected)
        val connectionData = JSONObject()
        connectionData.put("public_key", myPeer.publicKey.keyToBin().toHex())
        connectionData.put("amount", amount)
        connectionData.put("uuid", uuid.toString())

        return connectionData
    }
}
