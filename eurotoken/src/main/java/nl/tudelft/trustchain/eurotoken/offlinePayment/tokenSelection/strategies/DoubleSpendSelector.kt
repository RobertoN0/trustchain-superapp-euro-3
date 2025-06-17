package nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection.strategies

import nl.tudelft.trustchain.eurotoken.entity.BillFaceToken
import nl.tudelft.trustchain.eurotoken.offlinePayment.ITokenStore
import nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection.SelectionResult
import nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection.SelectionStrategy
import kotlin.math.ceil

class DoubleSpendSelector(private val tokenStore: ITokenStore) : SelectionStrategy {
    override fun select(amount: Long): SelectionResult {
        if (amount == 0L)
            return SelectionResult.Failure("Cannot select spent tokens for amount 0")

        val tokens = tokenStore.getAllTokens()
        val totalAvailable = tokens.sumOf { it.amount }

        if (totalAvailable < amount)
            return SelectionResult.Failure("Not enough tokens")

        val spent = tokens.filter { it.isSpent }
        if (spent.isEmpty()) {
            return SelectionResult.Failure("No spent tokens present")
        }

        val tokenValue = tokens.first().amount.toDouble()
        val requiredNumberOfTokens = ceil(amount / tokenValue).toInt()
        val selected = mutableListOf<BillFaceToken>()

        selected.addAll(spent.take(requiredNumberOfTokens))

        if (selected.size < requiredNumberOfTokens) {
            val unSpent = tokens.filter { !it.isSpent }
            selected.addAll(unSpent.take(requiredNumberOfTokens - selected.size))
        }

        return SelectionResult.Success(selected)
    }
}

