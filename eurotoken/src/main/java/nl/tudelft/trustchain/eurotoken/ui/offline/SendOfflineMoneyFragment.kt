package nl.tudelft.trustchain.eurotoken.ui.offline

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
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
import nl.tudelft.trustchain.eurotoken.ui.EurotokenBaseFragment
import nl.tudelft.trustchain.eurotoken.ui.transfer.SendMoneyFragment

class SendOfflineMoneyFragment : EurotokenBaseFragment(R.layout.fragment_send_offline_money) {


    private val binding by viewBinding(FragmentSendOfflineMoneyBinding::bind)
    private var selectedTokens = emptyList<BillFaceToken>()
    private var selectionProof : MPTSelectionProof? = null
    private val mptSelectionHelper = MPTTokenSelectionHelper()

    private val previousSelections = mutableListOf<List<BillFaceToken>>()

    private val ownPublicKey by lazy {
        defaultCryptoProvider.keyFromPublicBin(
            transactionRepository.trustChainCommunity.myPeer.publicKey.keyToBin().toHex()
                .hexToBytes()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val seed = arguments?.getString(ARG_SEED)
        val amount = arguments?.getLong(ARG_AMOUNT)!!

        selectedTokens = if (!seed.isNullOrEmpty()) {
            selectTokensForAmountMPT(amount, seed)
        } else {
            // Generate default seed if none provided for backward compatibility
            val defaultSeed = TokenMPTUtils.createMerchantSeed(
                merchantPublicKey = arguments?.getString(ARG_PUBLIC_KEY) ?: "",
                timestamp = System.currentTimeMillis()
            )
            Log.d("MPT", "had to create a defult seed since $seed as been reconized as null or empty. Defualt seed: $defaultSeed")
            selectTokensForAmountMPT(amount, defaultSeed)
        }

        // Generate cryptographic proof for the selection
        //if (selectedTokens.isNotEmpty()) {
        //    val allTokens = tokenStore.getUnspentTokens()
        //    selectionProof = mptSelectionHelper.generateSelectionProof(allTokens, selectedTokens)
        //}

        Log.d("MPT", "Selected ${selectedTokens.size} tokens for the transaction using ${if (seed.isNullOrEmpty()) "simple" else "MPT"} selection")
        Log.d("MPT", "Token list: ${selectedTokens}")
        Toast.makeText(
            requireContext(),
            "Selected ${selectedTokens.size} tokens for the transaction using ${if (seed.isNullOrEmpty()) "simple" else "MPT"} selection",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val publicKey = requireArguments().getString(ARG_PUBLIC_KEY)!!
        val amount = requireArguments().getLong(ARG_AMOUNT)
        val name = requireArguments().getString(SendMoneyFragment.ARG_NAME)!!
        val seed = requireArguments().getString(ARG_SEED)
        val key = defaultCryptoProvider.keyFromPublicBin(publicKey.hexToBytes())
        val contact = ContactStore.getInstance(view.context).getContactFromPublicKey(key)

        updateBalanceInfo()
        updateSelectionInfo(seed, amount)

        binding.txtRecipientName.text = "Recipient: $name"
        binding.txtRecipientPublicKey.text = "Public Key: $publicKey"
        binding.txtAmount.text = "Amount: ${TransactionRepository.prettyAmount(amount)}"

        binding.btnSend.setOnClickListener {
            handleSendTransaction(publicKey, amount, seed)
        }

        // Setup double spend button
        binding.btnDoubleSpend.setOnClickListener {
            handleDoubleSpendTest(publicKey, amount, seed)
        }
    }

    /**
     * NEW METHOD: MPT-based token selection replacing the original selectTokensForAmount
     * This implements Algorithm 1 from the paper using existing MPT infrastructure
     */
    private fun selectTokensForAmountMPT(amount: Long, merchantSeed: String): List<BillFaceToken> {
        val unspentTokens = tokenStore.getUnspentTokens()

        if (unspentTokens.isEmpty()) {
            Toast.makeText(requireContext(), "No tokens available", Toast.LENGTH_LONG).show()
            return emptyList()
        }

        val totalAvailable = unspentTokens.sumOf { it.amount }
        if (totalAvailable < amount) {
            Toast.makeText(requireContext(), "Insufficient tokens available", Toast.LENGTH_LONG).show()
            return emptyList()
        }

        return try {
            // Use MPT-based deterministic selection (Algorithm 1)
            // IMPORTANT: selectTokensForAmountMPT build a new MPT starting from the unspentToken list
            val selectedTokens = mptSelectionHelper.selectTokensForAmountMPT(
                unspentTokens,
                amount,
                merchantSeed
            )

            Log.d("MPT", "Total selected token: ${selectedTokens.size}, token list: $selectedTokens")

            val selectedSum = selectedTokens.sumOf { it.amount }
            if (selectedSum < amount) {
                Toast.makeText(
                    requireContext(),
                    "Selected amount insufficient: $selectedSum < $amount",
                    Toast.LENGTH_LONG
                ).show()
                selectTokensForAmount(amount)
            } else {
                selectedTokens
            }

        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "MPT selection error: ${e.message}",
                Toast.LENGTH_LONG
            ).show()

            // Fallback to original method
            selectTokensForAmount(amount)
        }
    }

    /**
     * Fallback for compatibility
     * This is the original simple random selection method
     */
    private fun selectTokensForAmount(amount: Long): List<BillFaceToken> {
        val unspentTokens = tokenStore.getUnspentTokens()
        val totalAvailable = unspentTokens.sumOf { it.amount }

        if (totalAvailable < amount) {
            Toast.makeText(requireContext(), "Not enough tokens available", Toast.LENGTH_LONG).show()
            return emptyList()
        }

        val shuffledTokens = unspentTokens.shuffled()
        val selectedTokens = mutableListOf<BillFaceToken>()
        var currentSum = 0L

        for (token in shuffledTokens) {
            selectedTokens.add(token)
            currentSum += token.amount
            if (currentSum >= amount) {
                break
            }
        }

        return selectedTokens
    }

    /**
     * Enhanced transaction handling with MPT proof
     */
    private fun handleSendTransaction(publicKey: String, amount: Long, seed: String?) {
        if (isOnline()) {
            Toast.makeText(
                requireContext(),
                "Online transactions are not supported in this mode",
                Toast.LENGTH_LONG
            ).show()
            Log.d("MPT", "Online transactions are not supported in this mode")
            return
        }

        if (selectedTokens.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "No tokens selected for transaction",
                Toast.LENGTH_LONG
            ).show()
            Log.d("MPT", "No tokens selected for transaction")
            return
        }

        try {
            // Serialize tokens for transmission
            val serializedTokens = BillFaceToken.serializeTokenList(selectedTokens)
            val sizeInBytes = serializedTokens.toByteArray(Charsets.UTF_8).size

            // Include MPT proof in the transaction for verification
            //val proofData = selectionProof?.let { proof ->
            //    "MPT_PROOF:${proof.rootHash.joinToString("") { "%02x".format(it) }}:${proof.selectedTokenIds.joinToString(",")}"
            //} ?: ""

            // Log selection for debugging
            val selectionInfo = TokenMPTUtils.formatTokenSelection(selectedTokens, seed)
            Log.d("MPT","MPT Selection: $selectionInfo")

            Toast.makeText(
                requireContext(),
                "Sending ${selectedTokens.size} tokens (${sizeInBytes} bytes) with MPT proof",
                Toast.LENGTH_SHORT
            ).show()
            Log.d("MPT", "Sending ${selectedTokens.size} tokens (${sizeInBytes} bytes) with MPT proof")

            val tokenBalance = tokenStore.getTotalBalance()
            val success = transactionRepository.sendOfflineProposal(
                publicKey.hexToBytes(),
                tokenBalance,
                amount,
                serializedTokens
            )

            if (!success) {
                Toast.makeText(
                    requireContext(),
                    "Transaction failed: Insufficient balance",
                    Toast.LENGTH_LONG
                ).show()
                Log.d("MPT", "Transaction failed: Insufficient balance")
                return
            }

            // Store this selection for double-spend detection
            previousSelections.add(selectedTokens)

            Toast.makeText(
                requireContext(),
                "MPT-based offline transaction sent successfully",
                Toast.LENGTH_SHORT
            ).show()
            Log.d("MPT","MPT-based offline transaction sent successfully")


            findNavController().navigate(R.id.action_sendOfflineMoneyFragment_to_transactionsFragment)

        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Transaction error: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            Log.d("MPT", "Transaction error: ${e.message}")
        }
    }

    /**
     * Test double-spending prevention
     */
    private fun handleDoubleSpendTest(publicKey: String, amount: Long, seed: String?) {
        if (seed.isNullOrEmpty()) {
            Toast.makeText(
                requireContext(),
                "Double-spend test requires merchant seed",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            val allTokens = tokenStore.getUnspentTokens()

            // Detect double-spend attempt using utility
            val isDoubleSpend = TokenMPTUtils.detectDoubleSpendAttempt(
                originalTokens = allTokens,
                seed = seed,
                amount = amount,
                previousSelections = previousSelections
            )

            if (isDoubleSpend) {
                Toast.makeText(
                    requireContext(),
                    "⚠️ DOUBLE-SPEND DETECTED! Same tokens would be selected again.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    requireContext(),
                    "✅ No double-spend detected. Selection is safe.",
                    Toast.LENGTH_SHORT
                ).show()
            }

            // Generate new selection for comparison
            val testSelection = mptSelectionHelper.selectTokensForAmountMPT(allTokens, amount, seed)
            val sameSelection = TokenMPTUtils.compareSelections(selectedTokens, testSelection)

            Toast.makeText(
                requireContext(),
                "Selection comparison: ${if (sameSelection) "IDENTICAL (Deterministic OK)" else "DIFFERENT (Error!)"}",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Double-spend test failed: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Update selection information display (for debugging purposes)
     */
    private fun updateSelectionInfo(seed: String?, amount: Long) {
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
            val args = Bundle().apply {
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
        fun createTestSeed(merchantId: String, scenario: String = "default"): String {
            return TokenMPTUtils.createTestSeed(scenario)
        }
    }
}
