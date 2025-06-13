package nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection.strategies

import nl.tudelft.trustchain.eurotoken.entity.BillFaceToken
import nl.tudelft.trustchain.eurotoken.offlinePayment.ITokenStore
import nl.tudelft.trustchain.eurotoken.offlinePayment.StubTokenStore
import nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection.SelectionResult
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before

class RandomSelectorTest {

    private val tokenValue = 1L
    private val seed = 123456789L
    private lateinit var tokenStore: ITokenStore
    private lateinit var selector: RandomSelector

    @Before
    fun setup() {
        tokenStore = StubTokenStore()
        selector = RandomSelector(tokenStore, seed)
    }

    @Test
    fun `returns failure when not enough tokens are available`() {
        prepareStoreWithUnspentTokens(2)

        val result = selector.select(5L)

        assertTrue(result is SelectionResult.Failure)
        assertEquals("Not enough tokens available", (result as SelectionResult.Failure).reason)
    }

    @Test
    fun `returns success when enough tokens are available`() {
        prepareStoreWithUnspentTokens(5)

        val result = selector.select(3L)

        assertTrue(result is SelectionResult.Success)
        val selected = (result as SelectionResult.Success).tokens
        assertEquals(3, selected.size)
        assertEquals(3L, selected.sumOf { it.amount })
    }

    @Test
    fun `returns deterministically shuffled selection with same seed`() {
        prepareStoreWithUnspentTokens(10)

        val result1 = RandomSelector(tokenStore, seed).select(5L)
        val result2 = RandomSelector(tokenStore, seed).select(5L)

        assertTrue(result1 is SelectionResult.Success)
        assertTrue(result2 is SelectionResult.Success)

        val tokens1 = (result1 as SelectionResult.Success).tokens
        val tokens2 = (result2 as SelectionResult.Success).tokens

        assertEquals(tokens1.map { it.id }, tokens2.map { it.id })
    }

    @Test
    fun `selects minimum number of tokens needed to meet amount`() {
        prepareStoreWithUnspentTokens(10)

        val result = selector.select(7L)

        assertTrue(result is SelectionResult.Success)
        val selected = (result as SelectionResult.Success).tokens
        assertTrue(selected.sumOf { it.amount } >= 7L)
        assertEquals(7, selected.size)
    }

    private fun prepareStoreWithSpentTokens(n: Int) {
        repeat(n) {
            tokenStore.saveToken(
                BillFaceToken(
                    amount = tokenValue,
                    isSpent = true,
                    id = "",
                    intermediarySignature = ByteArray(0)
                )
            )
        }
    }

    private fun prepareStoreWithUnspentTokens(n: Int) {
        repeat(n) {
            tokenStore.saveToken(
                BillFaceToken(
                    amount = tokenValue,
                    isSpent = false,
                    id = "",
                    intermediarySignature = ByteArray(0)
                )
            )
        }
    }
}
