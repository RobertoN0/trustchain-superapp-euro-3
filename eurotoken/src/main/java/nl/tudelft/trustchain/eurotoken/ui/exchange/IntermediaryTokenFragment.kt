package nl.tudelft.trustchain.eurotoken.ui.exchange

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.common.eurotoken.GatewayStore
import nl.tudelft.trustchain.common.eurotoken.TransactionRepository
import nl.tudelft.trustchain.common.util.viewBinding
import nl.tudelft.trustchain.eurotoken.R
import nl.tudelft.trustchain.eurotoken.databinding.FragmentIntermediaryBinding
import nl.tudelft.trustchain.eurotoken.entity.BillFaceToken
import nl.tudelft.trustchain.eurotoken.ui.EurotokenBaseFragment

class IntermediaryTokenFragment : EurotokenBaseFragment(R.layout.fragment_intermediary) {

    private val binding by viewBinding(FragmentIntermediaryBinding::bind)

    private val TAG = "IntermediaryTokenFragment"

    private val TOKEN_FIXED_VALUE = 5L // 0.05 euro

    private val ownPublicKey by lazy {
        defaultCryptoProvider.keyFromPublicBin(
            transactionRepository.trustChainCommunity.myPeer.publicKey.keyToBin().toHex()
                .hexToBytes()
        )
    }

    override val transactionRepository by lazy {
        TransactionRepository(getIpv8().getOverlay()!!, gatewayStore)
    }

    private val gatewayStore by lazy {
        GatewayStore.getInstance(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val amountInput = view.findViewById<EditText>(R.id.amountInput)
        val convertButton = view.findViewById<Button>(R.id.convertMoneyBtn)

        binding.txtAccountValue.text = TransactionRepository.prettyAmount(transactionRepository.getMyBalance())

        binding.txtTokenValue.text = TransactionRepository.prettyAmount(transactionRepository.getMyBalance())

        convertButton.setOnClickListener {
            val gateway = transactionRepository.getGatewayPeer()
            val myBalance = transactionRepository.getMyBalance()
            val amountText = amountInput.text.toString()

            if (amountText.isNotEmpty()) {
                val amount = amountText.toLongOrNull()
                if(amount == null || gateway == null){
                    Toast.makeText(requireContext(), "Gateway not found", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if(amount >= myBalance) {
                    Toast.makeText(requireContext(), "Insufficient Balance", Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                }
                if (amount > 0 && amount % 5 == 0L) {
                    createTokens(amount)
                    transactionRepository.sendWithdrawalProposal(gateway, amount)
                } else {
                    Toast.makeText(requireContext(), "Insert a valid amount", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Insert an amount", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createTokens(amountInCents: Long) {
        val tokenCount = (amountInCents / TOKEN_FIXED_VALUE).toInt()

        if (tokenCount <= 0) {
            Toast.makeText(
                requireContext(),
                "L'importo deve essere almeno ${TOKEN_FIXED_VALUE/100} euro",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val peerId = ownPublicKey.toString()

        val dummySignature = "dummySignature".toByteArray()

        val generatedTokens = mutableListOf<BillFaceToken>()

        for (i in 0 until tokenCount) {
            val timestamp = System.currentTimeMillis() + i // Aggiungi i per garantire ID unici
            val tokenId = BillFaceToken.createId(peerId, timestamp)


            val token = BillFaceToken(
                id = tokenId,
                amount = TOKEN_FIXED_VALUE,
                intermediarySignature = dummySignature
            )

            generatedTokens.add(token)

            Log.d(TAG, "Token creato: ID=${token.id}, Valore=${token.amount}, Signature=${token.intermediarySignature}, Timestamp=${token.dateCreated}")
        }

        Toast.makeText(
            requireContext(),
            "Creati $tokenCount token per un valore di ${prettyAmount(amountInCents)}",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun prettyAmount(amount: Long): String {
        return "€${amount / 100},${(amount % 100).toString().padStart(2, '0')}"
    }
}
