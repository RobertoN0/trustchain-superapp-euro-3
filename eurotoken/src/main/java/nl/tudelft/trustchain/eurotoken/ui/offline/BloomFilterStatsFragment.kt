package nl.tudelft.trustchain.eurotoken.ui.offline

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import nl.tudelft.trustchain.common.contacts.ContactStore
import nl.tudelft.trustchain.eurotoken.R
import nl.tudelft.trustchain.eurotoken.community.EuroTokenCommunity
import nl.tudelft.trustchain.eurotoken.databinding.FragmentBloomFilterStatsBinding
import nl.tudelft.trustchain.eurotoken.offlinePayment.BLEConnectedPeersViewModel
import nl.tudelft.trustchain.eurotoken.offlinePayment.BLEConnectedPeersViewModelFactory
import nl.tudelft.trustchain.eurotoken.offlinePayment.BloomFilerStatsViewModel
import nl.tudelft.trustchain.eurotoken.offlinePayment.BloomFilterViewModelFactory
import nl.tudelft.trustchain.eurotoken.ui.EurotokenBaseFragment


class BloomFilterStatsFragment : EurotokenBaseFragment(R.layout.fragment_bloom_filter_stats) {
    private lateinit var binding: FragmentBloomFilterStatsBinding
    private lateinit var bfViewModel: BloomFilerStatsViewModel
    private lateinit var peerViewModel: BLEConnectedPeersViewModel


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBloomFilterStatsBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initBFViewModel()
        initPeerViewModel()

        lifecycleScope.launch {
            bfViewModel.capacityUsed.flowWithLifecycle(lifecycle)
                .collectLatest { capacity ->
                    binding.capacityValue.text = capacity.toString()
                }
        }
        lifecycleScope.launch {
            bfViewModel.receivedCount.flowWithLifecycle(lifecycle)
                .collectLatest { count ->
                    binding.receivedCountValue.text = count.toString()
                }
        }

        lifecycleScope.launch {
            peerViewModel.connectedPeers.flowWithLifecycle(lifecycle)
                .collectLatest { peers ->
                    if (peers.isEmpty())
                        binding.connectedPeersText.text = "No Bluetooth peers connected"
                    else
                        binding.connectedPeersText.text = peers.joinToString("\n")
                }
        }
    }

    private fun initBFViewModel() {
        val bfManager = getIpv8().getOverlay<EuroTokenCommunity>()?.bfManager
        if (bfManager == null) {
            Toast.makeText(requireContext(), "Unable to obtain BloomFilterManager", Toast.LENGTH_LONG).show()
            return
        }
        val factory = BloomFilterViewModelFactory(bfManager)
        bfViewModel = ViewModelProvider(this, factory).get(BloomFilerStatsViewModel::class.java)
    }

    private fun initPeerViewModel() {
        val factory = BLEConnectedPeersViewModelFactory(getIpv8().getOverlay<EuroTokenCommunity>()
            ?: throw IllegalStateException("EuroTokenCommunity not configured"),
            ContactStore.getInstance(requireContext())
        )
        peerViewModel = ViewModelProvider(this, factory).get(BLEConnectedPeersViewModel::class.java)
    }
}
