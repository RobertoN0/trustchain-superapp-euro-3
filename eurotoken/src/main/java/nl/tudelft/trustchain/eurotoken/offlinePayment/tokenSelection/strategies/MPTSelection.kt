package nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection.strategies

import nl.tudelft.trustchain.eurotoken.entity.mpt.MPTTokenSelectionHelper
import nl.tudelft.trustchain.eurotoken.offlinePayment.ITokenStore
import nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection.SelectionResult
import nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection.SelectionStrategy

class MPTSelection(
    private val tokenStore: ITokenStore,
    private val merchantSeed: String
): SelectionStrategy {
    private val mptSelectionHelper = MPTTokenSelectionHelper()

    override fun select(amount: Long): SelectionResult {
        val unspentTokens = tokenStore.getUnspentTokens()

        if (unspentTokens.isEmpty()) {
            return SelectionResult.Failure("No tokens available")
        }

        val totalAvailable = unspentTokens.sumOf { it.amount }
        if (totalAvailable < amount) {
            return SelectionResult.Failure("Insufficient tokens available")
        }

        val selected = mptSelectionHelper.selectTokensForAmountMPT(
            unspentTokens,
            amount,
            merchantSeed
        )
        return SelectionResult.Success(selected)
    }
}
