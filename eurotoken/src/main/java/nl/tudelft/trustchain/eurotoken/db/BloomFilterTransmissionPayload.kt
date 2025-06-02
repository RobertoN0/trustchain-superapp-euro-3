package nl.tudelft.trustchain.eurotoken.db

import nl.tudelft.ipv8.messaging.Deserializable
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.deserializeVarLen
import nl.tudelft.ipv8.messaging.serializeVarLen

class BloomFilterTransmissionPayload(
    val bloomFilterData: ByteArray
) : Serializable {

    override fun serialize(): ByteArray {
        return serializeVarLen(bloomFilterData)
    }

    companion object Deserializer : Deserializable<BloomFilterTransmissionPayload> {
        override fun deserialize(
            buffer: ByteArray,
            offset: Int
        ): Pair<BloomFilterTransmissionPayload, Int> {
            var localOffset = offset
            val (data, dataSize) = deserializeVarLen(buffer, localOffset)
            localOffset += dataSize

            return Pair(
                BloomFilterTransmissionPayload(data),
                localOffset - offset
            )
        }
    }
}

/**
 * Payload for transmitting Bloom filters
 */
data class BloomFilterPayload(
    val bloomFilterData: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BloomFilterPayload

        if (!bloomFilterData.contentEquals(other.bloomFilterData)) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bloomFilterData.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}
