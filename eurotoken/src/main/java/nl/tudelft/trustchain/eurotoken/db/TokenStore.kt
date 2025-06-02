package nl.tudelft.trustchain.eurotoken.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.Query
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import nl.tudelft.eurotoken.sqldelight.Database
import nl.tudelft.peerchat.sqldelight.GetTotalBalance
import nl.tudelft.trustchain.eurotoken.entity.BillFaceToken

/**
 * TokenStore manages the BillFaceToken objects.
 * It uses SQLDelight for database operations.
 */



class TokenStore(context: Context) {
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
                                is_spent: Long, date_created: Long ->
        BillFaceToken(
            id,
            amount,
            intermediary_signature,
            is_spent == 1L,
            date_created
        )
    }

    /**
     * Saves a token to the database.
     */
    fun saveToken(token: BillFaceToken) {
        database.dbTokensQueries.insertToken(
            token.id,
            token.amount,
            token.intermediarySignature,
            if (token.isSpent) 1L else 0L,
            token.dateCreated
        )
    }

    /**
     * Retrieves a token by its ID.
     */
    fun getToken(id: String): BillFaceToken? {
        return database.dbTokensQueries.getToken(id, tokenMapper).executeAsOneOrNull()
    }

    /**
     * Retrieves all tokens from the database.
     */
    fun getAllTokens(): List<BillFaceToken> {
        return database.dbTokensQueries.getAllTokens(tokenMapper).executeAsList()
    }

    /**
     * Marks a token as spent.
     */
    fun markTokenAsSpent(id: String) {
        database.dbTokensQueries.markTokenAsSpent(id)
    }

    /**
     * Deletes a token from the database.
     */
    fun deleteToken(id: String) {
        database.dbTokensQueries.deleteToken(id)
    }

    /**
     * Gets the total balance of unspent tokens.
     */
    fun getTotalBalance(): Long {
        return database.dbTokensQueries.getTotalBalance().executeAsOneOrNull()?.total ?: 0L
    }

    /**
     * Gets all unspent tokens.
     */
    fun getUnspentTokens(): List<BillFaceToken> {
        return database.dbTokensQueries.getUnspentTokens(tokenMapper).executeAsList()
    }

    fun createContactStateTable() {
        database.dbTokensQueries.createContactStateTable()
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
