package nl.tudelft.trustchain.eurotoken

import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.trustchain.eurotoken.entity.Transaction
import nl.tudelft.trustchain.eurotoken.db.SimpleBloomFilter
import nl.tudelft.trustchain.eurotoken.entity.BFSpentMoniesManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*

/**
 * Unit Tests for BFSpentMoniesManager
 *
 * Tests the implementation of Algorithm 2 from the paper:
 * "Ad Hoc Prevention of Double-Spending in Offline Payments"
 */
class BFSpentMoniesManagerTest {

    private lateinit var manager: BFSpentMoniesManager
    private val testCapacity = 10 // Small capacity for predictable testing
    private val testExpectedItems = 5

    @Before
    fun setUp() {
        manager = BFSpentMoniesManager(
            bloomFilterCapacity = 240,
            expectedItems = testExpectedItems,
            falsePositiveRate = 0.03
        )
    }

    @After
    fun tearDown() {
        manager.clearAllData()
    }

    // ===============================
    // BASIC FUNCTIONALITY TESTS
    // ===============================

    @Test
    fun testBasicDoubleSpendingDetection() {
        // Arrange
        val transaction = createMockTransaction("tx_001", 100, "sender_1")

        // Act & Assert - First check: should be clean
        assertFalse("New transaction shouldn't be flagged as double-spent",
            manager.isDoubleSpent(transaction))

        // Add as received money
        manager.addReceivedMoney(transaction)

        // Act & Assert - Second check: should be flagged as double-spent
        assertTrue("Previously received transaction should be flagged as double-spent",
            manager.isDoubleSpent(transaction))
    }

    @Test
    fun testMultipleTransactionsNoDoubleSpending() {
        // Arrange
        val transactions = (1..3).map {
            createMockTransaction("tx_$it", it * 100, "sender_$it")
        }

        // Act
        transactions.forEach { tx ->
            assertFalse("Transaction $tx should be clean initially", manager.isDoubleSpent(tx))
            manager.addReceivedMoney(tx)
        }

        // Assert - Each transaction should only trigger double-spending for itself
        transactions.forEach { tx ->
            assertTrue("Transaction ${tx.id} should be flagged as double-spent",
                manager.isDoubleSpent(tx))
        }
    }

    @Test
    fun testDifferentTransactionsSameAmount() {
        // Arrange - Same amount, different IDs and senders
        val tx1 = createMockTransaction("tx_001", 100, "sender_1")
        val tx2 = createMockTransaction("tx_002", 100, "sender_2")

        // Act
        manager.addReceivedMoney(tx1)

        // Assert - Different transactions with same amount should not conflict
        assertFalse("Different transactions with same amount should not conflict",
            manager.isDoubleSpent(tx2))
    }

    // ===============================
    // ALGORITHM 2 IMPLEMENTATION TESTS
    // ===============================

    @Test
    fun testAlgorithm2WithoutReceivedFilter() {
        // Arrange
        addMultipleTransactions(3)

        // Act
        val (filter, resultCode) = manager.createSharedBloomFilter(null)

        // Assert
        assertNotNull("Filter should be created when no received filter is provided", filter)
        assertEquals("Result should be ONLY_OURS when no received filter",
            BFSpentMoniesManager.RESULT_ONLY_OURS, resultCode)
        assertTrue("Filter should contain our items", filter!!.getApproximateSize() > 0)
    }

    @Test
    fun testAlgorithm2SuccessfulMerge() {
        // Arrange
        addMultipleTransactions(2) // Below capacity
        val receivedFilter = createSmallBloomFilter()

        // Act
        val (filter, resultCode) = manager.createSharedBloomFilter(receivedFilter)

        // Assert
        assertNotNull("Filter should be created for successful merge", filter)
        assertEquals("Result should be INCLUDED_RECEIVED for successful merge",
            BFSpentMoniesManager.RESULT_INCLUDED_RECEIVED, resultCode)
        assertTrue("Combined filter should have reasonable size",
            filter!!.estimateSize() <= testExpectedItems)
    }

    @Test
    fun testAlgorithm2ResetStrategy() {
        // Arrange
        addMultipleTransactions(testExpectedItems - 1) // Fill almost to capacity
        val largeReceivedFilter = createLargeBloomFilter()

        // Act
        val (filter, resultCode) = manager.createSharedBloomFilter(largeReceivedFilter)

        // Assert
        assertNotNull("Filter should be created even with reset strategy", filter)
        assertTrue("Result should indicate some form of handling",
            resultCode in listOf(
                BFSpentMoniesManager.RESULT_RESET_SHARED,
                BFSpentMoniesManager.RESULT_KEPT_PREVIOUS,
                BFSpentMoniesManager.RESULT_ONLY_OURS
            ))
    }

    @Test
    fun testAlgorithm2CapacityExceeded() {
        // Arrange - Add more transactions than expected capacity
        addMultipleTransactions(testExpectedItems + 2)

        // Act
        val (filter, resultCode) = manager.createSharedBloomFilter(null)

        // Assert
        assertNull("Filter should be null when capacity is exceeded", filter)
        assertEquals("Result should be CAPACITY_EXCEEDED",
            BFSpentMoniesManager.RESULT_CAPACITY_EXCEEDED, resultCode)
    }

    // ===============================
    // BLOOM FILTER PROCESSING TESTS
    // ===============================

    @Test
    fun testProcessValidBloomFilter() {
        // Arrange
        addMultipleTransactions(2)
        val filterBytes = createSmallBloomFilter().toByteArray()

        // Act
        val result = manager.processReceivedBloomFilter(filterBytes)

        // Assert
        assertTrue("Processing valid filter should succeed", result)
        assertTrue("Should have valid filter to share after processing",
            manager.hasValidFilterToShare())
    }

    @Test
    fun testProcessInvalidBloomFilter() {
        // Arrange
        val invalidBytes = ByteArray(10) { 0xFF.toByte() } // Invalid data

        // Act
        val result = manager.processReceivedBloomFilter(invalidBytes)

        // Assert
        assertFalse("Processing invalid filter should fail", result)
    }

    @Test
    fun testGetSharedBloomFilterBytes() {
        // Arrange
        addMultipleTransactions(2)
        manager.createSharedBloomFilter(null)

        // Act
        val filterBytes = manager.getSharedBloomFilterBytes()

        // Assert
        assertNotNull("Should return filter bytes when filter exists", filterBytes)
        assertEquals("Filter bytes should match expected capacity",
            240, filterBytes!!.size)
    }

    @Test
    fun testGetSharedBloomFilterBytesWhenEmpty() {
        // Act
        val filterBytes = manager.getSharedBloomFilterBytes()

        // Assert
        assertNull("Should return null when no filter exists", filterBytes)
    }

    // ===============================
    // CAPACITY AND CLEANUP TESTS
    // ===============================

    @Test
    fun testClearAllData() {
        // Arrange
        addMultipleTransactions(3)
        manager.createSharedBloomFilter(null)

        // Act
        manager.clearAllData()

        // Assert
        val stats = manager.getStatistics()
        assertEquals("Received monies count should be zero", 0, stats["receivedMoniesCount"])
        assertFalse("Should not have valid filter after clear", manager.hasValidFilterToShare())
        assertNull("Filter bytes should be null after clear", manager.getSharedBloomFilterBytes())
    }

    @Test
    fun testHasValidFilterToShare() {
        // Initially should not have valid filter
        assertFalse("Should not have valid filter initially", manager.hasValidFilterToShare())

        // Add transactions and create filter
        addMultipleTransactions(2)
        manager.createSharedBloomFilter(null)

        // Should now have valid filter
        assertTrue("Should have valid filter after creation", manager.hasValidFilterToShare())
    }

    // ===============================
    // STATISTICS AND MONITORING TESTS
    // ===============================

    @Test
    fun testStatisticsCollection() {
        // Arrange & Act
        val tx1 = createMockTransaction("tx_001", 100, "sender_1")
        manager.addReceivedMoney(tx1)
        manager.isDoubleSpent(tx1) // Should detect double-spending

        val tx2 = createMockTransaction("tx_002", 200, "sender_2")
        manager.isDoubleSpent(tx2) // Should be clean

        // Assert
        val stats = manager.getStatistics()
        assertEquals("Should have 1 received money", 1, stats["receivedMoniesCount"])
        assertTrue("Should detect double spending on TX1", manager.isDoubleSpent(tx1))
        assertTrue("Should not detect a double spending from TX2", !manager.isDoubleSpent(tx2))
        //assertTrue("Double-spending detection rate should be 0.5", Math.abs((stats["doubleSpendingDetectionRate"] as Double) - 0.5) < 0.01)
    }

    @Test
    fun testGetFilterInfo() {
        // Test without filter
        var filterInfo = manager.getFilterInfo()
        assertFalse("Should report no filter initially", filterInfo["hasFilter"] as Boolean)

        // Add transactions and create filter
        addMultipleTransactions(2)
        manager.createSharedBloomFilter(null)

        // Test with filter
        filterInfo = manager.getFilterInfo()
        assertTrue("Should report having filter", filterInfo["hasFilter"] as Boolean)
        assertTrue("Should report positive approximate size",
            (filterInfo["approximateSize"] as Int) > 0)
        assertTrue("Should report positive estimated size",
            (filterInfo["estimatedSize"] as Int) > 0)
        assertEquals("Should report correct capacity", 240, filterInfo["capacityBytes"])
    }

    // ===============================
    // EDGE CASE TESTS
    // ===============================

    @Test
    fun testEmptyTransactionId() {
        // Arrange
        val transaction = createMockTransaction("", 100, "sender_1")

        // Act & Assert - Should handle empty ID gracefully
        assertFalse("Empty transaction ID should not cause issues",
            manager.isDoubleSpent(transaction))

        // Should be able to add it
        manager.addReceivedMoney(transaction)

        // Should detect it as double-spent
        assertTrue("Empty ID transaction should be detectable",
            manager.isDoubleSpent(transaction))
    }

    @Test
    fun testLargeAmountTransactions() {
        // Arrange
        val largeAmount = Long.MAX_VALUE.toInt()
        val transaction = createMockTransaction("tx_large", largeAmount, "sender_1")

        // Act & Assert
        assertFalse("Large amount transaction should be handled normally",
            manager.isDoubleSpent(transaction))
        manager.addReceivedMoney(transaction)
        assertTrue("Large amount transaction should be detectable",
            manager.isDoubleSpent(transaction))
    }

    @Test
    fun testZeroAmountTransaction() {
        // Arrange
        val transaction = createMockTransaction("tx_zero", 0, "sender_1")

        // Act & Assert
        assertFalse("Zero amount transaction should be handled normally",
            manager.isDoubleSpent(transaction))
        manager.addReceivedMoney(transaction)
        assertTrue("Zero amount transaction should be detectable",
            manager.isDoubleSpent(transaction))
    }

    // ===============================
    // PERFORMANCE TESTS
    // ===============================

    @Test
    fun testPerformanceWithManyTransactions() {
        val startTime = System.currentTimeMillis()

        // Add many transactions (but within capacity)
        repeat(testExpectedItems) { i ->
            val tx = createMockTransaction("perf_tx_$i", i * 100, "sender_$i")
            manager.addReceivedMoney(tx)
            manager.isDoubleSpent(tx)
        }

        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        // Assert reasonable performance (should complete within 1 second)
        assertTrue("Operations should complete in reasonable time (< 1000ms), took ${duration}ms",
            duration < 1000)
    }

    // ===============================
    // HELPER METHODS
    // ===============================

    private fun addMultipleTransactions(count: Int) {
        repeat(count) { i ->
            val tx = createMockTransaction("tx_$i", (i + 1) * 100, "sender_$i")
            manager.addReceivedMoney(tx)
        }
    }

    private fun createMockTransaction(id: String, amount: Int, senderId: String): Transaction {
        return Transaction(
            id = id,
            amount = amount,
            sender = MockPublicKey(senderId),
            recipient = MockPublicKey("default_recipient"),
            outgoing = false,
            timestamp = Date(),
            confirmed = false,
            sent = false,
            received = true,
            read = false
        )
    }

    private fun createSmallBloomFilter(): SimpleBloomFilter {
        val filter = SimpleBloomFilter(240, 3)
        filter.put("external_item_1")
        filter.put("external_item_2")
        return filter
    }

    private fun createLargeBloomFilter(): SimpleBloomFilter {
        val filter = SimpleBloomFilter(240, 3)
        repeat(20) { i ->
            filter.put("large_external_item_$i")
        }
        return filter
    }

    // ===============================
    // MOCK CLASSES
    // ===============================

    private data class MockPublicKey(
        private val keyId: String
    ) : PublicKey {
        override fun keyToBin(): ByteArray = keyId.toByteArray(Charsets.UTF_8)
        override fun toString(): String = keyId
        override fun verify(signature: ByteArray, msg: ByteArray): Boolean {
            return true // always "verifies" in the mock
        }

        override fun getSignatureLength(): Int {
            TODO("Not yet implemented")
        }

        override fun encrypt(msg: ByteArray): ByteArray {
            TODO("Not yet implemented")
        }
    }
}

/**
 * Test Suite Runner for integration testing
 * Can be used to run all tests programmatically in Android environment
 */
class BFSpentMoniesManagerTestSuite {

    fun runAllTests(): TestResults {
        val testClass = BFSpentMoniesManagerTest()
        val results = TestResults()

        // List of all test methods to run
        val testMethods = listOf(
            "testBasicDoubleSpendingDetection" to { testClass.testBasicDoubleSpendingDetection() },
            "testMultipleTransactionsNoDoubleSpending" to { testClass.testMultipleTransactionsNoDoubleSpending() },
            "testDifferentTransactionsSameAmount" to { testClass.testDifferentTransactionsSameAmount() },
            "testAlgorithm2WithoutReceivedFilter" to { testClass.testAlgorithm2WithoutReceivedFilter() },
            "testAlgorithm2SuccessfulMerge" to { testClass.testAlgorithm2SuccessfulMerge() },
            "testAlgorithm2ResetStrategy" to { testClass.testAlgorithm2ResetStrategy() },
            "testAlgorithm2CapacityExceeded" to { testClass.testAlgorithm2CapacityExceeded() },
            "testProcessValidBloomFilter" to { testClass.testProcessValidBloomFilter() },
            "testProcessInvalidBloomFilter" to { testClass.testProcessInvalidBloomFilter() },
            "testGetSharedBloomFilterBytes" to { testClass.testGetSharedBloomFilterBytes() },
            "testGetSharedBloomFilterBytesWhenEmpty" to { testClass.testGetSharedBloomFilterBytesWhenEmpty() },
            "testClearAllData" to { testClass.testClearAllData() },
            "testHasValidFilterToShare" to { testClass.testHasValidFilterToShare() },
            "testStatisticsCollection" to { testClass.testStatisticsCollection() },
            "testGetFilterInfo" to { testClass.testGetFilterInfo() },
            "testEmptyTransactionId" to { testClass.testEmptyTransactionId() },
            "testLargeAmountTransactions" to { testClass.testLargeAmountTransactions() },
            "testZeroAmountTransaction" to { testClass.testZeroAmountTransaction() },
            "testPerformanceWithManyTransactions" to { testClass.testPerformanceWithManyTransactions() }
        )

        // Run each test
        testMethods.forEach { (testName, testMethod) ->
            try {
                testClass.setUp()
                testMethod()
                testClass.tearDown()
                results.addSuccess(testName)
            } catch (e: Exception) {
                results.addFailure(testName, e)
            }
        }

        return results
    }

    data class TestResults(
        private val successes: MutableList<String> = mutableListOf(),
        private val failures: MutableList<Pair<String, Exception>> = mutableListOf()
    ) {

        fun addSuccess(testName: String) {
            successes.add(testName)
        }

        fun addFailure(testName: String, exception: Exception) {
            failures.add(testName to exception)
        }

        fun getSuccessCount(): Int = successes.size
        fun getFailureCount(): Int = failures.size
        fun getTotalCount(): Int = successes.size + failures.size

        fun isAllPassed(): Boolean = failures.isEmpty()

        fun getReport(): String {
            val builder = StringBuilder()
            builder.appendLine("=== BFSpentMoniesManager Test Results ===")
            builder.appendLine("Total Tests: ${getTotalCount()}")
            builder.appendLine("Passed: ${getSuccessCount()}")
            builder.appendLine("Failed: ${getFailureCount()}")
            builder.appendLine()

            if (failures.isNotEmpty()) {
                builder.appendLine("FAILURES:")
                failures.forEach { (testName, exception) ->
                    builder.appendLine("- $testName: ${exception.message}")
                }
                builder.appendLine()
            }

            builder.appendLine("PASSED TESTS:")
            successes.forEach { testName ->
                builder.appendLine("✓ $testName")
            }

            return builder.toString()
        }
    }
}
