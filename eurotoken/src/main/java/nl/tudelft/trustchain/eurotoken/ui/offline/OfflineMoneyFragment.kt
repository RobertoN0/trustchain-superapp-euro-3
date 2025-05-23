package nl.tudelft.trustchain.eurotoken.ui.offline

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import nl.tudelft.trustchain.common.util.QRCodeUtils
import nl.tudelft.trustchain.common.util.viewBinding
import nl.tudelft.trustchain.eurotoken.R
import nl.tudelft.trustchain.eurotoken.databinding.FragmentOfflineMoneyBinding
import nl.tudelft.trustchain.eurotoken.ui.EurotokenBaseFragment
import nl.tudelft.trustchain.eurotoken.ui.transfer.SendMoneyFragment
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
        findNavController().navigate(R.id.action_offlineMoneyFragment_to_requestMoneyFragment)
    }

    private fun onSendClicked() {
        qrCodeUtils.startQRScanner(this)
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}



