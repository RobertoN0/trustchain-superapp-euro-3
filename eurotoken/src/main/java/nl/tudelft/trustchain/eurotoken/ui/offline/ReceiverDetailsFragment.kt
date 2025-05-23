package nl.tudelft.trustchain.eurotoken.ui.offline

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.fragment.findNavController
import nl.tudelft.trustchain.common.util.viewBinding
import nl.tudelft.trustchain.eurotoken.R
import nl.tudelft.trustchain.eurotoken.databinding.FragmentReceiverDetailsBinding
import nl.tudelft.trustchain.eurotoken.offlinePayment.transaction.BluetoothController
import nl.tudelft.trustchain.eurotoken.offlinePayment.transaction.Receiver
import nl.tudelft.trustchain.eurotoken.ui.EurotokenBaseFragment
import nl.tudelft.trustchain.eurotoken.ui.transfer.RequestMoneyFragment

/**
 * A simple [Fragment] subclass.
 * Use the [ReceiverDetailsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ReceiverDetailsFragment : EurotokenBaseFragment(R.layout.fragment_receiver_details) {
    private val binding by viewBinding(FragmentReceiverDetailsBinding::bind)
    private lateinit var enableBluetoothLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableBluetoothLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                Toast.makeText(requireContext(), "Bluetooth not enabled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initially disable the continue button
        binding.continueButton.isEnabled = false

        // Add listeners to validate inputs in real-time
        binding.recipientNameInput.addTextChangedListener(inputWatcher)
        binding.amountInput.addTextChangedListener(inputWatcher)

        binding.continueButton.setOnClickListener {
            if (validateInputs()) {
                val controller = BluetoothController(
                    context = requireContext(),
                    activityStarter = { intent -> enableBluetoothLauncher.launch(intent) }
                )

                val receiver = Receiver(controller)
                val amount = binding.amountInput.text.toString().trim().toInt()
                val name = binding.recipientNameInput.text.toString().trim()
                val data = receiver.start(getTrustChainCommunity().myPeer, name, amount) { socket ->
                    requireActivity().runOnUiThread {
                        findNavController().navigate(
                            R.id.action_requestMoneyFragment_to_transactionProgressFragment,
                        )
                    }
                }

                Log.i("Offline", "Generating QR: $data")
                val bundle = Bundle()
                bundle.putString(RequestMoneyFragment.ARG_DATA, data.toString())
                findNavController().navigate(
                    R.id.action_receiverDetailsFragment_to_requestMoneyFragment,
                    bundle
                )
            } else {
                handleUserInput()
            }
        }

        binding.backButton.setOnClickListener {
            findNavController().navigate(R.id.action_receiverDetailsFragment_to_offlineMoneyFragment)
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
            Toast.makeText(requireContext(), "Please enter a name", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = amountText.toIntOrNull()
        if (amount == null || amount < 1) {
            Toast.makeText(
                requireContext(),
                "Please enter a valid whole number of Euro Tokens",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        Toast.makeText(requireContext(), "Name: $name, Amount: €$amount", Toast.LENGTH_SHORT).show()
    }
}
