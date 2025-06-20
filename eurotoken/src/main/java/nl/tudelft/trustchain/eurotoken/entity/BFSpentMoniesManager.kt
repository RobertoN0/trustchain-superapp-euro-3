package nl.tudelft.trustchain.eurotoken.entity

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import nl.tudelft.trustchain.eurotoken.db.SimpleBloomFilter
import nl.tudelft.trustchain.eurotoken.db.TokenStore
import kotlin.math.*


/**
 * Double-Spending Prevention using Bloom Filters
 * Implements Algorithm 2 from the paper "Ad Hoc Prevention of Double-Spending in Offline Payments"
 */
class BFSpentMoniesManager(
    private val tokenStore: TokenStore,
    private val bloomFilterId: String,
    private val bloomFilterCapacity: Int = 240, // 240 bytes as per paper
    private val expectedItems: Int = 100,
    private val falsePositiveRate: Double = 0.03 // 3% FPR target
) {
    private val spendingIdGenerator = SpendingIdentifierGenerator()
    // Optimal hash functions based on target FPR (so it's dynamically set)
    private val numHashFunctions = SimpleBloomFilter.optimalNumOfHashFunctions(falsePositiveRate) // 3% FPR

    private val _bloomFilter = MutableStateFlow(SimpleBloomFilter(bloomFilterCapacity))
    val bloomFilter: StateFlow<SimpleBloomFilter> = _bloomFilter

    private val _receivedCount = MutableStateFlow(0)
    val receivedCount: StateFlow<Int> = _receivedCount

    // TODO: include a "StatisticalCollector" for collecting detailed statistics (idea: private class here)
    private var lastCleanupTime = System.currentTimeMillis()
    private val cleanupIntervalMs = 12 * 60 * 60 * 1000L // 12 hours as per paper

    companion object {
        private const val TAG = "BFSpentMoniesManager"

        const val RESULT_INCLUDED_RECEIVED = "INCLUDED_RECEIVED"
        const val RESULT_RESET_SHARED = "RESET_SHARED"
        const val RESULT_KEPT_PREVIOUS = "KEPT_PREVIOUS"
        const val RESULT_ONLY_OURS = "ONLY_OURS"
        const val RESULT_CAPACITY_EXCEEDED = "CAPACITY_EXCEEDED"
    }

    init {
        tokenStore.createBloomFilterTable()
        tokenStore.createReceivedTokensTable()
        val existing = tokenStore.getBloomFilter(bloomFilterId)
        if (existing == null) {
            val newSharedBloomFilter = SimpleBloomFilter(bloomFilterCapacity, numHashFunctions)
            updateBloomFilter(newSharedBloomFilter)
            Log.d(TAG, "Initialize Bloom filter")
        } else {
            _bloomFilter.value = existing
        }
    }

    /**
     * Add received money to our database with timestamp for expiration
     * Called when we receive a legitimate transaction
     */
    fun addReceivedMoney(tokens: List<BillFaceToken>) {
        val timestamp = System.currentTimeMillis()
        tokens.forEach { token ->
            token.dateReceived = timestamp
            tokenStore.saveReceivedToken(token)
        }
        // TODO: include statistics here from StatisticalCollector
        createSharedBloomFilter(null)
        Log.d(TAG, "Added received money")
        if (shouldPerformCleanup()) {
            performCleanup()
        }
    }

    /**
     * CORE DOUBLE-SENDING CHECK
     * Check if money has been spent (double-spending detection)
     */
    fun isDoubleSpent(tokens: List<BillFaceToken>): Boolean {
        val tokens_id = tokenStore.getReceivedTokenIds()
        Log.d(TAG, "Already received tokens: ${tokens_id.joinToString(", ")}")
        tokens.forEach { token ->
            Log.d(TAG, "Checking for token: ${token.id}")
            val existingToken = tokens_id.contains(token.id)
            if (existingToken) {
                Log.w(TAG, "Double-spending detected in local DB for token: ${token.id}")
                return true
            }

            getBloomFilter()?.let { filter ->
                if (filter.mightContain(token.id)) {
                    Log.w(TAG, "Potential double-spending detected in Bloom filter for token: ${token.id}")
                    return true
                }
            }
        }

        return false
    }

    /**
     * Create Bloom filter for sharing (Algorithm 2 implementation)
     * Creates shared Bloom filter according to the paper's algo
     *
     * @param receivedFilter Bloom filter received from another node (can be null)
     * @return Pair of (filter to share, algorithm result code)
     */
    fun createSharedBloomFilter(receivedFilter: SimpleBloomFilter?): Pair<SimpleBloomFilter?, String> {
        Log.d(TAG, "Creating shared Bloom filter. Received monies: ${getAllReceivedTokens().size}, " +
            "Expected capacity: $expectedItems")

        // Step 1: Check if we can include our received monies
        if (getAllReceivedTokens().size > expectedItems) {
            Log.w(TAG, "Too many received monies (${getAllReceivedTokens().size} > $expectedItems), " +
                "cannot include all in filter")
            // TODO: include "StatisticalCollector"
            return Pair(null, RESULT_CAPACITY_EXCEEDED)
        }

        // Step 2: Create filter from our monies (the 'simplest' but also we want to avoid that)
        val ourFilter = SimpleBloomFilter(bloomFilterCapacity, numHashFunctions)
        getAllReceivedTokens().forEach { token ->
            ourFilter.put(token.id)
        }
        Log.d(TAG, "Created our filter F_M with ${ourFilter.getApproximateSize()} items")

        // Step 3: Update our shared filter F_S ← F_S ∪ F_M
        val previousShared = getBloomFilter()?.copy() ?: ourFilter.copy()
        previousShared.merge(ourFilter)

        if (receivedFilter == null) {
            updateBloomFilter(previousShared)
            Log.d(TAG, "No received filter, using our shared filter")
            return Pair(previousShared, RESULT_ONLY_OURS)
        }

        // Step 4: Try to include received filter
        val combinedWithReceived = previousShared.copy()
        combinedWithReceived.merge(receivedFilter)

        if (combinedWithReceived.estimateSize() <= expectedItems) {
            // Success: include the received BF
            updateBloomFilter(combinedWithReceived)
            Log.d(TAG, "Successfully included received filter. Combined size: ${combinedWithReceived.estimateSize()}")
            // TODO: include "StatisticalCollector"
            return Pair(combinedWithReceived, RESULT_INCLUDED_RECEIVED)
        }

        Log.d(TAG, "Combined filter too large (${combinedWithReceived.estimateSize()} > $expectedItems), trying reset strategy")

        // Step 5: Try reset approach F_M ∪ F_R
        val resetFilter = ourFilter.copy()
        resetFilter.merge(receivedFilter)

        if (resetFilter.estimateSize() <= expectedItems) {
            // Reset the shared BF
            updateBloomFilter(resetFilter)
            Log.d(TAG, "Reset strategy successful. New filter size: ${resetFilter.estimateSize()}")
            return Pair(resetFilter, RESULT_RESET_SHARED)
        }

        Log.d(TAG, "Reset strategy also failed (${resetFilter.estimateSize()} > $expectedItems), keeping previous")

        // Step 6: Keep previous or return our own
        if (previousShared.estimateSize() <= expectedItems) {
            updateBloomFilter(previousShared)
            Log.d(TAG, "Keeping previous shared filter. Size: ${previousShared.estimateSize()}")
            return Pair(previousShared, RESULT_KEPT_PREVIOUS)
        } else {
            updateBloomFilter(ourFilter)
            Log.d(TAG, "Previous filter too large, using only our filter. Size: ${ourFilter.estimateSize()}")
            return Pair(ourFilter, RESULT_ONLY_OURS)
        }
    }

    /**
     * Process received Bloom filter from other nodes
     * This is called when we receive a broadcast message
     */
    fun processReceivedBloomFilter(filterBytes: ByteArray) : Boolean {
        return try {
            val receivedFilter = SimpleBloomFilter.fromByteArray(filterBytes, numHashFunctions)
            val (newSharedFilter,resultCode)  = createSharedBloomFilter(receivedFilter)

            Log.d(TAG, "Processed received Bloom filter. Result: $resultCode, " +
                "New shared filter size: ${newSharedFilter?.estimateSize() ?: 0}")

            _receivedCount.update { it + 1 }
            newSharedFilter != null

        } catch (e: Exception) {
            Log.e(TAG, "Error processing received Bloom filter", e)
            // TODO: StatisticalCollector
            false
        }
    }

    /**
     * Get current shared Bloom filter for broadcasting to other nodes
     */
    fun getSharedBloomFilterBytes(): ByteArray? {
        return getBloomFilter()?.toByteArray()
    }

    /**
     * Check if we have a valid filter to share
     */
    fun hasValidFilterToShare(): Boolean {
        return getBloomFilter() != null &&
            getBloomFilter()!!.getApproximateSize() > 0 &&
            getBloomFilter()!!.estimateSize() <= expectedItems
    }

    /**
     * Get filter info for broadcasting decisions
     */
    fun getFilterInfo(): Map<String, Any> {
        val filter = getBloomFilter()
        return if (filter != null) {
            mapOf(
                "hasFilter" to true,
                "approximateSize" to filter.getApproximateSize(),
                "estimatedSize" to filter.estimateSize(),
                "falsePositiveRate" to filter.calculateFalsePositiveRate(),
                "capacityBytes" to filter.getCapacityBytes()
            )
        } else {
            mapOf("hasFilter" to false)
        }
    }

    /**
     * Periodic cleanup of expired data (called every 12 hours per paper recommendation)
     */
    fun performCleanup() {
        val currentTime = System.currentTimeMillis()
        val expirationThreshold = currentTime - cleanupIntervalMs

        // Remove expired received monies
        val sizeBefore = getAllReceivedTokens().size
        val expiredTokens = getAllReceivedTokens().filter { token ->
            token.dateReceived!! < expirationThreshold
        }
        expiredTokens.forEach { expired ->
            tokenStore.deleteReceivedToken(expired.id)
        }
        val sizeAfter = getAllReceivedTokens().size

        if (getAllReceivedTokens().isEmpty()) {
            val newSharedBloomFilter = SimpleBloomFilter(bloomFilterCapacity, numHashFunctions)
            updateBloomFilter(newSharedBloomFilter)
        }

        lastCleanupTime = currentTime

        Log.d(TAG, "Cleanup completed. Removed ${sizeBefore - sizeAfter} expired entries. " +
            "Remaining: $sizeAfter")

    }

    /**
     * Force clear all data (for testing or reset)
     */
    fun clearAllData() {
        getAllReceivedTokens().forEach { token ->
            tokenStore.deleteReceivedToken(token.id)
        }
        val newSharedBloomFilter = SimpleBloomFilter(bloomFilterCapacity, numHashFunctions)
        updateBloomFilter(newSharedBloomFilter)
        Log.d(TAG, "All data cleared")
    }

    /**
     * Check if cleanup should be performed
     */
    private fun shouldPerformCleanup(): Boolean {
        return System.currentTimeMillis() - lastCleanupTime > cleanupIntervalMs
    }

    /**
     * Clear expired data
     */
    fun clearExpiredData() {
        getAllReceivedTokens().forEach { token ->
            tokenStore.deleteReceivedToken(token.id)
        }
        val newSharedBloomFilter = SimpleBloomFilter(bloomFilterCapacity, numHashFunctions)
        updateBloomFilter(newSharedBloomFilter)
        Log.d(TAG, "Cleared expired data")
    }

    fun getBloomFilter(): SimpleBloomFilter? {
        return tokenStore.getBloomFilter(bloomFilterId)
    }

    fun getTokenById(tokenId: String): BillFaceToken? {
        return tokenStore.getToken(tokenId)
    }

    fun getAllReceivedTokens(): List<BillFaceToken> {
        return tokenStore.getAllReceivedTokens()
    }

    private fun updateBloomFilter(bloomFilter: SimpleBloomFilter) {
        tokenStore.saveBloomFilter(bloomFilterId, bloomFilter)
        _bloomFilter.value = bloomFilter
    }

    /**
     * Get statistics for monitoring
     */
    fun getStatistics(): Map<String, Any> {
        return mapOf(
            "receivedMoniesCount" to getAllReceivedTokens().size,
            "sharedFilterSize" to (getBloomFilter()?.estimateSize() ?: 0),
            "sharedFilterCapacity" to bloomFilterCapacity,
            "expectedItems" to expectedItems,
            "falsePositiveRate" to calculateFalsePositiveRate(),
            "lastCleanupTime" to lastCleanupTime,
            "timeSinceLastCleanup" to (System.currentTimeMillis() - lastCleanupTime)
        )
    }

    private fun calculateFalsePositiveRate(): Double {
        val filter = getBloomFilter() ?: return 0.0
        val bitsSet = filter.getBitSize() - filter.toByteArray().count { it == 0.toByte() } * 8
        val totalBits = filter.getBitSize()
        val ratio = bitsSet.toDouble() / totalBits
        return ratio.pow(numHashFunctions.toDouble())
    }
}
