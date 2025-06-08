package nl.tudelft.trustchain.eurotoken.entity.mpt

import nl.tudelft.ipv8.util.toHex
import java.security.MessageDigest

/**
 * Inclusion proof to verify that an item is present in the MPT
 */
data class InclusionProof(
    val key: String,
    val value: ByteArray?,
    val proof: List<ByteArray>,
    val isIncluded: Boolean
) {

    /**
     * Verifies that the proof is valid against the provided root hash
     * Reconstructs the path from the leaf to the root and compares the hashes
     */
    fun verify(rootHash: ByteArray): Boolean {
        if (proof.isEmpty()) {
            return false
        }

        try {
            if (!isIncluded) {
                return verifyNonInclusion(rootHash)
            }

            var currentHash = calculateLeafHash(key, value ?: return false)
            var keyIndex = 0

            // BOTTOM UP!
            for (i in proof.indices.reversed()) {
                val nodeData = proof[i]
                currentHash = calculateParentHash(nodeData, currentHash, key, keyIndex)
                keyIndex++
            }

            return currentHash.contentEquals(rootHash)

        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Verify that an element is NOT present in the trie
     */
    private fun verifyNonInclusion(rootHash: ByteArray): Boolean {
        // To prove non-inclusion, the proof must show that:
        // 1. The path breaks before reaching the full key
        // 2. Or that there exists a leaf node with different key

        if (proof.isEmpty()) return false

        try {
            var currentHash = rootHash
            var keyIndex = 0

            for (nodeData in proof) {
                val nodeType = getNodeType(nodeData)

                when (nodeType) {
                    NodeType.LEAF -> {
                        val leafKey = extractLeafKey(nodeData)
                        return leafKey != key
                    }

                    NodeType.BRANCH -> {
                        if (keyIndex >= key.length) return true

                        val nextChar = key[keyIndex]
                        val childIndex = hexCharToIndex(nextChar)
                        val hasChild = branchHasChild(nodeData, childIndex)

                        if (!hasChild) return true
                        keyIndex++
                    }

                    NodeType.EXTENSION -> {
                        val sharedKey = extractExtensionKey(nodeData)
                        val remainingKey = key.substring(keyIndex)

                        if (!remainingKey.startsWith(sharedKey)) return true
                        keyIndex += sharedKey.length
                    }
                }
            }

            return false

        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Compute has of leaf node
     */
    private fun calculateLeafHash(key: String, value: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("leaf".toByteArray())
        digest.update(key.toByteArray())
        digest.update(value)
        return digest.digest()
    }

    /**
     * Calculates the hash of the parent node based on the data of the node and the hash of the child
     */
    private fun calculateParentHash(
        nodeData: ByteArray,
        childHash: ByteArray,
        key: String,
        keyIndex: Int
    ): ByteArray {
        val nodeType = getNodeType(nodeData)
        val digest = MessageDigest.getInstance("SHA-256")

        when (nodeType) {
            NodeType.BRANCH -> {
                digest.update("branch".toByteArray())

                val childIndex = if (keyIndex < key.length) {
                    hexCharToIndex(key[keyIndex])
                } else 0

                for (i in 0..15) {
                    if (i == childIndex) {
                        digest.update(childHash)
                    } else {
                        digest.update(ByteArray(32)) // Hash vuoto per figli assenti
                    }
                }

                val branchValue = extractBranchValue(nodeData)
                if (branchValue != null) {
                    digest.update(branchValue)
                }
            }

            NodeType.EXTENSION -> {
                digest.update("extension".toByteArray())
                val sharedKey = extractExtensionKey(nodeData)
                digest.update(sharedKey.toByteArray())
                digest.update(childHash)
            }

            NodeType.LEAF -> {
                throw IllegalStateException("Leaf node found in parent calculation")
            }
        }

        return digest.digest()
    }

    // Utilities for parsing nodes (simplified version)

    private enum class NodeType { LEAF, EXTENSION, BRANCH }

    private fun getNodeType(nodeData: ByteArray): NodeType {
        if (nodeData.isEmpty()) throw IllegalArgumentException("Empty node data")

        return when (nodeData[0].toInt()) {
            0 -> NodeType.LEAF
            1 -> NodeType.EXTENSION
            2 -> NodeType.BRANCH
            else -> throw IllegalArgumentException("Unknown node type: ${nodeData[0]}")
        }
    }

    private fun extractLeafKey(nodeData: ByteArray): String {
        var offset = 1 // Skip type byte

        // Read key length
        val keyLength =
            ((nodeData[offset++].toInt() and 0xFF) shl 24) or
                ((nodeData[offset++].toInt() and 0xFF) shl 16) or
                ((nodeData[offset++].toInt() and 0xFF) shl 8) or
                (nodeData[offset++].toInt() and 0xFF)

        // Read key
        val keyBytes = nodeData.sliceArray(offset until offset + keyLength)
        return String(keyBytes)
    }

    private fun extractExtensionKey(nodeData: ByteArray): String {
        var offset = 1 // Skip type byte

        // Read key length
        val keyLength =
            ((nodeData[offset++].toInt() and 0xFF) shl 24) or
                ((nodeData[offset++].toInt() and 0xFF) shl 16) or
                ((nodeData[offset++].toInt() and 0xFF) shl 8) or
                (nodeData[offset++].toInt() and 0xFF)

        // Read key
        val keyBytes = nodeData.sliceArray(offset until offset + keyLength)
        return String(keyBytes)
    }

    private fun branchHasChild(nodeData: ByteArray, childIndex: Int): Boolean {
        if (childIndex < 0 || childIndex > 15) return false

        // In our format, after the type byte we have 16 hashes of 32 bytes each
        val hashOffset = 1 + (childIndex * 32)
        if (hashOffset + 32 > nodeData.size) return false

        // Checks whether the hash is non-zero (child present)
        val childHash = nodeData.sliceArray(hashOffset until hashOffset + 32)
        return !childHash.all { it == 0.toByte() }
    }

    private fun extractBranchValue(nodeData: ByteArray): ByteArray? {
        // In our format: type(1) + 16_hashes(512) + value_length(4) + value
        val valueLengthOffset = 1 + (16 * 32)
        if (valueLengthOffset + 4 > nodeData.size) return null

        val valueLength =
            ((nodeData[valueLengthOffset].toInt() and 0xFF) shl 24) or
                ((nodeData[valueLengthOffset + 1].toInt() and 0xFF) shl 16) or
                ((nodeData[valueLengthOffset + 2].toInt() and 0xFF) shl 8) or
                (nodeData[valueLengthOffset + 3].toInt() and 0xFF)

        if (valueLength == 0) return null

        val valueOffset = valueLengthOffset + 4
        if (valueOffset + valueLength > nodeData.size) return null

        return nodeData.sliceArray(valueOffset until valueOffset + valueLength)
    }

    private fun hexCharToIndex(char: Char): Int {
        return when (char) {
            in '0'..'9' -> char - '0'
            in 'a'..'f' -> char - 'a' + 10
            in 'A'..'F' -> char - 'A' + 10
            else -> throw IllegalArgumentException("Invalid hex character: $char")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InclusionProof) return false

        return key == other.key &&
            value?.contentEquals(other.value ?: ByteArray(0)) != false &&
            isIncluded == other.isIncluded &&
            proof.size == other.proof.size &&
            proof.zip(other.proof).all { (a, b) -> a.contentEquals(b) }
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + (value?.contentHashCode() ?: 0)
        result = 31 * result + isIncluded.hashCode()
        result = 31 * result + proof.hashCode()
        return result
    }

    override fun toString(): String {
        return "InclusionProof(key='$key', isIncluded=$isIncluded, " +
            "value=${value?.toHex()?.take(16)}..., proofSize=${proof.size})"
    }
}
