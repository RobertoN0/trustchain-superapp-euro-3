package nl.tudelft.trustchain.eurotoken.ui.offline

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.common.contacts.ContactStore
import nl.tudelft.trustchain.common.eurotoken.TransactionRepository
import nl.tudelft.trustchain.common.util.viewBinding
import nl.tudelft.trustchain.eurotoken.R
import nl.tudelft.trustchain.eurotoken.databinding.FragmentSendOfflineMoneyBinding
import nl.tudelft.trustchain.eurotoken.entity.BillFaceToken
import nl.tudelft.trustchain.eurotoken.entity.mpt.MPTSelectionProof
import nl.tudelft.trustchain.eurotoken.entity.mpt.MPTTokenSelectionHelper
import nl.tudelft.trustchain.eurotoken.entity.mpt.TokenMPTUtils
import nl.tudelft.trustchain.eurotoken.offlinePayment.TokenSelectionViewModel
import nl.tudelft.trustchain.eurotoken.ui.EurotokenBaseFragment
import nl.tudelft.trustchain.eurotoken.ui.transfer.SendMoneyFragment

class SendOfflineMoneyFragment : EurotokenBaseFragment(R.layout.fragment_send_offline_money) {
    private val binding by viewBinding(FragmentSendOfflineMoneyBinding::bind)
    private var selectionProof: MPTSelectionProof? = null

    private val tokenSelectionViewModel: TokenSelectionViewModel by activityViewModels()

    private val ownPublicKey by lazy {
        defaultCryptoProvider.keyFromPublicBin(
            transactionRepository.trustChainCommunity.myPeer.publicKey
                .keyToBin()
                .toHex()
                .hexToBytes()
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val publicKey = requireArguments().getString(ARG_PUBLIC_KEY)!!
        val amount = requireArguments().getLong(ARG_AMOUNT)
        val name = requireArguments().getString(SendMoneyFragment.ARG_NAME)!!
        val seed =
            requireArguments().getString(ARG_SEED)
                ?: TokenMPTUtils.createMerchantSeed(
                    merchantPublicKey = arguments?.getString(ARG_PUBLIC_KEY) ?: "",
                    timestamp = System.currentTimeMillis()
                )
        val key = defaultCryptoProvider.keyFromPublicBin(publicKey.hexToBytes())
        val contact = ContactStore.getInstance(view.context).getContactFromPublicKey(key)

        updateBalanceInfo()

        binding.txtRecipientName.text = "Recipient: $name"
        binding.txtRecipientPublicKey.text = "Public Key: $publicKey"
        binding.txtAmount.text = "Amount: ${TransactionRepository.prettyAmount(amount)}"

        binding.btnSend.setOnClickListener {
            tokenSelectionViewModel.selectMPT(amount, seed)
        }

        binding.btnDoubleSpend.setOnClickListener {
            tokenSelectionViewModel.selectDoubleSpending(amount)
        }

        lifecycleScope.launchWhenStarted {
            tokenSelectionViewModel.error.collect { errorMessage ->
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            }
        }

        lifecycleScope.launchWhenStarted {
            tokenSelectionViewModel.selectedTokens.collect { tokens ->
                handleSendTransaction(publicKey, amount, seed, tokens)
            }
        }
    }

    /**
     * Enhanced transaction handling with MPT proof
     */
    private fun handleSendTransaction(
        publicKey: String,
        amount: Long,
        seed: String?,
        tokens: List<BillFaceToken>
    ) {
        if (isOnline()) {
            Toast
                .makeText(
                    requireContext(),
                    "Online transactions are not supported in this mode",
                    Toast.LENGTH_LONG
                ).show()
            Log.d("MPT", "Online transactions are not supported in this mode")
            return
        }

        if (tokens.isEmpty()) {
            Toast
                .makeText(
                    requireContext(),
                    "No tokens selected for transaction",
                    Toast.LENGTH_LONG
                ).show()
            Log.d("MPT", "No tokens selected for transaction")
            return
        }

        try {
            // Serialize tokens for transmission
            val serializedTokens = BillFaceToken.serializeTokenList(tokens)
            val sizeInBytes = serializedTokens.toByteArray(Charsets.UTF_8).size

            // Log selection for debugging
            val selectionInfo = TokenMPTUtils.formatTokenSelection(tokens, seed)
            Log.d("MPT", "MPT Selection: $selectionInfo")

            Toast
                .makeText(
                    requireContext(),
                    "Sending ${tokens.size} tokens ($sizeInBytes bytes) with MPT proof",
                    Toast.LENGTH_SHORT
                ).show()
            Log.d("MPT", "Sending ${tokens.size} tokens ($sizeInBytes bytes) with MPT proof")

            val tokenBalance = tokenStore.getTotalBalance()
            val success =
                transactionRepository.sendOfflineProposal(
                    publicKey.hexToBytes(),
                    tokenBalance,
                    amount,
                    serializedTokens
                )

            if (!success) {
                Toast
                    .makeText(
                        requireContext(),
                        "Transaction failed: Insufficient balance",
                        Toast.LENGTH_LONG
                    ).show()
                Log.d("MPT", "Transaction failed: Insufficient balance")
                return
            }

            Toast
                .makeText(
                    requireContext(),
                    "MPT-based offline transaction sent successfully",
                    Toast.LENGTH_SHORT
                ).show()
            Log.d("MPT", "MPT-based offline transaction sent successfully")

            findNavController().navigate(R.id.action_sendOfflineMoneyFragment_to_transactionsFragment)
        } catch (e: Exception) {
            Toast
                .makeText(
                    requireContext(),
                    "Transaction error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            Log.d("MPT", "Transaction error: ${e.message}")
        }
    }

    /**
     * Update selection information display (for debugging purposes)
     */
    private fun updateSelectionInfo(
        seed: String?,
        amount: Long,
        selectedTokens: List<BillFaceToken>
    ) {
        val selectionMethod = if (seed.isNullOrEmpty()) "Auto-Generated Seed" else "Merchant Seed"
        val seedDisplay = seed?.take(8) ?: "auto-gen"

        // Update UI elements if they exist in the layout
        binding.txtSelectionMethod?.text = "Method: MPT Algorithm ($selectionMethod)"
        binding.txtSeedInfo?.text = "Seed: $seedDisplay..."
        binding.txtSelectedCount?.text = "Selected: ${selectedTokens.size} tokens"
        binding.txtSelectedAmount?.text = "Total: ${TransactionRepository.prettyAmount(selectedTokens.sumOf { it.amount })}"

        // Show proof information if available
        selectionProof?.let { proof ->
            binding.txtProofInfo?.text = "MPT Root: ${proof.rootHash.take(8).joinToString("") { "%02x".format(it) }}..."
        }

        // Calculate and display selection efficiency
        val efficiency = TokenMPTUtils.calculateSelectionEfficiency(amount, selectedTokens)
        binding.txtEfficiency?.text = "Efficiency: ${String.format("%.1f", efficiency * 100)}%"
    }

    private fun updateBalanceInfo() {
        binding.txtAccountBalance.text =
            TransactionRepository.prettyAmount(transactionRepository.getMyBalance())
        binding.txtTokenBalance.text =
            TransactionRepository.prettyAmount(tokenStore.getTotalBalance())
    }

    companion object {
        const val ARG_AMOUNT = "amount"
        const val ARG_PUBLIC_KEY = "pubkey"
        const val ARG_NAME = "name"
        const val ARG_SEED = "seed" // NEW: Added seed argument

        /**
         * Create instance with MPT support
         */
        fun newInstanceWithMPT(
            amount: Long,
            publicKey: String,
            name: String,
            merchantSeed: String? = null
        ): SendOfflineMoneyFragment {
            val fragment = SendOfflineMoneyFragment()
            val args =
                Bundle().apply {
                    putLong(ARG_AMOUNT, amount)
                    putString(ARG_PUBLIC_KEY, publicKey)
                    putString(ARG_NAME, name)
                    merchantSeed?.let { putString(ARG_SEED, it) }
                }
            fragment.arguments = args
            return fragment
        }

        /**
         * Create deterministic seed for testing
         */
        fun createTestSeed(
            merchantId: String,
            scenario: String = "default"
        ): String = TokenMPTUtils.createTestSeed(scenario)
    }
}
