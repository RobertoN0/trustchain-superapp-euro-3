package nl.tudelft.trustchain.eurotoken.ui.bluetooth

import android.os.Bundle
import android.view.View
import android.widget.Toast
import nl.tudelft.trustchain.common.util.viewBinding
import nl.tudelft.trustchain.eurotoken.R
import nl.tudelft.trustchain.eurotoken.databinding.FragmentBluetoothBroadcastBinding
import nl.tudelft.trustchain.eurotoken.ui.EurotokenBaseFragment

class BluetoothBroadcastFragment
    : EurotokenBaseFragment(R.layout.fragment_bluetooth_broadcast) {

    // ➊ UI binding
    private val binding by viewBinding(FragmentBluetoothBroadcastBinding::bind)

    // ➋ Puntatore alla community (ti servirà per i veri broadcast)
    private val euroTokenCommunity by lazy {
        transactionRepository.trustChainCommunity     // vedi punto 1
    }

    // ➌ Viene chiamato subito dopo che la view è stata creata
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ➍ Listener del bottone
        binding.btnSendHello.setOnClickListener {
            // qui in futuro: euroTokenCommunity.broadcastHello()
            Toast.makeText(requireContext(),
                "HELLO inviato (simulato)", Toast.LENGTH_SHORT).show()
        }
    }
}
