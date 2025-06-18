package nl.tudelft.trustchain.eurotoken.entity.mpt

import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.eurotoken.entity.BillFaceToken
import nl.tudelft.trustchain.eurotoken.entity.mpt.MPTNode.*
import java.security.MessageDigest

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

/**
 * Utility functions for MPT token operations
 */
object TokenMPTUtils {

    /**
     * Create a deterministic seed from merchant information
     * Used by merchants to generate consistent seeds for token selection
     */
    fun createMerchantSeed(
        merchantPublicKey: String,
        timestamp: Long = System.currentTimeMillis(),
        nonce: String? = null
    ): String {
        val input = "$merchantPublicKey:$timestamp:${nonce ?: ""}"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }.take(32)
    }

    /**
     * Compare two token selections for equality
     * Used in double-spend detection
     */
    fun compareSelections(
        selection1: List<BillFaceToken>,
        selection2: List<BillFaceToken>
    ): Boolean {
        val ids1 = selection1.map { it.id }.toSet()
        val ids2 = selection2.map { it.id }.toSet()
        return ids1 == ids2
    }

    /**
     * Detect potential double-spend attempt
     * Returns true if the same tokens would be selected again
     */
    fun detectDoubleSpendAttempt(
        originalTokens: List<BillFaceToken>,
        seed: String,
        amount: Long,
        previousSelections: List<List<BillFaceToken>>
    ): Boolean {

        return try {
            val selector = MPTTokenSelector()
            val mpt = selector.buildTokenMPT(originalTokens)
            val newSelection = selector.chooseTokensFromMPT(seed, mpt, amount)

            // Check if this selection matches any previous selection
            previousSelections.any { previousSelection ->
                compareSelections(newSelection, previousSelection)
            }

        } catch (e: Exception) {
            false
        }
    }

    /**
     * Generate a unique transaction ID for tracking
     */
    fun generateTransactionId(
        merchantPublicKey: String,
        customerPublicKey: String,
        amount: Long,
        timestamp: Long = System.currentTimeMillis()
    ): String {
        val input = "$merchantPublicKey:$customerPublicKey:$amount:$timestamp"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }.take(16)
    }

    /**
     * Validate a merchant seed for security requirements
     */
    fun validateMerchantSeed(seed: String): Boolean {
        return seed.length >= 16 && seed.matches(Regex("[0-9a-fA-F]+"))
    }

    /**
     * Calculate the efficiency of token selection
     * Returns a value between 0 and 1, where 1 is perfect efficiency
     */
    fun calculateSelectionEfficiency(
        targetAmount: Long,
        selectedTokens: List<BillFaceToken>
    ): Double {
        if (selectedTokens.isEmpty() || targetAmount <= 0) return 0.0

        val totalSelected = selectedTokens.sumOf { it.amount }
        if (totalSelected < targetAmount) return 0.0

        return targetAmount.toDouble() / totalSelected.toDouble()
    }

    /**
     * Find the optimal token combination for an amount (for comparison with MPT selection)
     */
    fun findOptimalTokenCombination(
        tokens: List<BillFaceToken>,
        targetAmount: Long
    ): List<BillFaceToken> {

        // Simple greedy algorithm for comparison
        val sortedTokens = tokens.filter { !it.isSpent }.sortedBy { it.amount }
        val selected = mutableListOf<BillFaceToken>()
        var currentSum = 0L

        for (token in sortedTokens) {
            if (currentSum + token.amount <= targetAmount) {
                selected.add(token)
                currentSum += token.amount
            }
            if (currentSum >= targetAmount) break
        }

        // If we didn't reach the target, try adding the smallest token that gets us there
        if (currentSum < targetAmount) {
            val remainingTokens = sortedTokens.filter { it !in selected }
            val nextToken = remainingTokens.minByOrNull { it.amount }
            nextToken?.let { selected.add(it) }
        }

        return selected
    }

    /**
     * Verify MPT root hash integrity
     */
    fun verifyMPTIntegrity(
        tokens: List<BillFaceToken>,
        expectedRootHash: ByteArray
    ): Boolean {
        return try {
            val selector = MPTTokenSelector()
            val actualRootHash = selector.getMPTRootHash(tokens)
            actualRootHash.contentEquals(expectedRootHash)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Create a seed for testing purposes
     */
    fun createTestSeed(scenario: String = "default"): String {
        val testData = when (scenario) {
            "deterministic" -> "test_deterministic_scenario_001"
            "double_spend" -> "test_double_spend_scenario_002"
            "merchant_a" -> "test_merchant_a_scenario_003"
            "merchant_b" -> "test_merchant_b_scenario_004"
            else -> "test_default_scenario_000"
        }

        return createMerchantSeed(testData, System.currentTimeMillis())
    }

    /**
     * Format token selection for logging/debugging
     */
    fun formatTokenSelection(
        selectedTokens: List<BillFaceToken>,
        seed: String? = null
    ): String {
        val tokenInfo = selectedTokens.joinToString(", ") {
            "${it.id.take(8)}:${it.amount}"
        }
        val totalAmount = selectedTokens.sumOf { it.amount }
        val seedInfo = seed?.let { " (seed: ${it.take(8)}...)" } ?: ""

        return "Selection: [${tokenInfo}] Total: ${totalAmount}${seedInfo}"
    }

    /**
     * Extract token IDs from a selection for comparison
     */
    fun extractTokenIds(tokens: List<BillFaceToken>): Set<String> {
        return tokens.map { it.id }.toSet()
    }

    /**
     * Check if a token selection contains any spent tokens
     */
    fun containsSpentTokens(tokens: List<BillFaceToken>): Boolean {
        return tokens.any { it.isSpent }
    }

    /**
     * Filter out spent tokens from a selection
     */
    fun filterSpentTokens(tokens: List<BillFaceToken>): List<BillFaceToken> {
        return tokens.filter { !it.isSpent }
    }

    /**
     * Calculate selection statistics
     */
    fun calculateSelectionStatistics(
        allTokens: List<BillFaceToken>,
        selectedTokens: List<BillFaceToken>
    ): Map<String, Any> {
        val totalTokens = allTokens.size
        val selectedCount = selectedTokens.size
        val totalAmount = allTokens.sumOf { it.amount }
        val selectedAmount = selectedTokens.sumOf { it.amount }

        return mapOf(
            "total_tokens" to totalTokens,
            "selected_tokens" to selectedCount,
            "selection_ratio" to (selectedCount.toDouble() / totalTokens.toDouble()),
            "total_amount" to totalAmount,
            "selected_amount" to selectedAmount,
            "amount_ratio" to (selectedAmount.toDouble() / totalAmount.toDouble()),
            "efficiency" to calculateSelectionEfficiency(selectedAmount, selectedTokens),
            "average_token_value" to (selectedAmount.toDouble() / selectedCount.toDouble())
        )
    }
}
