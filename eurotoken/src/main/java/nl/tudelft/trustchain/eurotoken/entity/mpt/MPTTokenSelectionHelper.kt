package nl.tudelft.trustchain.eurotoken.entity.mpt


import nl.tudelft.trustchain.eurotoken.entity.BillFaceToken
import nl.tudelft.trustchain.eurotoken.entity.mpt.*

/**
 * Selection proof data for MPT-based token selection
 */
data class MPTSelectionProof(
    val rootHash: ByteArray,
    val selectedTokenIds: List<String>,
    val inclusionProofs: List<InclusionProof>,
    val totalAmount: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MPTSelectionProof

        if (!rootHash.contentEquals(other.rootHash)) return false
        if (selectedTokenIds != other.selectedTokenIds) return false
        if (inclusionProofs != other.inclusionProofs) return false
        if (totalAmount != other.totalAmount) return false

        return true
    }

    override fun hashCode(): Int {
        var result = rootHash.contentHashCode()
        result = 31 * result + selectedTokenIds.hashCode()
        result = 31 * result + inclusionProofs.hashCode()
        result = 31 * result + totalAmount.hashCode()
        return result
    }
}

/**
 * Helper class for integrating MPT-based token selection with UI components
 * Bridges between the SendOfflineMoneyFragment and the MPT algorithm
 */
class MPTTokenSelectionHelper {

    private val mptSelector = MPTTokenSelector()

    /**
     * Main method to replace simple random selection with MPT-based selection
     * This method integrates directly with SendOfflineMoneyFragment
     */
    fun selectTokensForAmountMPT(
        unspentTokens: List<BillFaceToken>,
        amount: Long,
        merchantSeed: String
    ): List<BillFaceToken> {

        if (unspentTokens.isEmpty()) {
            return emptyList()
        }

        val totalAvailable = unspentTokens.sumOf { it.amount }
        if (totalAvailable < amount) {
            return emptyList()
        }

        return try {
            // Build MPT using existing implementation
            val mpt = mptSelector.buildTokenMPT(unspentTokens)

            // Use Algorithm 1 for deterministic selection
            val selectedTokens = mptSelector.chooseTokensFromMPT(merchantSeed, mpt, amount)

            // Verify selection integrity
            if (selectedTokens.isEmpty()) {
                return emptyList()
            }

            val selectedSum = selectedTokens.sumOf { it.amount }
            if (selectedSum < amount) {
                return emptyList()
            }

            selectedTokens

        } catch (e: Exception) {
            println("Error in MPT token selection: ${e.message}")
            emptyList()
        }
    }

    /**
     * Generate and verify MPT proof for selected tokens
     */
    fun generateSelectionProof(
        originalTokens: List<BillFaceToken>,
        selectedTokens: List<BillFaceToken>
    ): MPTSelectionProof {

        val mpt = mptSelector.buildTokenMPT(originalTokens)
        val inclusionProofs = mptSelector.generateTokenInclusionProof(mpt, selectedTokens)
        val rootHash = mpt.getRootHash()

        return MPTSelectionProof(
            rootHash = rootHash,
            selectedTokenIds = selectedTokens.map { it.id },
            inclusionProofs = inclusionProofs,
            totalAmount = selectedTokens.sumOf { it.amount }
        )
    }

    /**
     * Verify selection proof from merchant perspective
     */
    fun verifySelectionProof(
        proof: MPTSelectionProof,
        expectedSeed: String,
        expectedAmount: Long
    ): Boolean {

        return try {
            // Verify that all inclusion proofs are valid
            val allProofsValid = proof.inclusionProofs.all { it.isIncluded }

            // Verify total amount
            val correctAmount = proof.totalAmount >= expectedAmount

            allProofsValid && correctAmount

        } catch (e: Exception) {
            println("Error verifying selection proof: ${e.message}")
            false
        }
    }

    /**
     * Check if two token selections are identical (for double-spend detection)
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
     * Simulate multiple selections with same seed to verify determinism
     */
    fun testDeterministicSelection(
        tokens: List<BillFaceToken>,
        seed: String,
        amount: Long,
        iterations: Int = 3
    ): Boolean {

        return try {
            val mpt = mptSelector.buildTokenMPT(tokens)
            val baseSelection = mptSelector.chooseTokensFromMPT(seed, mpt, amount)

            repeat(iterations - 1) {
                val testSelection = mptSelector.chooseTokensFromMPT(seed, mpt, amount)
                if (!compareSelections(baseSelection, testSelection)) {
                    return false
                }
            }

            true

        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get selection statistics for debugging
     */
    fun getSelectionStats(
        tokens: List<BillFaceToken>,
        selectedTokens: List<BillFaceToken>
    ): SelectionStats {

        return SelectionStats(
            totalTokens = tokens.size,
            selectedTokens = selectedTokens.size,
            totalAmount = tokens.sumOf { it.amount },
            selectedAmount = selectedTokens.sumOf { it.amount },
            selectionRatio = selectedTokens.size.toDouble() / tokens.size.toDouble(),
            amountRatio = selectedTokens.sumOf { it.amount }.toDouble() / tokens.sumOf { it.amount }.toDouble()
        )
    }
}

/**
 * Selection statistics data class
 */
data class SelectionStats(
    val totalTokens: Int,
    val selectedTokens: Int,
    val totalAmount: Long,
    val selectedAmount: Long,
    val selectionRatio: Double,
    val amountRatio: Double
) {
    override fun toString(): String {
        return "SelectionStats(tokens: $selectedTokens/$totalTokens, " +
            "amount: $selectedAmount/$totalAmount, " +
            "ratios: ${String.format("%.2f", selectionRatio)}/${String.format("%.2f", amountRatio)})"
    }
}
