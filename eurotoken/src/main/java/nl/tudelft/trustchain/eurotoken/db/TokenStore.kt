package nl.tudelft.trustchain.eurotoken.db

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import nl.tudelft.eurotoken.sqldelight.Database
import nl.tudelft.trustchain.eurotoken.entity.BillFaceToken
import nl.tudelft.trustchain.eurotoken.offlinePayment.ITokenStore

/**
 * TokenStore manages the BillFaceToken objects.
 * It uses SQLDelight for database operations.
 */



class TokenStore(context: Context): ITokenStore {
    private val driver = AndroidSqliteDriver(
        schema = Database.Schema,
        context = context,
        name = "eurotoken.db",
    )
    private val database = Database(driver)

    /**
     * Maps the database rows to BillFaceToken objects.
     */
    private val tokenMapper = { id: String, amount: Long, intermediary_signature: ByteArray,
                                is_spent: Long, date_created: Long, date_received: Long? ->
        BillFaceToken(
            id,
            amount,
            intermediary_signature,
            is_spent == 1L,
            date_created,
            date_received
        )
    }

    /**
     * Maps database rows to SimpleBloomFilter objects.
     */
    private val bloomMapper = { _id: String, _numHash: Long, filterBytes: ByteArray ->
        SimpleBloomFilter.fromByteArray(filterBytes, _numHash.toInt())
    }

    /**
     * Saves a token to the database.
     */
    override fun saveToken(token: BillFaceToken) {
        database.dbTokensQueries.insertToken(
            token.id,
            token.amount,
            token.intermediarySignature,
            if (token.isSpent) 1L else 0L,
            token.dateCreated,
            token.dateReceived
        )
    }

    /**
     * Retrieves a token by its ID.
     */
    override fun getToken(id: String): BillFaceToken? {
        return database.dbTokensQueries.getToken(id, tokenMapper).executeAsOneOrNull()
    }

    /**
     * Retrieves all tokens from the database.
     */
    override fun getAllTokens(): List<BillFaceToken> {
        return database.dbTokensQueries.getAllTokens(tokenMapper).executeAsList()
    }

    /**
     * Marks a token as spent.
     */
    override fun markTokenAsSpent(id: String) {
        database.dbTokensQueries.markTokenAsSpent(id)
    }

    /**
     * Deletes a token from the database.
     */
    override fun deleteToken(id: String) {
        database.dbTokensQueries.deleteToken(id)
    }

    /**
     * Gets the total balance of unspent tokens.
     */
    override fun getTotalBalance(): Long {
        return database.dbTokensQueries.getTotalBalance().executeAsOneOrNull()?.total ?: 0L
    }

    /**
     * Gets all unspent tokens.
     */
    override fun getUnspentTokens(): List<BillFaceToken> {
        return database.dbTokensQueries.getUnspentTokens(tokenMapper).executeAsList()
    }

    /**
     * Gets all spent tokens.
     */
    override fun getSpentTokens(): List<BillFaceToken> {
        return database.dbTokensQueries.getSpentTokens(tokenMapper).executeAsList()
    }
    /**
     * Updates the date received for a token.
     */
    override fun updateDateReceived(dateReceived: Long, id: String) {
        database.dbTokensQueries.updateDateReceived(dateReceived, id)
    }

    fun createContactStateTable() {
        database.dbTokensQueries.createContactStateTable()
    }

    fun createBloomFilterTable() {
        database.dbTokensQueries.createBloomFilterTable()
    }

    /**
     * Inserts a bloom filter into the database.
     */
    override fun saveBloomFilter(id: String, filter: SimpleBloomFilter) {
        database.dbTokensQueries.insertOrUpdateBloomFilter(
            id,
            filter.getNumHashFunctions().toLong(),
            filter.toByteArray()
        )
    }

    /**
     * Retrieves a bloom filter by its ID.
     */
    override fun getBloomFilter(id: String): SimpleBloomFilter? {
        return database.dbTokensQueries.selectBloomFilter(id, bloomMapper).executeAsOneOrNull()
    }

    /**
     * Deletes a bloom filter by its ID.
     */
    override fun deleteBloomFilter(id: String) {
        database.dbTokensQueries.deleteBloomFilter(id)
    }

    companion object {
        private lateinit var instance: TokenStore

        fun getInstance(context: Context): TokenStore {
            if (!::instance.isInitialized) {
                instance = TokenStore(context)
            }
            return instance
        }
    }
}
