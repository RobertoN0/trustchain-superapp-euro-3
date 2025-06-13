package nl.tudelft.trustchain.eurotoken.offlinePayment

import nl.tudelft.trustchain.eurotoken.db.SimpleBloomFilter
import nl.tudelft.trustchain.eurotoken.entity.BillFaceToken

interface ITokenStore {
    fun saveToken(token: BillFaceToken)
    fun getToken(id: String): BillFaceToken?
    fun getAllTokens(): List<BillFaceToken>
    fun markTokenAsSpent(id: String)
    fun deleteToken(id: String)
    fun getTotalBalance(): Long
    fun getUnspentTokens(): List<BillFaceToken>
    fun getSpentTokens(): List<BillFaceToken>
    fun updateDateReceived(dateReceived: Long, id: String)
    fun saveBloomFilter(id: String, filter: SimpleBloomFilter)
    fun getBloomFilter(id: String): SimpleBloomFilter?
    fun deleteBloomFilter(id: String)
}
