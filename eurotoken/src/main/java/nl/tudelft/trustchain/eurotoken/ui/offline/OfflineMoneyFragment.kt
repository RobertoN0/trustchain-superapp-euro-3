package nl.tudelft.trustchain.eurotoken.ui.offline

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import nl.tudelft.trustchain.common.util.QRCodeUtils
import nl.tudelft.trustchain.common.util.viewBinding
import nl.tudelft.trustchain.eurotoken.R
import nl.tudelft.trustchain.eurotoken.databinding.FragmentOfflineMoneyBinding
import nl.tudelft.trustchain.eurotoken.offlinePayment.OfflineTransactionViewModel
import nl.tudelft.trustchain.eurotoken.ui.EurotokenBaseFragment
import nl.tudelft.trustchain.eurotoken.ui.offline.ReceiverDetailsFragment.Companion.ARG_AMOUNT
import nl.tudelft.trustchain.eurotoken.ui.offline.ReceiverDetailsFragment.Companion.ARG_IS_EDITABLE
import nl.tudelft.trustchain.eurotoken.ui.offline.ReceiverDetailsFragment.Companion.ARG_NAME
import org.json.JSONObject


class OfflineMoneyFragment : EurotokenBaseFragment(R.layout.fragment_offline_money) {
    private val binding by viewBinding(FragmentOfflineMoneyBinding::bind)
    private val transactionViewModel: OfflineTransactionViewModel by activityViewModels()

    private val qrCodeUtils by lazy {
        QRCodeUtils(requireContext())
    }

    private val qrScannerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = result.data
            val scannedData = qrCodeUtils.parseActivityResult(result.resultCode, intent)
            scannedData?.let {
                handleScannedData(it)
            }
        } else {
            showToast("Scan cancelled or failed")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        transactionViewModel.reset()

        binding.receiveMoney.setOnClickListener {
            onReceiveClicked()
        }

        binding.sendMoney.setOnClickListener {
            onSendClicked()
        }
    }

    private fun onReceiveClicked() {
        findNavController().navigate(R.id.action_offlineMoneyFragment_to_receiverDetailsFragment)
    }

    private fun onSendClicked() {
        val scanIntent = qrCodeUtils.createQRScannerIntent(this)
        qrScannerLauncher.launch(scanIntent)
    }

    private fun handleScannedData(data: String) {
        try {
            val payload = JSONObject(data)
            transactionViewModel.setDetailsFromQRPayload(payload)
            val details = transactionViewModel.transactionDetails.value

            val bundle = bundleOf(
                ARG_IS_EDITABLE to false,
                ARG_NAME to details?.name,
                ARG_AMOUNT to details?.amount
            )

            findNavController().navigate(
                R.id.action_offlineMoneyFragment_to_receiverDetailsFragment,
                bundle
            )

        } catch (e: Exception) {
            e.printStackTrace()
            showToast("QR code not recognized")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}



