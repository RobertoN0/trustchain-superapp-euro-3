package nl.tudelft.trustchain.eurotoken.community

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen

/**
 * Payload per inviare via Bluetooth un semplice messaggio broadcast.
 */
class BroadcastBluetoothPayload(
    val id: String,
    val message: ByteArray
) : Serializable {
    override fun serialize(): ByteArray {

        return serializeVarLen(id.toByteArray(Charsets.UTF_8)) +
            serializeVarLen(message)
    }

    companion object Deserializer : Deserializable<BroadcastBluetoothPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<BroadcastBluetoothPayload, Int> {
            var localOffset = offset

            val (idBytes, idSize) = deserializeVarLen(buffer, localOffset)
            localOffset += idSize

            val (msgBytes, msgSize) = deserializeVarLen(buffer, localOffset)
            localOffset += msgSize

            return Pair(
                BroadcastBluetoothPayload(
                    id     = idBytes.toString(Charsets.UTF_8),
                    message = msgBytes
                ),
                localOffset - offset
            )
        }
    }
}
