package nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection.strategies

import nl.tudelft.trustchain.eurotoken.entity.BillFaceToken
import nl.tudelft.trustchain.eurotoken.offlinePayment.ITokenStore
import nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection.SelectionResult
import nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection.SelectionStrategy

class ForgedTokenSelector(
    private val tokenStore: ITokenStore
) : SelectionStrategy {
    override fun select(amount: Long): SelectionResult {
        val selector = RandomSelector(tokenStore, 123456789)
        val tokens: List<BillFaceToken>
        when (val res = selector.select(amount / 2)) {
            is SelectionResult.Failure -> return res
            is SelectionResult.Success -> tokens = res.tokens
        }

        val modifiedTokens = tokens.map { it.copy(amount = it.amount * 2) }

        return SelectionResult.Success(modifiedTokens)
    }
}

