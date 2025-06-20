package nl.tudelft.trustchain.common.eurotoken.blocks

import android.util.Log
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainTransaction
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore
import nl.tudelft.trustchain.common.eurotoken.TransactionRepository

class EuroTokenOfflineTransferValidator(
    transactionRepository: TransactionRepository,
    private val checkOfflineSpending: (TrustChainTransaction) -> Unit
    ) : EuroTokenTransferValidator(transactionRepository) {
    override fun validateEuroTokenProposal(
        block: TrustChainBlock,
        database: TrustChainStore
    ) {
        Log.d("EuroOfflineValidator", "Validating EuroToken proposal")
        if (block.isProposal && block.linkPublicKey.contentEquals(transactionRepository.trustChainCommunity.myPeer.publicKey.keyToBin())) {
            Log.d("EuroOfflineValidator", "Block is a proposal for me")
            checkOfflineSpending(block.transaction)
        }
        //super.validateEuroTokenProposal(block, database)
        return // Valid
    }

    class OfflineDoubleSpendingDetected(message: String) : Invalid(message) {
        override val type: String = "OfflineDoubleSpendingDetected"
    }
    class InvalidTokenPayload(message: String) : Invalid(message) {
        override val type: String = "InvalidTokenPayload"
    }
    class ForgedTokenSignature(message: String) : Invalid(message) {
        override val type: String = "ForgedTokenSignature"
    }

}
