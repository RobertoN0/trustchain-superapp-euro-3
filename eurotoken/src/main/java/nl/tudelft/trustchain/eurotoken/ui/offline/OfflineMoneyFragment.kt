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
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.trustchain.common.util.viewBinding
import nl.tudelft.trustchain.eurotoken.R
import nl.tudelft.trustchain.eurotoken.databinding.FragmentOfflineMoneyBinding
import nl.tudelft.trustchain.eurotoken.offlinePayment.transaction.BluetoothController
import nl.tudelft.trustchain.eurotoken.offlinePayment.transaction.Receiver
import nl.tudelft.trustchain.eurotoken.ui.EurotokenBaseFragment
import nl.tudelft.trustchain.eurotoken.ui.transfer.RequestMoneyFragment
import nl.tudelft.trustchain.common.util.QRCodeUtils
import nl.tudelft.trustchain.eurotoken.community.EuroTokenCommunity
import nl.tudelft.trustchain.eurotoken.ui.transfer.TransferFragment.Companion.ConnectionData
import nl.tudelft.trustchain.eurotoken.ui.transfer.SendMoneyFragment
import org.json.JSONException
import java.util.UUID

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

    private val qrCodeUtils by lazy {
        QRCodeUtils(requireContext())
    }

    private val bluetoothController by lazy {
        BluetoothController(requireContext()) { intent -> startActivityForResult(intent, 9999) }
    }

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
            showToast("Receive clicked")
            onReceiveClicked()
        }

        binding.sendMoney.setOnClickListener {
            showToast("Send clicked")
            qrCodeUtils.startQRScanner(this)
        }
    }

    private fun setupBluetoothClient(serverUuid: String, serverAddress: String = "00:11:22:33:AA:BB") {
        if (!bluetoothController.isBluetoothSupported()) return
        if (!bluetoothController.isBluetoothEnabled()) {
            bluetoothController.requestEnableBluetooth()
            return
        }
        bluetoothController.connectToServer(UUID.fromString(serverUuid), serverAddress) {  }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        qrCodeUtils.parseActivityResult(requestCode, resultCode, data)?.let {
            try {
                Log.i("Offline", "Scanned data: $it")
                //showToast("Scanned data: $it")
                val connectionData = ConnectionData(it)
                Log.i("Offline", "Parsed connection data: $connectionData")
                if (connectionData.type.equals("transfer")) {
                    Log.i( "Offline", "Valid transfer detected: $connectionData")
                    val args = Bundle().apply {
                        putString(SendMoneyFragment.ARG_PUBLIC_KEY, connectionData.publicKey)
                        putLong(SendMoneyFragment.ARG_AMOUNT, connectionData.amount)
                        putString(SendMoneyFragment.ARG_NAME, connectionData.name)
                        putString(SendMoneyFragment.ARG_UUID_BLUETOOTH, connectionData.uuid)
                    }
                    if (!connectionData.uuid.isNullOrEmpty()) {
                        Log.i("Offline", "Setting up bluetooth client with uuid: ${connectionData.uuid}")
                        try {
                            setupBluetoothClient(connectionData.uuid)
                        }
                        catch (e: Exception) {
                            Log.e("Offline", "Failed to setup bluetooth client", e)
                            showToast("Failed to setup bluetooth client")
                        }
                    }
                    Log.i("Offline", "Navigating to send money fragment with args: $args")
//                    // Try to send the addresses of the last X transactions to the peer we have just scanned.
//                    try {
//                        val peer =
//                            findPeer(
//                                defaultCryptoProvider.keyFromPublicBin(connectionData.publicKey.hexToBytes())
//                                    .toString()
//                            )
//                        if (peer == null) {
//                            logger.warn { "Could not find peer from QR code by public key " + connectionData.publicKey }
//                            Toast.makeText(
//                                requireContext(),
//                                "Could not find peer from QR code",
//                                Toast.LENGTH_LONG
//                            )
//                                .show()
//                        }
//                        val euroTokenCommunity = getIpv8().getOverlay<EuroTokenCommunity>()
//                        if (euroTokenCommunity == null) {
//                            Toast.makeText(
//                                requireContext(),
//                                "Could not find community",
//                                Toast.LENGTH_LONG
//                            )
//                                .show()
//                        }
//                        if (peer != null && euroTokenCommunity != null) {
//                            euroTokenCommunity.sendAddressesOfLastTransactions(peer)
//                        }
//                    } catch (e: Exception) {
//                        logger.error { e }
//                        Toast.makeText(
//                            requireContext(),
//                            "Failed to send transactions",
//                            Toast.LENGTH_LONG
//                        )
//                            .show()
//                    }
                    findNavController().navigate(
                        R.id.action_offlineMoneyFragment_to_sendMoneyFragment,
                        args
                    )
                }
            } catch (e: JSONException) {
                Toast.makeText(requireContext(), "Scan failed, try again", Toast.LENGTH_LONG).show()
            }
            Log.i("Offline", "Parsed data: $it")
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
