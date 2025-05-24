package nl.tudelft.trustchain.eurotoken.ui.offline

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.common.util.viewBinding
import nl.tudelft.trustchain.eurotoken.R
import nl.tudelft.trustchain.eurotoken.databinding.FragmentReceiverDetailsBinding
import nl.tudelft.trustchain.eurotoken.offlinePayment.OfflineTransactionViewModel
import nl.tudelft.trustchain.eurotoken.offlinePayment.TransactionDetails
import nl.tudelft.trustchain.eurotoken.offlinePayment.bluetooth.PermissionHelper
import nl.tudelft.trustchain.eurotoken.ui.EurotokenBaseFragment
import nl.tudelft.trustchain.eurotoken.ui.transfer.RequestMoneyFragment
import org.json.JSONObject
import java.util.UUID


class ReceiverDetailsFragment : EurotokenBaseFragment(R.layout.fragment_receiver_details) {
    private val binding by viewBinding(FragmentReceiverDetailsBinding::bind)
    private val transactionViewModel: OfflineTransactionViewModel by activityViewModels()
    private lateinit var bluetoothPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var enableBluetoothLauncher: ActivityResultLauncher<Intent>
    private lateinit var discoverableLauncher: ActivityResultLauncher<Intent>
    private var pendingBluetoothAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bluetoothPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                pendingBluetoothAction?.invoke()
                pendingBluetoothAction = null
            } else {
                showToast("Bluetooth permissions are required to proceed")
            }
        }

        enableBluetoothLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                showToast("Bluetooth not enabled")
            }
        }

        discoverableLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                showToast("Device not discoverable, other devices may not find it.")
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isEditable = arguments?.getBoolean(ARG_IS_EDITABLE) ?: true
        if (isEditable) {
            // Add listeners to validate inputs in real-time
            binding.recipientNameInput.addTextChangedListener(inputWatcher)
            binding.amountInput.addTextChangedListener(inputWatcher)
        } else {
            binding.recipientNameInput.setText(arguments?.getString("name") ?: "Anonymous")
            binding.recipientNameInput.isEnabled = false
            binding.amountInput.setText((arguments?.getInt("amount") ?: 0).toString())
            binding.amountInput.isEnabled = false
        }

        binding.continueButton.isEnabled = validateInputs()

        binding.continueButton.setOnClickListener {
            if (validateInputs()) {
                if (isEditable) {
                    proceedAsReceiver()
                } else {
                    proceedAsSender()
                }
            } else {
                handleUserInput()
            }
        }

        binding.backButton.setOnClickListener {
            findNavController().navigate(R.id.action_receiverDetailsFragment_to_offlineMoneyFragment)
        }
    }

    @SuppressLint("MissingPermission")
    private fun proceedAsSender() {
        // Ask for Bluetooth permissions
        if (!PermissionHelper.hasAllPermissions(requireContext())) {
            pendingBluetoothAction = { proceedAsSender() }
            bluetoothPermissionLauncher.launch(PermissionHelper.BluetoothPermissions.requiredPermissions)
            return
        }

        // Enable Bluetooth
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        enableBluetoothLauncher.launch(enableBtIntent)

        val details = transactionViewModel.transactionDetails.value
            ?: throw IllegalStateException("No transaction details known")

        transactionViewModel.startTransactionAsSender(
            requireContext(),
            details.deviceName,
            details.uuid,
            onConnected = {
            },
            onError = {
                requireActivity().runOnUiThread {
                    showToast("Error while establishing connection")
                }
            }
        )
        findNavController().navigate(R.id.action_receiverDetailsFragment_to_transactionProgressFragment)
    }

    @SuppressLint("MissingPermission")
    private fun proceedAsReceiver() {
        // Ask for Bluetooth permissions
        if (!PermissionHelper.hasAllPermissions(requireContext())) {
            pendingBluetoothAction = { proceedAsReceiver() }
            bluetoothPermissionLauncher.launch(PermissionHelper.BluetoothPermissions.requiredPermissions)
            return
        }
        // Enable Bluetooth
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        enableBluetoothLauncher.launch(enableBtIntent)
        val bluetoothManager = requireContext().getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager?.adapter
            ?: throw UnsupportedOperationException("Bluetooth not supported")

        // Make device discoverable
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        }
        discoverableLauncher.launch(discoverableIntent)

        val publicKey = getTrustChainCommunity().myPeer.publicKey
        val name = binding.recipientNameInput.text.toString().trim()
        val amount = binding.amountInput.text.toString().trim().toInt()
        val uuid = UUID.randomUUID()
        val serverDeviceName = bluetoothAdapter.name
        transactionViewModel.setDetails(TransactionDetails(name, publicKey, amount, uuid, serverDeviceName))

        transactionViewModel.startTransactionAsReceiver(
            requireContext(),
            "OfflineTransaction",
            uuid,
            onConnected = { onServerConnectionSuccess() },
            onError = { onServerConnectionFailure() }
        )

        val data = JSONObject()
        data.put(ARG_NAME, name)
        data.put(ARG_AMOUNT, amount)
        data.put(ARG_TYPE, "transfer")
        data.put("public_key", publicKey.keyToBin().toHex())
        data.put("uuid", uuid.toString())
        data.put("device_name", serverDeviceName)

        Log.i("Offline", "Generating QR: $data")
        val bundle = Bundle()
        bundle.putString(RequestMoneyFragment.ARG_DATA, data.toString())
        findNavController().navigate(
            R.id.action_receiverDetailsFragment_to_requestMoneyFragment,
            bundle
        )
    }

    private fun onServerConnectionFailure() {
        requireActivity().runOnUiThread {
            showToast("Failed while establishing connection")
        }
    }

    private fun onServerConnectionSuccess() {
        requireActivity().runOnUiThread {
            findNavController().navigate(R.id.action_requestMoneyFragment_to_transactionProgressFragment)
        }
    }

    private val inputWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            binding.continueButton.isEnabled = validateInputs()
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }

    private fun validateInputs(): Boolean {
        val name = binding.recipientNameInput.text.toString().trim()
        val amountText = binding.amountInput.text.toString().trim()
        val amount = amountText.toIntOrNull()
        return name.isNotEmpty() && amount != null && amount >= 1
    }

    private fun handleUserInput() {
        val name = binding.recipientNameInput.text.toString().trim()
        val amountText = binding.amountInput.text.toString().trim()

        if (name.isEmpty()) {
            showToast("Please enter a name")
            return
        }

        val amount = amountText.toIntOrNull()
        if (amount == null || amount < 1) {
            showToast("Please enter a valid whole number of Euro Tokens")
            return
        }

        showToast("Name: $name, Amount: €$amount")
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val ARG_IS_EDITABLE = "isEditable"
        const val ARG_NAME = "name"
        const val ARG_AMOUNT = "amount"
        const val ARG_TYPE = "type"
    }
}
