package nl.tudelft.trustchain.eurotoken.ui.exchange

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import nl.tudelft.trustchain.eurotoken.entity.TokenSigner
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

    private fun getTokenBalance(): Long {
        return tokenStore.getTotalBalance()
    }

    private fun updateVisualBalance(){
        binding.txtAccountValue.text = TransactionRepository.prettyAmount(transactionRepository.getMyBalance())
        binding.txtTokenValue.text = TransactionRepository.prettyAmount(getTokenBalance())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.amountInput.addDecimalLimiter()

        val amountInput = view.findViewById<EditText>(R.id.amountInput)
        val convertButton = view.findViewById<Button>(R.id.convertMoneyBtn)
        val refundTokenButton = view.findViewById<Button>(R.id.refundTokenBtn)

        binding.txtAccountValue.text = TransactionRepository.prettyAmount(transactionRepository.getMyBalance())

        binding.txtTokenValue.text = TransactionRepository.prettyAmount(getTokenBalance())

        convertButton.setOnClickListener {
            val gateway = transactionRepository.getGatewayPeer()
            val myBalance = transactionRepository.getMyBalance()
            val amountText = amountInput.text.toString()

            if (amountText.isNotEmpty()) {
                val amount = getAmount(amountText)
                if(gateway == null){
                    Toast.makeText(requireContext(), "Gateway not found", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if(amount > myBalance) {
                    Toast.makeText(requireContext(), "Insufficient Balance", Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                }
                if (amount > 0 && amount % 5 == 0L) {
                    createTokens(amount)
                    transactionRepository.sendWithdrawalProposal(gateway, amount, false)
                    updateVisualBalance()
                } else {
                    Toast.makeText(requireContext(), "Insert a valid amount", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Insert an amount", Toast.LENGTH_SHORT).show()
            }
        }

        refundTokenButton.setOnClickListener {
            val gateway = transactionRepository.getGatewayPeer()
            val amountToRefund = tokenStore.getTotalBalance()
            Log.d(TAG, "Refunding tokens for amount: $amountToRefund")

            if (gateway == null) {
                Toast.makeText(requireContext(), "Gateway not found", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (amountToRefund > 0) {
                val spentTokens = tokenStore.getUnspentTokens()
                if (spentTokens.isEmpty()) {
                    Toast.makeText(requireContext(), "No tokens to refund", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                for (token in spentTokens) {
                    tokenStore.deleteToken(token.id)
                }
                transactionRepository.sendWithdrawalProposal(gateway, amountToRefund, true)
                updateVisualBalance()
            } else {
                Toast.makeText(requireContext(), "No tokens to refund", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createTokens(amountInCents: Long) {
        val tokenCount = (amountInCents / TOKEN_FIXED_VALUE).toInt()

        if (tokenCount <= 0) {
            Toast.makeText(
                requireContext(),
                "The amount must be at least ${TOKEN_FIXED_VALUE/50} euro",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val peerId = ownPublicKey.toString()

        val generatedTokens = mutableListOf<BillFaceToken>()

        for (i in 0 until tokenCount) {
            val timestamp = System.currentTimeMillis() + i
            val tokenId = BillFaceToken.createId(peerId, timestamp)

            val signature = tokenSigner.sign(
                id = tokenId,
                amount = TOKEN_FIXED_VALUE,
                dateCreated = timestamp
            )
            val token = BillFaceToken(
                id = tokenId,
                amount = TOKEN_FIXED_VALUE ,
                intermediarySignature = signature,
                dateCreated = timestamp
            )
            generatedTokens.add(token)
            tokenStore.saveToken(token)
            Log.d(TAG, "Created token: ID=${token.id}, Amount=${token.amount}, Signature=${token.intermediarySignature}, Timestamp=${token.dateCreated}")

        }

        Toast.makeText(
            requireContext(),
            "Created $tokenCount tokens for a value of ${prettyAmount(amountInCents)}",
            Toast.LENGTH_LONG
        ).show()
        Log.d("IntermediaryTokenFragment", "Created $tokenCount tokens for a value of ${prettyAmount(amountInCents)}")
    }

    private fun prettyAmount(amount: Long): String {
        return "€${amount / 100},${(amount % 100).toString().padStart(2, '0')}"
    }

    companion object {
        fun getAmount(amount: String): Long {
            val regex = """[^\d]""".toRegex()
            if (amount.isEmpty()) {
                return 0L
            }
            return regex.replace(amount, "").toLong()
        }

        fun EditText.decimalLimiter(string: String): String {
            var amount = getAmount(string)

            if (amount == 0L) {
                return ""
            }

            // val amount = string.replace("[^\\d]", "").toLong()
            return (amount / 100).toString() + "." + (amount % 100).toString().padStart(2, '0')
        }

        fun EditText.addDecimalLimiter() {
            this.addTextChangedListener(
                object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        val str = this@addDecimalLimiter.text!!.toString()
                        if (str.isEmpty()) return
                        val str2 = decimalLimiter(str)

                        if (str2 != str) {
                            this@addDecimalLimiter.setText(str2)
                            val pos = this@addDecimalLimiter.text!!.length
                            this@addDecimalLimiter.setSelection(pos)
                        }
                    }

                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {
                    }

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {}
                }
            )
        }
    }
}
