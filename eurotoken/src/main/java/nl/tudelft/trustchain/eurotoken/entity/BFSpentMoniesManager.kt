package nl.tudelft.trustchain.eurotoken.entity

import android.util.Log
import nl.tudelft.trustchain.eurotoken.db.SimpleBloomFilter
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*


/**
 * Double-Spending Prevention using Bloom Filters
 * Implements Algorithm 2 from the paper "Ad Hoc Prevention of Double-Spending in Offline Payments"
 */
class BFSpentMoniesManager(
    private val bloomFilterCapacity: Int = 240, // 240 bytes as per paper
    private val expectedItems: Int = 100,
    private val falsePositiveRate: Double = 0.03 // 3% FPR target
) {
    private val spendingIdGenerator = SpendingIdentifierGenerator()
    private var receivedMonies = ConcurrentHashMap<String, Long>() // ID -> timestamp
    private var sharedBloomFilter: SimpleBloomFilter? = null

    // Optimal hash functions based on target FPR (so it's dynamically set)
    private val numHashFunctions = SimpleBloomFilter.optimalNumOfHashFunctions(falsePositiveRate) // 3% FPR


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

    /**
     * Add received money to our local set with timestamp for expiration
     * Called when we receive a legitimate transaction
     */
    fun addReceivedMoney(transaction: Transaction) {
        val spendingId = generateSpendingId(transaction)
        val timestamp = System.currentTimeMillis()

        receivedMonies[spendingId] = timestamp
        // TODO: include statistics here from StatisticalCollector
        Log.d(TAG, "Added received money with ID: $spendingId (total: ${receivedMonies.size})")

        if (shouldPerformCleanup()) {
            performCleanup()
        }
    }

    /**
     * CORE DOUBLE-SENDING CHECK
     * Check if money has been spent (double-spending detection)
     */
    fun isDoubleSpent(transaction: Transaction): Boolean {
        val spendingId = generateSpendingId(transaction)

        // Check against our own received monies
        if (receivedMonies.containsKey(spendingId)) {
            Log.w(TAG, "Double-spending detected in local set: $spendingId")
            // TODO: include statistics here from StatisticalCollector
            return true
        }

        // Check against shared Bloom filter
        sharedBloomFilter?.let { filter ->
            if (filter.mightContain(spendingId)) {
                Log.w(TAG, "Potential double-spending detected in Bloom filter: $spendingId")
                // TODO: include statistics here from StatisticalCollector
                return true
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
        Log.d(TAG, "Creating shared Bloom filter. Received monies: ${receivedMonies.size}, " +
            "Expected capacity: $expectedItems")

        // Step 1: Check if we can include our received monies
        if (receivedMonies.size > expectedItems) {
            Log.w(TAG, "Too many received monies (${receivedMonies.size} > $expectedItems), " +
                "cannot include all in filter")
            // TODO: include "StatisticalCollector"
            return Pair(null, RESULT_CAPACITY_EXCEEDED)
        }

        // Step 2: Create filter from our monies (the 'simplest' but also we want to avoid that)
        val ourFilter = SimpleBloomFilter(bloomFilterCapacity, numHashFunctions)
        receivedMonies.keys.forEach { spendingId ->
            ourFilter.put(spendingId)
        }
        Log.d(TAG, "Created our filter F_M with ${ourFilter.getApproximateSize()} items")

        // Step 3: Update our shared filter F_S ← F_S ∪ F_M
        val previousShared = sharedBloomFilter?.copy() ?: ourFilter.copy()
        previousShared.merge(ourFilter)

        if (receivedFilter == null) {
            sharedBloomFilter = previousShared
            Log.d(TAG, "No received filter, using our shared filter")
            return Pair(previousShared, RESULT_ONLY_OURS)
        }

        // Step 4: Try to include received filter
        val combinedWithReceived = previousShared.copy()
        combinedWithReceived.merge(receivedFilter)

        if (combinedWithReceived.estimateSize() <= expectedItems) {
            // Success: include the received BF
            sharedBloomFilter = combinedWithReceived
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
            sharedBloomFilter = resetFilter
            Log.d(TAG, "Reset strategy successful. New filter size: ${resetFilter.estimateSize()}")
            return Pair(resetFilter, RESULT_RESET_SHARED)
        }

        Log.d(TAG, "Reset strategy also failed (${resetFilter.estimateSize()} > $expectedItems), keeping previous")

        // Step 6: Keep previous or return our own
        if (previousShared.estimateSize() <= expectedItems) {
            sharedBloomFilter = previousShared
            Log.d(TAG, "Keeping previous shared filter. Size: ${previousShared.estimateSize()}")
            return Pair(previousShared, RESULT_KEPT_PREVIOUS)
        } else {
            sharedBloomFilter = ourFilter
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
        return sharedBloomFilter?.toByteArray()
    }

    /**
     * Check if we have a valid filter to share
     */
    fun hasValidFilterToShare(): Boolean {
        return sharedBloomFilter != null &&
            sharedBloomFilter!!.getApproximateSize() > 0 &&
            sharedBloomFilter!!.estimateSize() <= expectedItems
    }

    /**
     * Get filter info for broadcasting decisions
     */
    fun getFilterInfo(): Map<String, Any> {
        val filter = sharedBloomFilter
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
        val sizeBefore = receivedMonies.size
        receivedMonies.entries.removeIf { (_, timestamp) ->
            timestamp < expirationThreshold
        }
        val sizeAfter = receivedMonies.size

        // Reset shared filter if it becomes too sparse
        if (receivedMonies.isEmpty()) {
            sharedBloomFilter = null
        }

        lastCleanupTime = currentTime

        Log.d(TAG, "Cleanup completed. Removed ${sizeBefore - sizeAfter} expired entries. " +
            "Remaining: $sizeAfter")

    }

    /**
     * Force clear all data (for testing or reset)
     */
    fun clearAllData() {
        receivedMonies.clear()
        sharedBloomFilter = null
        Log.d(TAG, "All data cleared")
    }

    /**
     * Check if cleanup should be performed
     */
    private fun shouldPerformCleanup(): Boolean {
        return System.currentTimeMillis() - lastCleanupTime > cleanupIntervalMs
    }

    /**
     * Generate spending identifier for transaction
     */
    private fun generateSpendingId(transaction: Transaction): String {
        // TODO: We must change the method used, based on the money representation. (please do not modify the SpendingIdentifierGenerator interface)
        // For now we can leave it like this.
        val transactionId = transaction.id
        return spendingIdGenerator.generateForUTxO(transactionId)
    }

    /**
     * Clear expired data
     */
    fun clearExpiredData() {
        receivedMonies.clear()
        sharedBloomFilter = null
        Log.d(TAG, "Cleared expired data")
    }

    /**
     * Get statistics for monitoring
     */
    fun getStatistics(): Map<String, Any> {
        return mapOf(
            "receivedMoniesCount" to receivedMonies.size,
            "sharedFilterSize" to (sharedBloomFilter?.estimateSize() ?: 0),
            "sharedFilterCapacity" to bloomFilterCapacity,
            "expectedItems" to expectedItems,
            "falsePositiveRate" to calculateFalsePositiveRate(),
            "lastCleanupTime" to lastCleanupTime,
            "timeSinceLastCleanup" to (System.currentTimeMillis() - lastCleanupTime)
        )
    }

    private fun calculateFalsePositiveRate(): Double {
        val filter = sharedBloomFilter ?: return 0.0
        val bitsSet = filter.getBitSize() - filter.toByteArray().count { it == 0.toByte() } * 8
        val totalBits = filter.getBitSize()
        val ratio = bitsSet.toDouble() / totalBits
        return ratio.pow(numHashFunctions.toDouble())
    }
}
