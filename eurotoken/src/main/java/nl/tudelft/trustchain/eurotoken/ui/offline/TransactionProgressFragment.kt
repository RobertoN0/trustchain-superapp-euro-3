package nl.tudelft.trustchain.eurotoken.ui.offline

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import nl.tudelft.trustchain.common.util.viewBinding
import nl.tudelft.trustchain.eurotoken.R
import nl.tudelft.trustchain.eurotoken.databinding.FragmentTransactionProgressBinding
import nl.tudelft.trustchain.eurotoken.ui.EurotokenBaseFragment

class TransactionProgressFragment : EurotokenBaseFragment(R.layout.fragment_transaction_progress) {
    private val binding by viewBinding(FragmentTransactionProgressBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.back.setOnClickListener {
            findNavController().navigate(
                R.id.action_transactionProgressFragment_to_offlineMoneyFragment
            )
        }
    }

}
