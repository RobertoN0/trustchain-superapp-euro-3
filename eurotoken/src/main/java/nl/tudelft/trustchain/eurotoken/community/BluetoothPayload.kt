package nl.tudelft.trustchain.eurotoken.community

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen

// sotto alla classe EuroTokenCommunity (o in un file a parte)
class BluetoothPayload(val msg: String) : Serializable {
    override fun serialize(): ByteArray = serializeVarLen(msg.toByteArray())

    companion object Deserializer : Deserializable<BluetoothPayload> {
        override fun deserialize(buf: ByteArray, off: Int)
            = deserializeVarLen(buf, off).let { (bytes, size) ->
            Pair(BluetoothPayload(String(bytes)), size)
        }
    }
}

