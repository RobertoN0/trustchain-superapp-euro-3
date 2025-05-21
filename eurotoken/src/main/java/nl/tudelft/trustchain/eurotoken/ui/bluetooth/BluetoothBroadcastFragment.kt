package nl.tudelft.trustchain.eurotoken.ui.bluetooth

import android.Manifest
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresPermission
import nl.tudelft.trustchain.common.util.viewBinding
import nl.tudelft.trustchain.eurotoken.R
import nl.tudelft.trustchain.eurotoken.databinding.FragmentBluetoothBroadcastBinding
import nl.tudelft.trustchain.eurotoken.ui.EurotokenBaseFragment
import nl.tudelft.trustchain.eurotoken.utils.BluetoothBroadcastManager
import nl.tudelft.trustchain.eurotoken.utils.BluetoothPermissionHelper

class BluetoothBroadcastFragment : EurotokenBaseFragment(R.layout.fragment_bluetooth_broadcast) {

    // UI binding
    private val binding by viewBinding(FragmentBluetoothBroadcastBinding::bind)

    // Puntatore alla community (ti servirà per i veri broadcast)
    private val euroTokenCommunity by lazy {
        transactionRepository.trustChainCommunity
    }

    // Aggiungi queste proprietà per Bluetooth
    private lateinit var bluetoothBroadcastManager: BluetoothBroadcastManager
    private lateinit var bluetoothPermissionHelper: BluetoothPermissionHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inizializza le classi helper
        bluetoothBroadcastManager = BluetoothBroadcastManager(requireContext())
        bluetoothPermissionHelper = BluetoothPermissionHelper(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Listener del bottone - versione migliorata
        binding.btnBluetoothBroadcast.setOnClickListener {
            // Verifica i permessi prima di procedere
            if (!bluetoothPermissionHelper.hasRequiredPermissions()) {
                // Chiedi i permessi se non sono già concessi
                bluetoothPermissionHelper.showPermissionExplanationDialog()
                return@setOnClickListener
            }

            // Here permission granted, proceed with broadcast
            if (bluetoothBroadcastManager.canBroadcast()) {
                val success = bluetoothBroadcastManager.broadcastHello()
                if (success) {
                    Toast.makeText(
                        requireContext(),
                        "HELLO message broadcast via Bluetooth!",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Failed to broadcast HELLO message",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    requireContext(),
                    "Bluetooth is not available or enabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Gestione dei risultati dei permessi
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        val handled = bluetoothPermissionHelper.handlePermissionResult(requestCode, permissions, grantResults)
        if (handled && bluetoothPermissionHelper.hasRequiredPermissions()) {
            // I permessi sono stati concessi, possiamo procedere con il broadcasting
            if (bluetoothBroadcastManager.canBroadcast()) {
                val success = bluetoothBroadcastManager.broadcastHello()
                if (success) {
                    Toast.makeText(
                        requireContext(),
                        "HELLO message broadcast via Bluetooth!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        if (!handled) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
}
