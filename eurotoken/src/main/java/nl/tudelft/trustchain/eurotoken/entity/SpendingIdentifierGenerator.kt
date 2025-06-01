package nl.tudelft.trustchain.eurotoken.entity

import nl.tudelft.ipv8.util.toHex
import java.security.MessageDigest


/**
 * Spending Identifier Generator
 * Generates unique identifiers for each transaction as described in the paper: "Ad Hoc Prevention of Double-Spending in Offline Payments"
 * It generalizes for UTxO-based, account-based and bill-based representations (for TrustChain the method `generateForAccount` should be used)
 */
class SpendingIdentifierGenerator {
    private val digest = MessageDigest.getInstance("SHA-256")

    /**
     * Generate spending identifier for UTxO representation
     */
    fun generateForUTxO(txId: String): String {
        return txId // UTxO ID can be used directly
    }

    /**
     * Generate spending identifier for account representation
     */
    fun generateForAccount(accountId: String, nonce: Long): String {
        val input = "$accountId||$nonce"
        return digest.digest(input.toByteArray()).toHex()
    }

    /**
     * Generate spending identifier for bill representation
     */
    fun generateForBill(billId: String, owner: String, previousSpendId: String? = null): String {
        val input = if (previousSpendId == null) {
            "$billId||$owner"
        } else {
            "$previousSpendId||$owner"
        }
        return digest.digest(input.toByteArray()).toHex()
    }
}
