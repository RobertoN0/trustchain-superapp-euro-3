package nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection.strategies

import nl.tudelft.trustchain.eurotoken.entity.BillFaceToken
import nl.tudelft.trustchain.eurotoken.offlinePayment.ITokenStore
import nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection.SelectionResult
import nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection.SelectionStrategy
import kotlin.random.Random

class RandomSelector(private val tokenStore: ITokenStore, private val seed: Long): SelectionStrategy {
    override fun select(amount: Long): SelectionResult {
        val unspentTokens = tokenStore.getUnspentTokens()
        val totalAvailable = unspentTokens.sumOf { it.amount }
        if (totalAvailable < amount)
            return SelectionResult.Failure("Not enough tokens available")

        val random = Random(seed)
        val shuffledTokens = unspentTokens.shuffled(random)

        var currentSum = 0L
        val selectedTokens = mutableListOf<BillFaceToken>()
        for (token in shuffledTokens) {
            selectedTokens.add(token)
            currentSum += token.amount
            if (currentSum >= amount) {
                break
            }
        }
        return SelectionResult.Success(selectedTokens)
    }
}
