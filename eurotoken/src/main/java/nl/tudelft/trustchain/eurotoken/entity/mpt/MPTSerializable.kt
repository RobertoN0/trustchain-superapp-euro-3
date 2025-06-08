package nl.tudelft.trustchain.eurotoken.entity.mpt

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
