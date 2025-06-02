package nl.tudelft.trustchain.eurotoken.offlinePayment.transaction

import android.annotation.SuppressLint
import android.bluetooth.BluetoothSocket
import nl.tudelft.trustchain.eurotoken.offlinePayment.TransactionPhase
import nl.tudelft.trustchain.eurotoken.offlinePayment.bluetooth.BluetoothClientThread
import nl.tudelft.trustchain.eurotoken.offlinePayment.bluetooth.BluetoothServerThread

abstract class TransactionManager{
    protected var serverThread: BluetoothServerThread? = null
    protected var clientThread: BluetoothClientThread? = null
    protected lateinit var setPhase: (TransactionPhase) -> Unit

    fun setPhaseCallback(callback: (TransactionPhase) -> Unit) {
        this.setPhase = callback
    }

    abstract fun execute(phase: TransactionPhase, socket: BluetoothSocket?)

    @SuppressLint("MissingPermission")
    fun stop() {
        serverThread?.cancel()
        clientThread?.stop()
    }
}
