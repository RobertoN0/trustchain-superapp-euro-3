package nl.tudelft.trustchain.eurotoken.entity

import android.util.Log
import nl.tudelft.trustchain.eurotoken.db.SimpleBloomFilter
import kotlin.math.*


/**
 * Double-Spending Prevention using Bloom Filters
 * Implements Algorithm 2 from the paper "Ad Hoc Prevention of Double-Spending in Offline Payments"
 */
class BFSpentMoniesManager(
    private val bloomFilterCapacity: Int = 240, // 240 bytes as per paper
    private val expectedItems: Int = 100
) {
    private val spendingIdGenerator = SpendingIdentifierGenerator()
    private var receivedMonies = mutableSetOf<String>()
    private var sharedBloomFilter: SimpleBloomFilter? = null
    private val numHashFunctions = SimpleBloomFilter.optimalNumOfHashFunctions(0.03) // 3% FPP

    companion object {
        private const val TAG = "BFSpentMoniesManager"
    }

    /**
     * Add received money to our local set
     */
    fun addReceivedMoney(transaction: Transaction) {
        val spendingId = generateSpendingId(transaction)
        receivedMonies.add(spendingId)
        Log.d(TAG, "Added received money with ID: $spendingId")
    }

    /**
     * Check if money has been spent (double-spending detection)
     */
    fun isDoubleSpent(transaction: Transaction): Boolean {
        val spendingId = generateSpendingId(transaction)

        // Check against our own received monies
        if (receivedMonies.contains(spendingId)) {
            Log.w(TAG, "Double-spending detected in local set: $spendingId")
            return true
        }

        // Check against shared Bloom filter
        sharedBloomFilter?.let { filter ->
            if (filter.mightContain(spendingId)) {
                Log.w(TAG, "Potential double-spending detected in Bloom filter: $spendingId")
                return true
            }
        }

        return false
    }

    /**
     * Create Bloom filter for sharing (Algorithm 2 implementation)
     */
    fun createSharedBloomFilter(receivedFilter: SimpleBloomFilter?): SimpleBloomFilter? {
        // Step 1: Check if we can include our received monies
        if (receivedMonies.size > expectedItems) {
            Log.w(TAG, "Too many received monies, cannot include all")
            return null
        }

        // Step 2: Create filter from our monies
        val ourFilter = SimpleBloomFilter(bloomFilterCapacity, numHashFunctions)
        receivedMonies.forEach { spendingId ->
            ourFilter.put(spendingId)
        }

        // Step 3: Update our shared filter
        val previousShared = sharedBloomFilter?.copy() ?: ourFilter.copy()
        previousShared.putAll(ourFilter)

        if (receivedFilter == null) {
            sharedBloomFilter = previousShared
            return previousShared
        }

        // Step 4: Try to include received filter
        val combinedWithReceived = previousShared.copy()
        combinedWithReceived.putAll(receivedFilter)

        if (combinedWithReceived.estimateSize() <= expectedItems) {
            // Include the received BF
            sharedBloomFilter = combinedWithReceived
            return combinedWithReceived
        }

        // Step 5: Try reset approach
        val resetFilter = ourFilter.copy()
        resetFilter.putAll(receivedFilter)

        if (resetFilter.estimateSize() <= expectedItems) {
            // Reset the shared BF
            sharedBloomFilter = resetFilter
            return resetFilter
        }

        // Step 6: Keep previous or return our own
        if (previousShared.estimateSize() <= expectedItems) {
            sharedBloomFilter = previousShared
            return previousShared
        } else {
            sharedBloomFilter = ourFilter
            return ourFilter
        }
    }

    /**
     * Process received Bloom filter from other nodes
     */
    fun processReceivedBloomFilter(filterBytes: ByteArray) {
        try {
            val receivedFilter = SimpleBloomFilter.fromByteArray(filterBytes, numHashFunctions)
            val newSharedFilter = createSharedBloomFilter(receivedFilter)

            Log.d(TAG, "Processed received Bloom filter. New shared filter size: ${newSharedFilter?.estimateSize()}")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing received Bloom filter", e)
        }
    }

    /**
     * Get current shared Bloom filter for broadcasting
     */
    fun getSharedBloomFilterBytes(): ByteArray? {
        return sharedBloomFilter?.toByteArray()
    }

    /**
     * Generate spending identifier for transaction
     */
    private fun generateSpendingId(transaction: Transaction): String {
        // Use transaction ID as spending identifier (UTxO approach)
        // In a real implementation, this should be adapted based on the money representation used
        return transaction.id
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
            "falsePositiveRate" to calculateFalsePositiveRate()
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
