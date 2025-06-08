package nl.tudelft.trustchain.eurotoken.entity.mpt

import java.security.MessageDigest

/**
 * Base NOde of the Merkle-Patricia-Trie
 */
sealed class MPTNode {
    abstract fun getHash(): ByteArray
    abstract fun serialize(): ByteArray
}

/**
 * Leaf node containing the value
 */
data class LeafNode<T : MPTSerializable>(
    val keyEnd: String, // Remaining part for the key
    val value: T
) : MPTNode() {

    private var cachedHash: ByteArray? = null

    override fun getHash(): ByteArray {
        return cachedHash ?: run {
            val digest = MessageDigest.getInstance("SHA-256") // TODO: Consider different hash function
            digest.update("leaf".toByteArray())
            digest.update(keyEnd.toByteArray())
            digest.update(value.toMPTBytes())
            val hash = digest.digest()
            cachedHash = hash
            hash
        }
    }

    override fun serialize(): ByteArray {
        val keyBytes = keyEnd.toByteArray()
        val valueBytes = value.toMPTBytes()
        val result = ByteArray(1 + 4 + keyBytes.size + 4 + valueBytes.size)
        var offset = 0

        // Tipo nodo (0 = leaf)
        result[offset++] = 0

        // Key length
        val keyLength = keyBytes.size
        result[offset++] = (keyLength shr 24).toByte()
        result[offset++] = (keyLength shr 16).toByte()
        result[offset++] = (keyLength shr 8).toByte()
        result[offset++] = keyLength.toByte()

        // Key
        keyBytes.copyInto(result, offset)
        offset += keyBytes.size

        // Value length
        val valueLength = valueBytes.size
        result[offset++] = (valueLength shr 24).toByte()
        result[offset++] = (valueLength shr 16).toByte()
        result[offset++] = (valueLength shr 8).toByte()
        result[offset++] = valueLength.toByte()

        // Value
        valueBytes.copyInto(result, offset)

        return result
    }
}

/**
 * Extension node for optimizing longpath with one child
 */
data class ExtensionNode(
    val sharedKey: String,
    val nextNode: MPTNode
) : MPTNode() {

    private var cachedHash: ByteArray? = null

    override fun getHash(): ByteArray {
        return cachedHash ?: run {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update("extension".toByteArray())
            digest.update(sharedKey.toByteArray())
            digest.update(nextNode.getHash())
            val hash = digest.digest()
            cachedHash = hash
            hash
        }
    }

    override fun serialize(): ByteArray {
        val keyBytes = sharedKey.toByteArray()
        val nodeBytes = nextNode.serialize()
        val result = ByteArray(1 + 4 + keyBytes.size + nodeBytes.size)
        var offset = 0

        // Node type (1 = extension)
        result[offset++] = 1

        // Key length
        val keyLength = keyBytes.size
        result[offset++] = (keyLength shr 24).toByte()
        result[offset++] = (keyLength shr 16).toByte()
        result[offset++] = (keyLength shr 8).toByte()
        result[offset++] = keyLength.toByte()

        // Key
        keyBytes.copyInto(result, offset)
        offset += keyBytes.size

        // Next node
        nodeBytes.copyInto(result, offset)

        return result
    }
}

/**
 * Branch node up to 16 children (hex digits) + optional value
 */
data class BranchNode<T : MPTSerializable>(
    val children: Array<MPTNode?> = arrayOfNulls(16),
    val value: T? = null
) : MPTNode() {

    private var cachedHash: ByteArray? = null

    override fun getHash(): ByteArray {
        return cachedHash ?: run {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update("branch".toByteArray())

            // Hash of all children
            for (child in children) {
                if (child != null) {
                    digest.update(child.getHash())
                } else {
                    digest.update(ByteArray(32))
                }
            }

            // Hash of the value (if present)
            value?.let { digest.update(it.toMPTBytes()) }

            val hash = digest.digest()
            cachedHash = hash
            hash
        }
    }

    override fun serialize(): ByteArray {
        // Simplified implementation TODO:
        val childrenHashes = children.map { it?.getHash() ?: ByteArray(32) }
        val valueBytes = value?.toMPTBytes() ?: ByteArray(0)

        val totalSize = 1 + (32 * 16) + 4 + valueBytes.size
        val result = ByteArray(totalSize)
        var offset = 0

        // Node type (2 = branch)
        result[offset++] = 2

        // Hash of children
        for (childHash in childrenHashes) {
            childHash.copyInto(result, offset)
            offset += 32
        }

        // Value length
        val valueLength = valueBytes.size
        result[offset++] = (valueLength shr 24).toByte()
        result[offset++] = (valueLength shr 16).toByte()
        result[offset++] = (valueLength shr 8).toByte()
        result[offset++] = valueLength.toByte()

        // Value
        if (valueBytes.isNotEmpty()) {
            valueBytes.copyInto(result, offset)
        }

        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BranchNode<*>) return false
        return children.contentEquals(other.children) && value == other.value
    }

    override fun hashCode(): Int {
        return children.contentHashCode() * 31 + (value?.hashCode() ?: 0)
    }
}
