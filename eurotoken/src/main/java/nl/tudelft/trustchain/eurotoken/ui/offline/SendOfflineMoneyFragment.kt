package nl.tudelft.trustchain.eurotoken.ui.offline

import android.os.Bundle
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
import nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection.SelectionResult
import nl.tudelft.trustchain.eurotoken.offlinePayment.tokenSelection.strategies.RandomSelector
import nl.tudelft.trustchain.eurotoken.ui.EurotokenBaseFragment
import nl.tudelft.trustchain.eurotoken.ui.transfer.SendMoneyFragment

class SendOfflineMoneyFragment : EurotokenBaseFragment(R.layout.fragment_send_offline_money) {


    private val binding by viewBinding(FragmentSendOfflineMoneyBinding::bind)
//    private var selectedTokens = emptyList<BillFaceToken>()


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
        // TO DO Replace this method with the proper random selection of tokens
//        selectedTokens = selectTokensForAmount(amount)
//        Toast.makeText(
//            requireContext(),
//            "Selected ${selectedTokens.size} tokens for the transaction",
//            Toast.LENGTH_SHORT
//        ).show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val publicKey = requireArguments().getString(ARG_PUBLIC_KEY)!!
        val amount = requireArguments().getLong(ARG_AMOUNT)
        val name = requireArguments().getString(SendMoneyFragment.ARG_NAME)!!
        var seed = requireArguments().getString(ARG_SEED)
        val key = defaultCryptoProvider.keyFromPublicBin(publicKey.hexToBytes())
        val contact = ContactStore.getInstance(view.context).getContactFromPublicKey(key)
        updateBalanceInfo()



        binding.txtRecipientName.text = "Recipient: $name"
        binding.txtRecipientPublicKey.text = "Public Key: $publicKey"
        binding.txtAmount.text = "Amount: ${TransactionRepository.prettyAmount(amount)}"


        binding.btnSend.setOnClickListener {
            if (isOnline()) {
              Toast.makeText(requireContext(),
                    "Online transactions are not supported in this mode",
                    Toast.LENGTH_LONG).show()
            } else {
                val selector = RandomSelector(tokenStore, 123456789101112)
                val result = selector.select(amount)
                val selectedTokens = mutableListOf<BillFaceToken>()
                if (result is SelectionResult.Failure) {
                    return@setOnClickListener Toast.makeText(
                        requireContext(),
                        "Error while selecting tokens: " + result.reason,
                        Toast.LENGTH_LONG
                    ).show()
                } else if (result is SelectionResult.Success) {
                    selectedTokens.addAll(result.tokens)
                }


                var serializedTokens = ""
                if (selectedTokens.isEmpty()) {
                    Toast.makeText(requireContext(), "No tokens selected for the transaction", Toast.LENGTH_LONG).show()
                } else {
                    serializedTokens = serializeTokens(selectedTokens)
                    val sizeInBytes: Int = serializedTokens.toByteArray(Charsets.UTF_8).size
                    Toast.makeText(
                        requireContext(),
                        "Sending $sizeInBytes bytes of serialized tokens",
                        Toast.LENGTH_SHORT
                    ).show()
                }
//                val newName = binding.newContactName.text.toString()
//                if (addContact && newName.isNotEmpty()) {
////                val key = defaultCryptoProvider.keyFromPublicBin(publicKey.hexToBytes())
//                    ContactStore.getInstance(requireContext())
//                        .addContact(key, newName)
//                }
                val tokenBalance = tokenStore.getTotalBalance()
                val success = transactionRepository.sendOfflineProposal(publicKey.hexToBytes(), tokenBalance, amount, serializedTokens)
                if (!success) {
                    return@setOnClickListener Toast.makeText(
                        requireContext(),
                        "Insufficient balance",
                        Toast.LENGTH_LONG
                    ).show()
                }
                findNavController().navigate(R.id.action_sendOfflineMoneyFragment_to_transactionsFragment)
            }
        }

        // Setup double spend button
        binding.btnDoubleSpend.setOnClickListener {
            // TO DO
        }
    }

    private fun updateBalanceInfo() {
        binding.txtAccountBalance.text = TransactionRepository.prettyAmount(transactionRepository.getMyBalance())
        binding.txtTokenBalance.text = TransactionRepository.prettyAmount(tokenStore.getTotalBalance())
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun serializeTokens(tokens: List<BillFaceToken>): String {
        val bytes = ProtoBuf.encodeToByteArray(
            ListSerializer(BillFaceToken.serializer()),
            tokens
        )
        return Base64.getEncoder().encodeToString(bytes)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun deserializeTokens(b64: String): List<BillFaceToken> {
        val bytes = Base64.getDecoder().decode(b64)
        return ProtoBuf.decodeFromByteArray(
            ListSerializer(BillFaceToken.serializer()),
            bytes
        )
    }



    companion object {
        const val ARG_AMOUNT = "amount"
        const val ARG_PUBLIC_KEY = "pubkey"
        const val ARG_NAME = "name"
        const val ARG_SEED = "seed"
    }
}
