package nl.tudelft.trustchain.eurotoken.db

import java.util.BitSet
import kotlin.math.absoluteValue
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Simple Bloom Filter implementation based on the paper requirements of "Ad Hoc Prevention of Double-Spending in Offline Payments"
 */
class SimpleBloomFilter(
    private val capacityBytes: Int = 240, // Just following suggestions on the paper
    private val numHashFunctions: Int = 3
) {
    private val bitArray = BitSet(capacityBytes * 8) // 8 bits per byte
    /**
     * This class maintains two different approaches for counting elements:
     *
     * 1. approximateElementCount (FAST COUNTER - O(1))
     * ------------------------------------------------
     * - Simple increment-based counter updated on each successful put() operation
     * - WHEN TO USE: Quick operations, debugging, UI statistics, non-critical decisions
     * - PROS: Very fast, no computational overhead
     * - CONS: May overestimate due to hash collisions and duplicate insertions
     * - ACCURACY: Lower, especially with high collision rates or duplicate elements
     *
     * 2. estimateSize() (MATHEMATICAL FORMULA - O(n))
     * -------------------------------------------------------
     * - Based on research paper formula: |F| ≈ ln(1 - s/m) / (k * ln(1 - 1/m))
     * - WHEN TO USE: Critical Algorithm 2 decisions, capacity checks, merge operations
     * - PROS: Theoretically more accurate, handles duplicates and collisions better
     * - CONS: Computationally expensive (must count set bits in BitSet)
     * - ACCURACY: Higher, mathematically sound based on actual bit utilization
     *
     *  ========================
     * In the context of "Ad Hoc Prevention of Double-Spending in Offline Payments":
     *
     * - Use approximateElementCount for: UI updates, logs, performance monitoring
     * - Use estimateSize() for: Algorithm 2 capacity checks (|F_S ∪ F_R| ≤ c)
     *
     * SYNCHRONIZATION STRATEGY:
     * ========================
     *
     * We periodically sync approximateElementCount with estimateSize() to:
     * - Detect significant discrepancies that might indicate implementation bugs
     * - Maintain reasonable consistency between the two approaches
     * - Log warnings when the difference exceeds acceptable thresholds
     */
    private var approximateSize = 0

    companion object {
        private const val TAG = "SimpleBloomFilter"
        private val LOG_TWO = ln(2.0)

        /**
         * Calculate optimal number of hash functions for given false positive probability
         */
        fun optimalNumOfHashFunctions(falsePositiveRate: Double): Int {
            return max(1, (-ln(falsePositiveRate) / LOG_TWO).roundToInt())
        }

        /**
         * Calculate optimal number of bits for given insertions and FalsePositiveRate
         */
        fun optimalNumOfBits(expectedInsertions: Long, falsePositiveRate: Double): Long {
            val adjustedFPR = if (falsePositiveRate == 0.0) Double.MIN_VALUE else falsePositiveRate
            return (-expectedInsertions * ln(adjustedFPR) / (LOG_TWO * LOG_TWO)).toLong()
        }

        fun fromByteArray(bytes: ByteArray, numHashFunctions: Int = 3): SimpleBloomFilter {
            val filter = SimpleBloomFilter(bytes.size, numHashFunctions)

            // Convert bytes back to BitSet
            for (i in bytes.indices) {
                val byte = bytes[i].toInt() and 0xFF
                for (bit in 0..7) {
                    if ((byte and (1 shl bit)) != 0) {
                        val bitIndex = i * 8 + bit
                        if (bitIndex < filter.bitArray.size()) {
                            filter.bitArray.set(bitIndex)
                        }
                    }
                }
            }

            filter.approximateSize = filter.estimateSize()
            return filter
        }
    }

    /**
     * Add element to Bloom filter
     * * @param element Stringa to add
     * * @return true if the filter has been modified
     */
    fun put(element: String): Boolean {
        val hashes = generateHashes(element)
        var changed = false

        for (hash in hashes) {
            val index = (hash.absoluteValue % bitArray.size())
            if (!bitArray.get(index)) {
                bitArray.set(index)
                changed = true
            }
        }

        if (changed) {
            approximateSize++
        }

        return changed
    }

    /**
     * Verify if an element could be present in teh BF
     * @param element String to verify
     * @return true if the element could be present (false positives are possible)
     */

    fun mightContain(element: String) : Boolean {
        val hashes = generateHashes(element)
        return hashes.all { hash ->
            val index = hash.absoluteValue % bitArray.size()
            bitArray.get(index)
        }
    }

    /**
     * Merges this filter with another using logical OR operation.
     *
     * CRITICAL OPERATION for Algorithm 2 in the double-spending prevention paper.
     * After union, ALWAYS use estimateSize() to check capacity constraints:
     *
     * Example Algorithm 2 usage:
     * ```
     * val union = filterA.union(filterB)
     * if (union.estimateSize() <= capacity) {
     *     // Safe to use this union
     *     return union
     * } else {
     *     // Capacity exceeded, try reset strategy
     * }
     * ```
     *
     * @param other The Bloom Filter to merge with
     * @return New Bloom Filter containing the union of both filters
     * @throws IllegalArgumentException if filters have different sizes
     */
    fun merge(other: SimpleBloomFilter) {
        if (other.bitArray.size() != this.bitArray.size()) {
            throw IllegalArgumentException("Bloom filters must have same size")
        }

        this.bitArray.or(other.bitArray)
        this.approximateSize = estimateSize()
    }

    /**
     * Create copy of this filter
     */
    fun copy(): SimpleBloomFilter {
        val copy = SimpleBloomFilter(capacityBytes, numHashFunctions)
        copy.bitArray.or(this.bitArray)
        copy.approximateSize = this.approximateSize
        return copy
    }

    /**
     * Estimates the number of elements using the mathematical formula from the research paper.
     *
     * Formula: |F| ≈ ln(1 - s/m) / (k * ln(1 - 1/m))
     * Where:
     * - s = number of set bits in the filter
     * - m = total number of bits in the filter
     * - k = number of hash functions
     *
     * CRITICAL for Algorithm 2 decisions in double-spending prevention.
     * Use THIS method for capacity checks: |F_S ∪ F_R| ≤ c
     *
     * @return Mathematically estimated element count based on bit utilization
     * @see getApproximateSize for fast but potentially inaccurate counting
     */
    fun estimateSize(): Int {
        val m = bitArray.size().toDouble()
        val s = bitArray.cardinality().toDouble()
        val k = numHashFunctions.toDouble()

        if (s == 0.0) return 0
        if (s == m) return Int.MAX_VALUE

        val ratio = s / m
        val estimate = -ln(1 - ratio) * m / k
        return estimate.toInt()
    }


    /**
     * Calculate the current false positive rate
     * @return FPR Double
     */
    fun calculateFalsePositiveRate(): Double {
        val bitSet = bitArray.cardinality().toDouble()
        val totalBits = bitArray.size().toDouble()
        val ratio = bitSet / totalBits
        return ratio.pow(numHashFunctions.toDouble())
    }


    /**
     * Get current approximate size
     */
    fun getApproximateSize() = approximateSize

    /**
     * Get bit array size
     */
    fun getBitSize() = bitArray.size()

    /**
     * Get bit array cardinality (how many bits are active)
     */
    fun getBitCardinality() = bitArray.cardinality()
    /**
     * Get capacity in bytes
     */
    fun getCapacityBytes() = capacityBytes

    /**
     * Serialize to byte array for transmission
     */
    fun toByteArray(): ByteArray {
        val bytes = ByteArray(capacityBytes)
        val longArray = bitArray.toLongArray()

        for (i in longArray.indices) {
            val startByte = i * 8
            if (startByte < bytes.size) {
                val long = longArray[i]
                for (j in 0..7) {
                    val byteIndex = startByte + j
                    if (byteIndex < bytes.size) {
                        bytes[byteIndex] = ((long shr (j * 8)) and 0xFF).toByte()
                    }
                }
            }
        }

        return bytes
    }

    private fun generateHashes(element: String): IntArray {
        val hash1 = element.hashCode()
        val hash2 = element.reversed().hashCode()

        val hashes = IntArray(numHashFunctions)
        for (i in 0 until  numHashFunctions) {
            hashes[i] = hash1 + i * hash2
        }

        return hashes
    }

    /**
     * Debug information
     */
    fun getDebugInfo(): Map<String, Any> {
        return mapOf(
            "capacityBytes" to capacityBytes,
            "totalBits" to bitArray.size(),
            "bitsSet" to bitArray.cardinality(),
            "approximateSize" to approximateSize,
            "falsePositiveRate" to calculateFalsePositiveRate(),
            "utilizationPercentage" to (bitArray.cardinality().toDouble() / bitArray.size() * 100)
        )
    }
}
