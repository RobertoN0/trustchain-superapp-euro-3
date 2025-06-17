package nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection

import nl.tudelft.trustchain.eurotoken.entity.BillFaceToken

sealed class SelectionResult {
    data class Success(val tokens: List<BillFaceToken>) : SelectionResult()
    data class Failure(val reason: String) : SelectionResult()
}
