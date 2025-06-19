package nl.tudelft.trustchain.eurotoken.entity.mpt

import nl.tudelft.trustchain.eurotoken.entity.BillFaceToken

/**
 * Interface for obj which can be serialized in the MPT
 */
interface MPTSerializable {
    fun toMPTBytes(): ByteArray
    fun getMPTKey(): String
}

/**
 * Base implementation for strings (TODO: include implementation for Transactions/Tokens)
 */
data class StringMPTItem(val key: String, val value: String) : MPTSerializable {
    override fun toMPTBytes(): ByteArray = value.toByteArray()
    override fun getMPTKey(): String = key
}

/**
 * BillFaceToken wrapper for MPT integration
 * Implements MPTSerializable to work with existing MPT infrastructure
 */
class BillFaceTokenMPT(private val token: BillFaceToken) : MPTSerializable {

    override fun toMPTBytes(): ByteArray {
        // Serialize token data for MPT storage
        val idBytes = token.id.toByteArray()
        val amountBytes = longToBytes(token.amount)
        val signatureBytes = token.intermediarySignature
        val spentFlag = if (token.isSpent) 1.toByte() else 0.toByte()
        val dateBytes = longToBytes(token.dateCreated)

        val totalSize = 4 + idBytes.size + 8 + signatureBytes.size + 1 + 8
        val result = ByteArray(totalSize)
        var offset = 0

        // ID length and data
        val idLength = idBytes.size
        result[offset++] = (idLength shr 24).toByte()
        result[offset++] = (idLength shr 16).toByte()
        result[offset++] = (idLength shr 8).toByte()
        result[offset++] = idLength.toByte()
        idBytes.copyInto(result, offset)
        offset += idBytes.size

        // Amount (8 bytes)
        amountBytes.copyInto(result, offset)
        offset += 8

        // Signature (variable length)
        signatureBytes.copyInto(result, offset)
        offset += signatureBytes.size

        // Spent flag (1 byte)
        result[offset++] = spentFlag

        // Date (8 bytes)
        dateBytes.copyInto(result, offset)

        return result
    }

    override fun getMPTKey(): String {
        // Convert token ID to hex key for MPT usage
        return MPTUtils.stringToHexKey(token.id)
    }

    /**
     * Get the original BillFaceToken
     */
    fun getOriginalToken(): BillFaceToken = token

    /**
     * Convert long to byte array
     */
    private fun longToBytes(value: Long): ByteArray {
        return byteArrayOf(
            (value shr 56).toByte(),
            (value shr 48).toByte(),
            (value shr 40).toByte(),
            (value shr 32).toByte(),
            (value shr 24).toByte(),
            (value shr 16).toByte(),
            (value shr 8).toByte(),
            value.toByte()
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BillFaceTokenMPT
        return token == other.token
    }

    override fun hashCode(): Int {
        return token.hashCode()
    }

    override fun toString(): String {
        return "BillFaceTokenMPT(token=$token)"
    }
}
