package nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection

interface SelectionStrategy {
    fun select(amount: Long): SelectionResult
}
