package nl.tudelft.trustchain.eurotoken

import nl.tudelft.trustchain.eurotoken.db.SimpleBloomFilter
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import kotlin.math.abs

/**
 * Unit Tests for SimpleBloomFilter
 *
 * These tests verify:
 * 1. Basic functionality
 * 2. Serialization/Deserialization
 * 3. Union Operations
 * 4. Statistical estimates/computations
 * 5. Edge case behaviours
 */
class SimpleBloomFilterTest {

    private lateinit var filter: SimpleBloomFilter
    private val testCapacity = 240 // 240 bytes come da paper

    @Before
    fun setUp() {
        filter = SimpleBloomFilter(testCapacity, 3)
    }

    @Test
    fun `test basic element insertion and lookup`() {
        // Given
        val testElements = listOf("elemento1", "elemento2", "elemento3")

        // When - inserimento elementi
        testElements.forEach { element ->
            val wasAdded = filter.put(element)
            assertTrue("Elemento dovrebbe essere aggiunto la prima volta", wasAdded)
        }

        // Then - verifica presenza
        testElements.forEach { element ->
            assertTrue("Elemento inserito dovrebbe essere presente", filter.mightContain(element))
        }

        // And - verifica elemento non inserito
        assertFalse("Elemento non inserito non dovrebbe essere presente",
            filter.mightContain("elemento_non_esistente"))
    }

    @Test
    fun `test duplicate insertion`() {
        // Given
        val element = "test_element"

        // When
        val firstInsert = filter.put(element)
        val secondInsert = filter.put(element)

        // Then
        assertTrue("Prima inserzione dovrebbe modificare il filtro", firstInsert)
        assertFalse("Seconda inserzione non dovrebbe modificare il filtro", secondInsert)

        // And
        assertTrue("Elemento dovrebbe essere presente dopo entrambe le inserzioni",
            filter.mightContain(element))
    }

    @Test
    fun `test serialization and deserialization`() {
        // Given
        val testElements = listOf("tx1", "tx2", "tx3", "tx4", "tx5")
        testElements.forEach { filter.put(it) }

        val originalCount = filter.getApproximateSize()
        val originalFPR = filter.calculateFalsePositiveRate()

        // When
        val serialized = filter.toByteArray()
        val deserialized = SimpleBloomFilter.fromByteArray(serialized, 3)

        // Then
        assertEquals("Array serializzato dovrebbe avere la capacità corretta",
            testCapacity, serialized.size)

        // Verifica che tutti gli elementi originali siano presenti
        testElements.forEach { element ->
            assertTrue("Elemento '$element' dovrebbe essere presente dopo deserializzazione",
                deserialized.mightContain(element))
        }

        // Verifica statistiche simili (potrebbero differire leggermente)
        val deserializedCount = deserialized.getApproximateSize()
        val deserializedFPR = deserialized.calculateFalsePositiveRate()

        assertTrue("Conteggio elementi dovrebbe essere simile",
            abs(originalCount - deserializedCount) <= 1)
        assertEquals("False positive rate dovrebbe essere uguale",
            originalFPR, deserializedFPR, 0.001)
    }

    @Test
    fun `test union operation`() {
        // Given
        val filter1 = SimpleBloomFilter(testCapacity, 3)
        val filter2 = SimpleBloomFilter(testCapacity, 3)

        val elements1 = listOf("tx1", "tx2", "tx3")
        val elements2 = listOf("tx4", "tx5", "tx6")
        val commonElement = "common_tx"

        elements1.forEach { filter1.put(it) }
        elements2.forEach { filter2.put(it) }

        // Elemento comune
        filter1.put(commonElement)
        filter2.put(commonElement)

        // When
        filter1.merge(filter2)

        // Then
        // Tutti gli elementi di entrambi i filtri dovrebbero essere presenti
        (elements1 + elements2 + commonElement).forEach { element ->
            assertTrue("Elemento '$element' dovrebbe essere presente nell'union",
                filter1.mightContain(element))
        }

        // Il conteggio dovrebbe essere approximately la somma meno gli elementi comuni
        val expectedCount = elements1.size + elements2.size // common element contato una volta
        val actualCount = filter1.getApproximateSize()
        assertTrue("Conteggio union dovrebbe essere ragionevole",
            actualCount >= expectedCount - 2 && actualCount <= expectedCount + 2)
    }

    @Test
    fun `test union with different sized filters should fail`() {
        // Given
        val filter1 = SimpleBloomFilter(240, 3)
        val filter2 = SimpleBloomFilter(480, 3) // Dimensione diversa

        // When & Then
        try {
            filter1.merge(filter2)
            fail("Union di filtri con dimensioni diverse dovrebbe lanciare eccezione")
        } catch (e: IllegalArgumentException) {
            assertTrue("Messaggio eccezione dovrebbe menzionare la dimensione",
                e.message?.contains("stessa dimensione") == true)
        }
    }

    @Test
    fun `test false positive rate calculation`() {
        // Given - aggiungiamo alcuni elementi
        val elements = (1..10).map { "element_$it" }
        elements.forEach { filter.put(it) }

        // When
        val fpr = filter.calculateFalsePositiveRate()

        // Then
        assertTrue("False positive rate dovrebbe essere > 0", fpr > 0.0)
        assertTrue("False positive rate dovrebbe essere < 1", fpr < 1.0)
        assertTrue("False positive rate dovrebbe essere ragionevole (< 0.1)", fpr < 0.1)
    }

    @Test
    fun `test element count estimation`() {
        // Given
        val elements = (1..20).map { "tx_$it" }

        // When
        elements.forEach { filter.put(it) }

        // Then
        val estimatedCount = filter.estimateSize()
        assertTrue("Stima dovrebbe essere ragionevolmente vicina al conteggio reale",
            abs(estimatedCount - elements.size) <= 5)

        assertTrue("Conteggio approssimativo dovrebbe corrispondere alla stima",
            abs(estimatedCount - filter.getApproximateSize()) <= 2)
    }

    @Test
    fun `test empty filter properties`() {
        // When
        val emptyFilter = SimpleBloomFilter(240, 3)

        // Then
        assertEquals("Empty filter should have 0 elements", 0, emptyFilter.getApproximateSize())
        assertEquals("Empty filter should have 0 bit set", 0, emptyFilter.getBitSize())
        assertEquals("Empty filter shouldh have FPR = 0", 0.0, emptyFilter.calculateFalsePositiveRate(), 0.001)

        assertFalse("Empty filter should not contain elements",
            emptyFilter.mightContain("anything"))
    }

    @Test
    fun `test filter copy`() {
        // Given
        val originalElements = listOf("tx1", "tx2", "tx3")
        originalElements.forEach { filter.put(it) }

        // When
        val copy = filter.copy()

        // Then
        originalElements.forEach { element ->
            assertTrue("Copy should contain '$element'", copy.mightContain(element))
        }

        copy.put("new element")
        assertTrue("Copia dovrebbe contenere nuovo elemento", copy.mightContain("new_element"))
        assertFalse("Originale non dovrebbe contenere nuovo elemento", filter.mightContain("new_element"))

        // Statistiche dovrebbero essere simili
        assertEquals("Numero approssimativo elementi dovrebbe essere uguale",
            filter.getApproximateSize(), copy.getApproximateSize() - 1)
    }

    @Test
    fun `test optimal hash functions calculation`() {
        // When & Then
        assertEquals("Per FPR 0.03 dovremmo avere 3 funzioni hash",
            3, SimpleBloomFilter.optimalNumOfHashFunctions(0.03))

        assertEquals("Per FPR 0.01 dovremmo avere più funzioni hash",
            7, SimpleBloomFilter.optimalNumOfHashFunctions(0.01))

        assertEquals("Per FPR 0.1 dovremmo avere meno funzioni hash",
            2, SimpleBloomFilter.optimalNumOfHashFunctions(0.1))
    }

    @Test
    fun `test optimal bit count calculation`() {
        // When
        val bits100Elements = SimpleBloomFilter.optimalNumOfBits(100, 0.03)
        val bits1000Elements = SimpleBloomFilter.optimalNumOfBits(1000, 0.03)

        // Then
        assertTrue("Più elementi richiedono più bit", bits1000Elements > bits100Elements)
        assertTrue("Il numero di bit dovrebbe essere ragionevole per 100 elementi",
            bits100Elements > 500 && bits100Elements < 2000)
    }

    @Test
    fun `test debug info completeness`() {
        // Given
        listOf("tx1", "tx2", "tx3").forEach { filter.put(it) }

        // When
        val debugInfo = filter.getDebugInfo()

        // Then
        val expectedKeys = setOf(
            "capacityBytes", "totalBits", "bitsSet",
            "approximateElements", "falsePositiveRate", "utilizationPercentage"
        )

        expectedKeys.forEach { key ->
            assertTrue("Debug info dovrebbe contenere '$key'", debugInfo.containsKey(key))
        }

        assertEquals("Capacity bytes dovrebbe essere corretta", testCapacity, debugInfo["capacityBytes"])
        assertTrue("Total bits dovrebbe essere capacity * 8",
            debugInfo["totalBits"] == testCapacity * 8)
    }

    @Test
    fun `test performance with many elements`() {
        // Given
        val manyElements = (1..1000).map { "transaction_$it" }

        // When - misura tempo di inserimento
        val startTime = System.currentTimeMillis()
        manyElements.forEach { filter.put(it) }
        val insertTime = System.currentTimeMillis() - startTime

        // Then
        assertTrue("The inserting of 1000 elements should be fast", insertTime < 1000)

        // Verify that elements are present
        val sampleElements = manyElements.take(10)
        sampleElements.forEach { element ->
            assertTrue("Element '$element' should be present", filter.mightContain(element))
        }

        // Statistics should be reasonable
        val fpr = filter.calculateFalsePositiveRate()
        assertTrue("With more elements, FPR should be still managed", fpr < 0.5)
    }

    @Test
    fun `test edge case with special characters`() {
        // Given
        val specialElements = listOf(
            "tx_with_spaces ", "tx-with-dashes", "tx.with.dots",
            "tx_with_números_123", "tx_with_símbolos_@#$", ""
        )

        // When & Then
        specialElements.forEach { element ->
            assertTrue("Could insert '$element'", filter.put(element))
            assertTrue("Should find '$element'", filter.mightContain(element))
        }
    }
}
