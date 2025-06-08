package nl.tudelft.trustchain.eurotoken.entity.mpt

import nl.tudelft.ipv8.util.toHex

/**
 * Factory for creating MPT with different data formats
 * TODO: for now only generic and StringMPT can be created
 */
object MPTFactory {

    /**
     * This creates an MPT for strings
     */
    fun createStringMPT(): MerklePatriciaTrie<StringMPTItem> {
        return MerklePatriciaTrie()
    }

    /**
     * Create a generic MPT
     */
    fun <T : MPTSerializable> createMPT(): MerklePatriciaTrie<T> {
        return MerklePatriciaTrie()
    }
}

/**
 * Utility for working with MPT
 */
object MPTUtils {

    /**
     * Converts a string ina hex key
     */
    fun stringToHexKey(input: String): String {
        return input.toByteArray().toHex()
    }

    /**
     * Verifies the integrity of a MPT
     */
    fun <T : MPTSerializable> verifyIntegrity(
        mpt1: MerklePatriciaTrie<T>,
        mpt2: MerklePatriciaTrie<T>
    ): Boolean {
        return mpt1.getRootHash().contentEquals(mpt2.getRootHash())
    }

    /**
     * Serializes an MPT in compact format
     */
    fun <T : MPTSerializable> serialize(mpt: MerklePatriciaTrie<T>): ByteArray {
        val keys = mpt.getAllKeys()
        val items = keys.mapNotNull { mpt.get(it) }

        // Simple serialization
        val totalSize = 4 + items.sumOf { 4 + it.getMPTKey().length + 4 + it.toMPTBytes().size }
        val result = ByteArray(totalSize)
        var offset = 0

        // number of elems
        val count = items.size
        result[offset++] = (count shr 24).toByte()
        result[offset++] = (count shr 16).toByte()
        result[offset++] = (count shr 8).toByte()
        result[offset++] = count.toByte()

        for (item in items) {
            val keyBytes = item.getMPTKey().toByteArray()
            val valueBytes = item.toMPTBytes()

            val keyLength = keyBytes.size
            result[offset++] = (keyLength shr 24).toByte()
            result[offset++] = (keyLength shr 16).toByte()
            result[offset++] = (keyLength shr 8).toByte()
            result[offset++] = keyLength.toByte()

            keyBytes.copyInto(result, offset)
            offset += keyBytes.size

            val valueLength = valueBytes.size
            result[offset++] = (valueLength shr 24).toByte()
            result[offset++] = (valueLength shr 16).toByte()
            result[offset++] = (valueLength shr 8).toByte()
            result[offset++] = valueLength.toByte()

            valueBytes.copyInto(result, offset)
            offset += valueBytes.size
        }

        return result
    }
}
