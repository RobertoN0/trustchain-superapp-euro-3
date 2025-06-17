package nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection.strategies

import nl.tudelft.trustchain.eurotoken.entity.BillFaceToken
import nl.tudelft.trustchain.eurotoken.offlinePayment.ITokenStore
import nl.tudelft.trustchain.eurotoken.offlinePayment.StubTokenStore
import nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection.SelectionResult
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before

class DoubleSpendSelectorTest {

    private val tokenValue = 1L
    private lateinit var tokenStore: ITokenStore
    private lateinit var selector: DoubleSpendSelector

    @Before
    fun setup() {
        tokenStore = StubTokenStore()
        selector = DoubleSpendSelector(tokenStore)
    }

    @Test
    fun `returns failure when amount is zero`() {
        val result = selector.select(0L)

        assertTrue(result is SelectionResult.Failure)
        assertEquals("Cannot select spent tokens for amount 0", (result as SelectionResult.Failure).reason)
    }

    @Test
    fun `returns failure when not enough total token value`() {
        prepareStoreWithUnspentTokens(3)

        val result = selector.select(4)
        assertTrue(result is SelectionResult.Failure)
        assertEquals("Not enough tokens", (result as SelectionResult.Failure).reason)
    }

    @Test
    fun `returns success with correct number of tokens using spent first`() {
        prepareStoreWithUnspentTokens(2)
        prepareStoreWithSpentTokens(2)

        val result = selector.select(3)

        assertTrue(result is SelectionResult.Success)
        val selected = (result as SelectionResult.Success).tokens
        assertEquals(3, selected.size)
        assertTrue(selected.take(2).all { it.isSpent }) // Spent tokens are prioritized
    }

    @Test
    fun `returns failure when no spent tokens are available`() {
        prepareStoreWithUnspentTokens(5)

        val result = selector.select(3)

        assertTrue(result is SelectionResult.Failure)
        assertEquals("No spent tokens present", (result as SelectionResult.Failure).reason)
    }

    @Test
    fun `returns success mixing spent and unspent when needed`() {
        prepareStoreWithSpentTokens(1)
        prepareStoreWithUnspentTokens(2)

        val result = selector.select(3)

        assertTrue(result is SelectionResult.Success)
        val selected = (result as SelectionResult.Success).tokens
        assertEquals(3, selected.size)
        assertEquals(1, selected.count { it.isSpent })
        assertEquals(2, selected.count { !it.isSpent })
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
