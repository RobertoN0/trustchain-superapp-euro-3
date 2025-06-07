package nl.tudelft.trustchain.eurotoken.ui.offline

import android.os.Bundle
import android.view.View
import android.widget.Toast
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.common.contacts.ContactStore
import nl.tudelft.trustchain.common.eurotoken.TransactionRepository
import nl.tudelft.trustchain.common.util.viewBinding
import nl.tudelft.trustchain.eurotoken.R
import nl.tudelft.trustchain.eurotoken.databinding.FragmentBroadcastBinding
import nl.tudelft.trustchain.eurotoken.ui.EurotokenBaseFragment
import nl.tudelft.trustchain.eurotoken.ui.transfer.SendMoneyFragment

class SendOfflineMoneyFragment : EurotokenBaseFragment(R.layout.fragment_broadcast) {

    private val binding by viewBinding(FragmentBroadcastBinding::bind)

    private val ownPublicKey by lazy {
        defaultCryptoProvider.keyFromPublicBin(
            transactionRepository.trustChainCommunity.myPeer.publicKey.keyToBin().toHex()
                .hexToBytes()
        )
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



        binding.txtRecipientName.text = "Recipient: $name"
        binding.txtRecipientPublicKey.text = "Public Key: $publicKey"
        binding.txtAmount.text = "Amount: ${TransactionRepository.prettyAmount(amount)}"


        binding.btnSend.setOnClickListener {
            if (isOnline()) {
              Toast.makeText(requireContext(),
                    "Online transactions are not supported in this mode",
                    Toast.LENGTH_LONG).show()
            } else {
                // TO DO
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


    companion object {
        const val ARG_AMOUNT = "amount"
        const val ARG_PUBLIC_KEY = "pubkey"
        const val ARG_NAME = "name"
        const val ARG_SEED = "seed"
    }
}
