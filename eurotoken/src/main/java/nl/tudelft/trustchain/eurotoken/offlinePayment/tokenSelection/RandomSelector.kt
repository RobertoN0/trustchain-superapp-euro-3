package nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection

import nl.tudelft.trustchain.eurotoken.db.TokenStore
import nl.tudelft.trustchain.eurotoken.entity.BillFaceToken

class RandomSelector(private val tokenStore: TokenStore): SelectionStrategy {
    override fun select(amount: Long): SelectionResult {
        val unspentTokens = tokenStore.getUnspentTokens()
        val totalAvailable = unspentTokens.sumOf { it.amount }
        if (totalAvailable < amount)
            return SelectionResult.Failure("Not enough tokens available")

        val shuffledTokens = unspentTokens.shuffled()
        val selectedTokens = mutableListOf<BillFaceToken>()
        var currentSum = 0L

        for (token in shuffledTokens) {
            selectedTokens.add(token)
            currentSum += token.amount
            if (currentSum == amount) {
                break
            }
        }
        return SelectionResult.Success(selectedTokens)
    }
}
