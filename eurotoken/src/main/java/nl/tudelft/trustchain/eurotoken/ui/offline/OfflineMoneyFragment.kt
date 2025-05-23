package nl.tudelft.trustchain.eurotoken.ui.offline

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import nl.tudelft.trustchain.common.util.viewBinding
import nl.tudelft.trustchain.eurotoken.R
import nl.tudelft.trustchain.eurotoken.databinding.FragmentOfflineMoneyBinding
import nl.tudelft.trustchain.eurotoken.ui.EurotokenBaseFragment

/**
 * A simple [Fragment] subclass.
 * Use the [OfflineMoneyFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class OfflineMoneyFragment : EurotokenBaseFragment(R.layout.fragment_offline_money) {
    private val binding by viewBinding(FragmentOfflineMoneyBinding::bind)
    private lateinit var enableBluetoothLauncher: ActivityResultLauncher<Intent>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableBluetoothLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                showToast("Bluetooth enabled")
            } else {
                showToast("Bluetooth not enabled")
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.receiveMoney.setOnClickListener {
            onReceiveClicked()
        }

        binding.sendMoney.setOnClickListener {
            showToast("Send clicked")
        }
    }

    private fun onReceiveClicked() {
        findNavController().navigate(R.id.action_offlineMoneyFragment_to_receiverDetailsFragment)
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
