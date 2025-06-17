package nl.tudelft.trustchain.eurotoken.offlinePayment

import nl.tudelft.trustchain.eurotoken.db.SimpleBloomFilter
import nl.tudelft.trustchain.eurotoken.entity.BillFaceToken

class StubTokenStore() : ITokenStore {
    private val tokens = mutableListOf<BillFaceToken>()

    override fun saveToken(token: BillFaceToken) {
        tokens.add(token)
    }

    override fun getToken(id: String): BillFaceToken? {
        return tokens.find { it.id == id }
    }

    override fun getAllTokens(): List<BillFaceToken> {
        return tokens.toList() // to prevent external mutation
    }

    override fun markTokenAsSpent(id: String) {
        tokens.find { it.id == id }?.let {
            tokens.remove(it)
            tokens.add(it.copy(isSpent = true))
        }
    }

    override fun deleteToken(id: String) {
        tokens.removeIf { it.id == id }
    }

    override fun getTotalBalance(): Long {
        return tokens.filter { !it.isSpent }.sumOf { it.amount }
    }

    override fun getUnspentTokens(): List<BillFaceToken> {
        return tokens.filter { !it.isSpent }
    }

    override fun getSpentTokens(): List<BillFaceToken> {
        return tokens.filter { it.isSpent }
    }

    override fun updateDateReceived(dateReceived: Long, id: String) {
        tokens.find { it.id == id }?.let {
            tokens.remove(it)
            tokens.add(it.copy(dateReceived = dateReceived))
        }
    }

    override fun saveBloomFilter(id: String, filter: SimpleBloomFilter) {
        TODO("Not yet implemented")
    }

    override fun getBloomFilter(id: String): SimpleBloomFilter? {
        TODO("Not yet implemented")
    }

    override fun deleteBloomFilter(id: String) {
        TODO("Not yet implemented")
    }
}
