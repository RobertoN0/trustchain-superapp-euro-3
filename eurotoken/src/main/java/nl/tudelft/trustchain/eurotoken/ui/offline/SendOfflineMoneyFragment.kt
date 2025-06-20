package nl.tudelft.trustchain.eurotoken.ui.offline

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.trustchain.common.contacts.ContactStore
import nl.tudelft.trustchain.common.eurotoken.TransactionRepository
import nl.tudelft.trustchain.common.util.viewBinding
import nl.tudelft.trustchain.eurotoken.R
import nl.tudelft.trustchain.eurotoken.databinding.FragmentSendOfflineMoneyBinding
import nl.tudelft.trustchain.eurotoken.entity.BillFaceToken
import nl.tudelft.trustchain.eurotoken.entity.mpt.MPTSelectionProof
import nl.tudelft.trustchain.eurotoken.entity.mpt.TokenMPTUtils
import nl.tudelft.trustchain.eurotoken.offlinePayment.TokenSelectionViewModel
import nl.tudelft.trustchain.eurotoken.offlinePayment.TokenSelectionViewModelFactory
import nl.tudelft.trustchain.eurotoken.ui.EurotokenBaseFragment
import nl.tudelft.trustchain.eurotoken.ui.transfer.SendMoneyFragment

class SendOfflineMoneyFragment : EurotokenBaseFragment(R.layout.fragment_send_offline_money) {
    private val binding by viewBinding(FragmentSendOfflineMoneyBinding::bind)

    private lateinit var tokenSelectionViewModel: TokenSelectionViewModel

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        initTokenSelectionViewModel()

        val publicKey = requireArguments().getString(ARG_PUBLIC_KEY)!!
        val amount = requireArguments().getLong(ARG_AMOUNT)
        val name = requireArguments().getString(SendMoneyFragment.ARG_NAME)!!
        val seed =
            requireArguments().getString(ARG_SEED)
                ?: TokenMPTUtils.createMerchantSeed(
                    merchantPublicKey = arguments?.getString(ARG_PUBLIC_KEY) ?: "",
                    timestamp = System.currentTimeMillis()
                )
        updateBalanceInfo()

        val key = defaultCryptoProvider.keyFromPublicBin(publicKey.hexToBytes())
        val contacts = ContactStore.getInstance(requireContext())
        val contact = contacts.getContactFromPublicKey(key)
        if (contact == null) {
            contacts.addContact(key, name)
        } else if (contact.name != name) {
            contacts.updateContact(key, name)
        }

        binding.txtRecipientName.text = "Recipient: $name"
        binding.txtRecipientPublicKey.text = "Public Key: $publicKey"
        binding.txtAmount.text = "Amount: ${TransactionRepository.prettyAmount(amount)}"

        binding.btnSend.setOnClickListener {
            tokenSelectionViewModel.selectMPT(amount, seed)
        }

        binding.btnDoubleSpend.setOnClickListener {
            tokenSelectionViewModel.selectDoubleSpending(amount)
        }

        binding.btnForgedSpend.setOnClickListener {
            tokenSelectionViewModel.selectForged(amount)
        }

        lifecycleScope.launchWhenStarted {
            tokenSelectionViewModel.error.collect { errorMessage ->
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            }
        }

        lifecycleScope.launchWhenStarted {
            tokenSelectionViewModel.selectedTokens.collect { tokens ->
                handleSendTransaction(publicKey, amount, tokens)
            }
        }
    }

    /**
     * Enhanced transaction handling with MPT proof
     */
    private fun handleSendTransaction(
        publicKey: String,
        amount: Long,
        tokens: List<BillFaceToken>
    ) {
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

        // Serialize tokens for transmission
        val serializedTokens = BillFaceToken.serializeTokenList(tokens)

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
                    "Transaction error",
                    Toast.LENGTH_LONG
                ).show()
            Log.d("MPT", "Transaction error")
            return
        }

        Toast
            .makeText(
                requireContext(),
                "Offline transaction sent successfully",
                Toast.LENGTH_SHORT
            ).show()
        Log.d("MPT", "MPT-based offline transaction sent successfully")

        findNavController().navigate(R.id.action_sendOfflineMoneyFragment_to_transactionsFragment)
    }

    private fun updateBalanceInfo() {
        binding.txtAccountBalance.text =
            TransactionRepository.prettyAmount(transactionRepository.getMyBalance())
        binding.txtTokenBalance.text =
            TransactionRepository.prettyAmount(tokenStore.getTotalBalance())
    }

    private fun initTokenSelectionViewModel() {
        val factory = TokenSelectionViewModelFactory(tokenStore)
        tokenSelectionViewModel = ViewModelProvider(this, factory).get(TokenSelectionViewModel::class.java)
    }

    companion object {
        const val ARG_AMOUNT = "amount"
        const val ARG_PUBLIC_KEY = "pubkey"
        const val ARG_NAME = "name"
        const val ARG_SEED = "seed"
    }
}
