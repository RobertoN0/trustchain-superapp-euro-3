package nl.tudelft.trustchain.eurotoken.entity.mpt

import nl.tudelft.trustchain.eurotoken.entity.BillFaceToken
import java.security.MessageDigest

/**
 * Pseudo-Random Permutation implementation for deterministic token selection
 */
class TokenPseudoRandomPermutation(private val seed: String) {
    private val digest = MessageDigest.getInstance("SHA-256")

    /**
     * Apply PRP to a nibble using the merchant's seed
     * @param nibble Input nibble (0-15)
     * @return Permuted nibble (0-15)
     */
    fun apply(nibble: Int): Int {
        require(nibble in 0..15) { "Nibble must be between 0 and 15" }

        val input = "$seed:$nibble"
        val hash = digest.digest(input.toByteArray())

        // Use first byte to determine permutation
        return (hash[0].toInt() and 0xFF) % 16
    }
}

/**
 * MPT Token Selector implementing Algorithm 1 from the paper
 * Uses existing MerklePatriciaTrie infrastructure
 */
class MPTTokenSelector {

    /**
     * Build MPT from available tokens using existing implementation
     */
    fun buildTokenMPT(tokens: List<BillFaceToken>): MerklePatriciaTrie<BillFaceTokenMPT> {
        val mpt = MPTFactory.createMPT<BillFaceTokenMPT>()

        tokens.forEach { token ->
            if (!token.isSpent) {
                val mptToken = BillFaceTokenMPT(token)
                mpt.put(mptToken)
            }
        }

        return mpt
    }

    /**
     * Algorithm 1 implementation: Choose tokens from MPT using pseudo-random permutation
     * @param seed Merchant-provided deterministic seed
     * @param mpt The Merkle Patricia Trie containing tokens
     * @param targetAmount Target amount to select
     * @return List of selected tokens in deterministic order
     */
    fun chooseTokensFromMPT(
        seed: String,
        mpt: MerklePatriciaTrie<BillFaceTokenMPT>,
        targetAmount: Long
    ): List<BillFaceToken> {

        val prp = TokenPseudoRandomPermutation(seed)
        val allKeys = mpt.getAllKeys()

        if (allKeys.isEmpty()) {
            return emptyList()
        }

        // Get deterministic order of keys using PRP-based algorithm
        val orderedKeys = chooseKeysRecursive(mpt, allKeys, prp)
        val selectedTokens = mutableListOf<BillFaceToken>()
        var currentSum = 0L

        // Select tokens in deterministic order until target amount is reached
        for (key in orderedKeys) {
            val mptToken = mpt.get(key)
            if (mptToken != null) {
                val token = mptToken.getOriginalToken()
                if (!token.isSpent) {
                    selectedTokens.add(token)
                    currentSum += token.amount

                    if (currentSum >= targetAmount) {
                        break
                    }
                }
            }
        }

        return selectedTokens
    }

    /**
     * Recursive implementation of Algorithm 1 adapted for existing MPT structure
     * Simulates the algorithm by organizing keys by their hex prefixes and applying PRP
     */
    private fun chooseKeysRecursive(
        mpt: MerklePatriciaTrie<BillFaceTokenMPT>,
        keys: List<String>,
        prp: TokenPseudoRandomPermutation
    ): List<String> {

        if (keys.isEmpty()) return emptyList()
        if (keys.size == 1) return keys

        // Group keys by their first nibble
        val keysByNibble = mutableMapOf<Int, MutableList<String>>()

        for (key in keys) {
            if (key.isNotEmpty()) {
                val firstNibble = hexCharToInt(key[0])
                keysByNibble.getOrPut(firstNibble) { mutableListOf() }.add(key)
            }
        }

        val orderedKeys = mutableListOf<String>()

        // Process nibbles in pseudo-random order (Algorithm 1 line 11-18)
        for (nibble in 0..15) {
            val k = prp.apply(nibble)
            val keysWithNibble = keysByNibble[k]

            if (!keysWithNibble.isNullOrEmpty()) {
                if (keysWithNibble.size == 1) {
                    orderedKeys.addAll(keysWithNibble)
                } else {
                    // Recursively process keys with same prefix
                    val subKeys = keysWithNibble.map { it.substring(1) }
                    val subOrdered = chooseKeysRecursive(mpt, subKeys, prp)
                    val fullKeys = subOrdered.map { k.toString(16) + it }
                    orderedKeys.addAll(fullKeys)
                }
            }
        }

        return orderedKeys
    }

    /**
     * Generate inclusion proof for selected tokens
     */
    fun generateTokenInclusionProof(
        mpt: MerklePatriciaTrie<BillFaceTokenMPT>,
        selectedTokens: List<BillFaceToken>
    ): List<InclusionProof> {
        return selectedTokens.map { token ->
            val key = MPTUtils.stringToHexKey(token.id)
            mpt.generateInclusionProof(key)
        }
    }

    /**
     * Verify that token selection was done correctly using the same seed
     */
    fun verifyTokenSelection(
        seed: String,
        originalTokens: List<BillFaceToken>,
        selectedTokens: List<BillFaceToken>,
        targetAmount: Long
    ): Boolean {

        return try {
            val mpt = buildTokenMPT(originalTokens)
            val reselectedTokens = chooseTokensFromMPT(seed, mpt, targetAmount)

            // Verify that the same tokens were selected
            val originalIds = selectedTokens.map { it.id }.toSet()
            val reselectedIds = reselectedTokens.map { it.id }.toSet()

            originalIds == reselectedIds

        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get root hash of the MPT for verification
     */
    fun getMPTRootHash(tokens: List<BillFaceToken>): ByteArray {
        val mpt = buildTokenMPT(tokens)
        return mpt.getRootHash()
    }

    private fun hexCharToInt(char: Char): Int {
        return when (char) {
            in '0'..'9' -> char - '0'
            in 'a'..'f' -> char - 'a' + 10
            in 'A'..'F' -> char - 'A' + 10
            else -> throw IllegalArgumentException("Invalid hex character: $char")
        }
    }
}
