package nl.tudelft.trustchain.eurotoken.ui.offline

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import nl.tudelft.trustchain.common.util.QRCodeUtils
import nl.tudelft.trustchain.common.util.viewBinding
import nl.tudelft.trustchain.eurotoken.R
import nl.tudelft.trustchain.eurotoken.databinding.FragmentOfflineMoneyBinding
import nl.tudelft.trustchain.eurotoken.ui.EurotokenBaseFragment
import nl.tudelft.trustchain.eurotoken.ui.transfer.TransferFragment
import org.json.JSONException

/**
 * A simple [Fragment] subclass.
 * Use the [OfflineMoneyFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class OfflineMoneyFragment : EurotokenBaseFragment(R.layout.fragment_offline_money) {
    private val binding by viewBinding(FragmentOfflineMoneyBinding::bind)

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
            Toast.makeText(requireContext(), "Scan cancelled or failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.receiveMoney.setOnClickListener {
            showToast("Receive clicked")
            onReceiveClicked()
        }

        binding.sendMoney.setOnClickListener {
            showToast("Send clicked")
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

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun handleScannedData(data: String) {
        try {
            Log.i("Offline", "Scanned data: $data")
            val connectionData = TransferFragment.Companion.ConnectionData(data)
            Log.i("Offline", "Parsed connection data: $connectionData")

            if (connectionData.type == "transfer") {
                findNavController().navigate(R.id.action_offlineMoneyFragment_to_receiverDetailsFragment)
            }
        } catch (e: JSONException) {
            Toast.makeText(requireContext(), "Scan failed, try again", Toast.LENGTH_LONG).show()
        }
    }
}



