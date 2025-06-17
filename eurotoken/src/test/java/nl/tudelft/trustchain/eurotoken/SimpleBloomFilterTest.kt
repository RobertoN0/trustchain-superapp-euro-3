//package nl.tudelft.trustchain.eurotoken
//
//import nl.tudelft.trustchain.eurotoken.db.SimpleBloomFilter
//import org.junit.Test
//import org.junit.Assert.*
//import org.junit.Before
//import org.junit.jupiter.api.Disabled
//import kotlin.math.abs
//
///**
// * Unit Tests for SimpleBloomFilter
// *
// * These tests verify:
// * 1. Basic functionality
// * 2. Serialization/Deserialization
// * 3. Union Operations
// * 4. Statistical estimates/computations
// * 5. Edge case behaviours
// */
//@Disabled
//class SimpleBloomFilterTest {
//
//    private lateinit var filter: SimpleBloomFilter
//    private val testCapacity = 240 // 240 bytes as the paper
//
//    @Before
//    fun setUp() {
//        filter = SimpleBloomFilter(testCapacity, 3)
//    }
//
//    @Test
//    fun `test basic element insertion and lookup`() {
//        // Given
//        val testElements = listOf("element1", "element2", "element3")
//
//        // When - element insertion
//        testElements.forEach { element ->
//            val wasAdded = filter.put(element)
//            assertTrue("Element should be added the first time", wasAdded)
//        }
//
//        // Then - verify presence
//        testElements.forEach { element ->
//            assertTrue("Inserted element should be present", filter.mightContain(element))
//        }
//
//        // And - verify not inserted element
//        assertFalse("Non-inserted element should not be present",
//            filter.mightContain("non_existent_element"))
//    }
//
//    @Test
//    fun `test duplicate insertion`() {
//        // Given
//        val element = "test_element"
//
//        // When
//        val firstInsert = filter.put(element)
//        val secondInsert = filter.put(element)
//
//        // Then
//        assertTrue("First insertion should modify the filter", firstInsert)
//        assertFalse("Second insertion should not modify the filter", secondInsert)
//
//        // And
//        assertTrue("Element should be present after both insertions",
//            filter.mightContain(element))
//    }
//
//    @Test
//    fun `test serialization and deserialization`() {
//        // Given
//        val testElements = listOf("tx1", "tx2", "tx3", "tx4", "tx5")
//        testElements.forEach { filter.put(it) }
//
//        val originalCount = filter.getApproximateSize()
//        val originalFPR = filter.calculateFalsePositiveRate()
//
//        // When
//        val serialized = filter.toByteArray()
//        val deserialized = SimpleBloomFilter.fromByteArray(serialized, 3)
//
//        // Then
//        assertEquals("Serialized array should have correct capacity",
//            testCapacity, serialized.size)
//
//        // Verify that all original elements are present
//        testElements.forEach { element ->
//            assertTrue("Element '$element' should be present after deserialization",
//                deserialized.mightContain(element))
//        }
//
//        // Verify similar statistics (might differ slightly)
//        val deserializedCount = deserialized.getApproximateSize()
//        val deserializedFPR = deserialized.calculateFalsePositiveRate()
//
//        assertTrue("Element count should be similar",
//            abs(originalCount - deserializedCount) <= 1)
//        assertEquals("False positive rate should be equal",
//            originalFPR, deserializedFPR, 0.001)
//    }
//
//
//    @Test
//    fun `test union operation`() {
//        // Given
//        val filter1 = SimpleBloomFilter(testCapacity, 3)
//        val filter2 = SimpleBloomFilter(testCapacity, 3)
//
//        val elements1 = listOf("tx1", "tx2", "tx3")
//        val elements2 = listOf("tx4", "tx5", "tx6")
//        val commonElement = "common_tx"
//
//        elements1.forEach { filter1.put(it) }
//        elements2.forEach { filter2.put(it) }
//
//        filter1.put(commonElement)
//        filter2.put(commonElement)
//
//        // When
//        filter1.merge(filter2)
//
//        // Then
//        // All elements from both filters should be present
//        (elements1 + elements2 + commonElement).forEach { element ->
//            assertTrue("Element '$element' should be present in union",
//                filter1.mightContain(element))
//        }
//
//        // Count should be approximately the sum minus common elements
//        val expectedCount = elements1.size + elements2.size // common element counted once
//        val actualCount = filter1.getApproximateSize()
//        assertTrue("Union count should be reasonable",
//            actualCount >= expectedCount - 2 && actualCount <= expectedCount + 2)
//    }
//
//    @Test
//    fun `test union with different sized filters should fail`() {
//        // Given
//        val filter1 = SimpleBloomFilter(240, 3)
//        val filter2 = SimpleBloomFilter(480, 3) // Different dimension
//
//        // When & Then
//        try {
//            filter1.merge(filter2)
//            fail("Union of filters with different dimension should raise an exception")
//        } catch (e: IllegalArgumentException) {
//            assertTrue("IllegalArgumentException should mention the dimension",
//                e.message?.contains("same size") == true)
//        }
//    }
//
//    @Test
//    fun `test false positive rate calculation`() {
//        // Given - add some elements
//        val elements = (1..10).map { "element_$it" }
//        elements.forEach { filter.put(it) }
//
//        // When
//        val fpr = filter.calculateFalsePositiveRate()
//
//        // Then
//        assertTrue("False positive rate should be > 0", fpr > 0.0)
//        assertTrue("False positive rate should be < 1", fpr < 1.0)
//        assertTrue("False positive rate should be reasonable (< 0.1)", fpr < 0.1)
//    }
//
//    @Test
//    fun `test element count estimation`() {
//        // Given
//        val elements = (1..20).map { "tx_$it" }
//
//        // When
//        elements.forEach { filter.put(it) }
//
//        // Then
//        val estimatedCount = filter.estimateSize()
//        assertTrue("Estimate should be reasonable close to real count",
//            abs(estimatedCount - elements.size) <= 5)
//
//        assertTrue("Approximate count should correspond to the estimate",
//            abs(estimatedCount - filter.getApproximateSize()) <= 2)
//    }
//
//    @Test
//    fun `test empty filter properties`() {
//        // When
//        val emptyFilter = SimpleBloomFilter(240, 3)
//
//        // Then
//        assertEquals("Empty filter should have 0 elements", 0, emptyFilter.getApproximateSize())
//        assertEquals("Empty filter should have 0 bit set", 0, emptyFilter.getBitCardinality())
//        assertEquals("Empty filter shouldh have FPR = 0", 0.0, emptyFilter.calculateFalsePositiveRate(), 0.001)
//
//        assertFalse("Empty filter should not contain elements",
//            emptyFilter.mightContain("anything"))
//    }
//
//    @Test
//    fun `test filter copy`() {
//        // Given
//        val originalElements = listOf("tx1", "tx2", "tx3")
//        originalElements.forEach { filter.put(it) }
//
//        // When
//        val copy = filter.copy()
//
//        // Then
//        originalElements.forEach { element ->
//            assertTrue("Copy should contain '$element'", copy.mightContain(element))
//        }
//
//        copy.put("new_element")
//        assertTrue("The copy should contain a new element", copy.mightContain("new_element"))
//        assertFalse("The original shouldn't contain the new element", filter.mightContain("new_element"))
//
//        assertEquals("Approximate number of elements should be equal",
//            filter.getApproximateSize(), copy.getApproximateSize() - 1)
//    }
//
//    @Test
//    fun `test optimal hash functions calculation`() {
//        // When & Then
//        assertEquals("For FPR 0.03 we should have 3 hash functions",
//            5, SimpleBloomFilter.optimalNumOfHashFunctions(0.03))
//
//        assertEquals("For FPR 0.01 we should have more hash functions",
//            7, SimpleBloomFilter.optimalNumOfHashFunctions(0.01))
//
//        assertEquals("For FPR 0.1 we should have 3 less functions",
//            3, SimpleBloomFilter.optimalNumOfHashFunctions(0.1))
//    }
//
//    @Test
//    fun `test optimal bit count calculation`() {
//        // When
//        val bits100Elements = SimpleBloomFilter.optimalNumOfBits(100, 0.03)
//        val bits1000Elements = SimpleBloomFilter.optimalNumOfBits(1000, 0.03)
//
//        // Then
//        assertTrue("More elements require more bits", bits1000Elements > bits100Elements)
//        assertTrue("The number of bir should be reasonable for 100 elements",
//            bits100Elements > 500 && bits100Elements < 2000)
//    }
//
//    @Test
//    fun `test debug info completeness`() {
//        // Given
//        listOf("tx1", "tx2", "tx3").forEach { filter.put(it) }
//
//        // When
//        val debugInfo = filter.getDebugInfo()
//
//        // Then
//        val expectedKeys = setOf(
//            "capacityBytes", "totalBits", "bitsSet",
//            "approximateSize", "falsePositiveRate", "utilizationPercentage"
//        )
//
//        expectedKeys.forEach { key ->
//            assertTrue("Debug info should contain '$key'", debugInfo.containsKey(key))
//        }
//
//        assertEquals("Capacity bytes should be correct", testCapacity, debugInfo["capacityBytes"])
//        assertTrue("Total bits should be capacity * 8",
//            debugInfo["totalBits"] == testCapacity * 8)
//    }
//
//    @Test
//    fun `test performance with many elements`() {
//        // Given
//        val manyElements = (1..1000).map { "transaction_$it" }
//
//        // When
//        val startTime = System.currentTimeMillis()
//        manyElements.forEach { filter.put(it) }
//        val insertTime = System.currentTimeMillis() - startTime
//
//        // Then
//        assertTrue("The inserting of 1000 elements should be fast", insertTime < 1000)
//
//        // Verify that elements are present
//        val sampleElements = manyElements.take(10)
//        sampleElements.forEach { element ->
//            assertTrue("Element '$element' should be present", filter.mightContain(element))
//        }
//
//        // Statistics should be reasonable
//        val fpr = filter.calculateFalsePositiveRate()
//        assertTrue("With more elements, FPR should be still managed", fpr < 0.5)
//    }
//
//    @Test
//    fun `test edge case with special characters`() {
//        // Given
//        val specialElements = listOf(
//            "tx_with_spaces ", "tx-with-dashes", "tx.with.dots",
//            "tx_with_números_123", "tx_with_símbolos_@#$", ""
//        )
//
//        // When & Then
//        specialElements.forEach { element ->
//            assertTrue("Could insert '$element'", filter.put(element))
//            assertTrue("Should find '$element'", filter.mightContain(element))
//        }
//    }
//}
