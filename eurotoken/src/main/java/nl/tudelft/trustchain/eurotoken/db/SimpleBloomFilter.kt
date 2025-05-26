package nl.tudelft.trustchain.eurotoken.db

import android.os.Build
import androidx.annotation.RequiresApi
import java.util.BitSet
import kotlin.math.absoluteValue
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Simple Bloom Filter implementation based on the paper requirements of "Ad Hoc Prevention of Double-Spending in Offline Payments"
 */
class SimpleBloomFilter(
    private val capacity: Int,
    private val numHashFunctions: Int = 3
) {
    private val bitArray = BitSet(capacity * 8) // 8 bits per byte
    private var approximateSize = 0

    companion object {
        private val LOG_TWO = ln(2.0)

        /**
         * Calculate optimal number of hash functions for given false positive probability
         */
        fun optimalNumOfHashFunctions(fpp: Double): Int {
            return max(1, (-ln(fpp) / LOG_TWO).roundToInt())
        }

        /**
         * Calculate optimal number of bits for given insertions and FPP
         */
        fun optimalNumOfBits(expectedInsertions: Long, fpp: Double): Long {
            val adjustedFpp = if (fpp == 0.0) Double.MIN_VALUE else fpp
            return (-expectedInsertions * ln(adjustedFpp) / (LOG_TWO * LOG_TWO)).toLong()
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

    fun mightContain(element: String) : Boolean {
        val hashes = generateHashes(element)
        return hashes.all { hash ->
            val index = hash.absoluteValue % bitArray.size()
            bitArray.get(index)
        }
    }

    /**
     * Merge this filter with another using logical OR
     */
    fun putAll(other: SimpleBloomFilter) {
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
        val copy = SimpleBloomFilter(capacity, numHashFunctions)
        copy.bitArray.or(this.bitArray)
        copy.approximateSize = this.approximateSize
        return copy
    }

    /**
     * Estimate current size using formula from paper
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
     * Get current approximate size
     */
    fun getApproximateSize() = approximateSize

    /**
     * Get bit array size
     */
    fun getBitSize() = bitArray.size()

    /**
     * Get capacity in bytes
     */
    fun getCapacityBytes() = capacity

    /**
     * Serialize to byte array for transmission
     */
    fun toByteArray(): ByteArray {
        val bytes = ByteArray(capacity)
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
}
