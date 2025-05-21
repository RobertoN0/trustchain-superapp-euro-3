package nl.tudelft.trustchain.eurotoken.ui.offline

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import nl.tudelft.trustchain.common.util.viewBinding
import nl.tudelft.trustchain.eurotoken.R
import nl.tudelft.trustchain.eurotoken.databinding.FragmentOfflineMoneyBinding
import nl.tudelft.trustchain.eurotoken.offlinePayment.transaction.BluetoothController
import nl.tudelft.trustchain.eurotoken.offlinePayment.transaction.Receiver
import nl.tudelft.trustchain.eurotoken.ui.EurotokenBaseFragment
import nl.tudelft.trustchain.eurotoken.ui.transfer.RequestMoneyFragment

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [OfflineMoneyFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class OfflineMoneyFragment : EurotokenBaseFragment(R.layout.fragment_offline_money) {
    private val binding by viewBinding(FragmentOfflineMoneyBinding::bind)
    private lateinit var enableBluetoothLauncher: ActivityResultLauncher<Intent>

    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }

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
            showToast("Receive clicked")
            onReceiveClicked()
        }

        binding.sendMoney.setOnClickListener {
            showToast("Send clicked")
        }
    }

    private fun onReceiveClicked() {
        Log.i("Offline", "Starting bluetooth server")
        val controller = BluetoothController(
            context = requireContext(),
            activityStarter = { intent -> enableBluetoothLauncher.launch(intent) }
        )

        val receiver = Receiver(controller)
        val data = receiver.start(getTrustChainCommunity().myPeer, 0) { socket ->
            showToast("Connection established")
        }

        Log.i("Offline", "Generating QR: $data")
        val bundle = Bundle()
        bundle.putString(RequestMoneyFragment.ARG_DATA, data.toString())
        findNavController().navigate(
            R.id.action_offlineMoneyFragment_to_requestMoneyFragment,
            bundle
        )
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment OfflineMoney.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            OfflineMoneyFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}
