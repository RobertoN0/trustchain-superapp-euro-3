package nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection.strategies

import nl.tudelft.trustchain.eurotoken.offlinePayment.ITokenStore
import nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection.SelectionResult
import nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection.SelectionStrategy

class ForgedTokenSelector(
    private val tokenStore: ITokenStore
): SelectionStrategy {
    override fun select(amount: Long): SelectionResult {
        return SelectionResult.Failure("Forged token selection is not yet implemented")
    }
}
