package nl.tudelft.trustchain.eurotoken.ui.offline

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import nl.tudelft.trustchain.common.util.viewBinding
import nl.tudelft.trustchain.eurotoken.R
import nl.tudelft.trustchain.eurotoken.databinding.FragmentTransactionProgressBinding
import nl.tudelft.trustchain.eurotoken.offlinePayment.OfflineTransactionViewModel
import nl.tudelft.trustchain.eurotoken.offlinePayment.TransactionPhase
import nl.tudelft.trustchain.eurotoken.ui.EurotokenBaseFragment

class TransactionProgressFragment : EurotokenBaseFragment(R.layout.fragment_transaction_progress) {
    private val binding by viewBinding(FragmentTransactionProgressBinding::bind)
    private val transactionViewModel: OfflineTransactionViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.back.setOnClickListener {
            findNavController().navigate(
                R.id.action_transactionProgressFragment_to_offlineMoneyFragment
            )
        }

        transactionViewModel.phase.observe(viewLifecycleOwner) { currentPhase ->
            updatePhaseViews(currentPhase)
        }
    }

    private fun updatePhaseViews(currentPhase: TransactionPhase?) {
        val phaseOrder = listOf(
            TransactionPhase.CONNECTING,
            TransactionPhase.CONNECTED,
            TransactionPhase.SENDING_SEED,
            TransactionPhase.SENDING_TOKENS,
            TransactionPhase.COMPLETED
        )

        val textViews = listOf(
            binding.phaseConnecting,
            binding.phaseConnected,
            binding.phaseSendingSeed,
            binding.phaseReceivingTokens,
            binding.phaseCompleted
        )

        phaseOrder.forEachIndexed { index, phase ->
            val textView = textViews[index]
            val phaseName = phase.name.replace('_', ' ')
                .lowercase()
                .replaceFirstChar { it.uppercase() }

            when {
                phase == currentPhase -> {
                    textView.text = getString(R.string.phase_current, phaseName)
                    textView.setTypeface(null, Typeface.BOLD)
                    textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.blue))
                }
                phaseOrder.indexOf(phase) < phaseOrder.indexOf(currentPhase) -> {
                    textView.text = getString(R.string.phase_completed, phaseName)
                    textView.setTypeface(null, Typeface.NORMAL)
                    textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
                }
                else -> {
                    textView.text = getString(R.string.phase_pending, phaseName)
                    textView.setTypeface(null, Typeface.NORMAL)
                    textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray))
                }
            }
        }
    }

}
