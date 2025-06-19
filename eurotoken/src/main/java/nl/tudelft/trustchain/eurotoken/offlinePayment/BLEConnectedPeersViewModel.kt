package nl.tudelft.trustchain.eurotoken.offlinePayment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.tudelft.ipv8.Peer
import nl.tudelft.trustchain.common.contacts.ContactStore
import nl.tudelft.trustchain.eurotoken.community.EuroTokenCommunity

class BLEConnectedPeersViewModel(
    private val community: EuroTokenCommunity,
    private val contacts: ContactStore
): ViewModel() {
    private val _connectedPeers = MutableStateFlow(emptyList<String>())
    val connectedPeers: StateFlow<List<String>> = _connectedPeers

    init {
        viewModelScope.launch {
            while (true) {
                val connectedBLEPeers = community.getPeers()
                    .filter { peer -> peer.bluetoothAddress != null }
                    .map { peer -> getPeerName(peer) }
                if (_connectedPeers.value != connectedBLEPeers) {
                    _connectedPeers.value = connectedBLEPeers
                }

                delay(2000)
            }
        }
    }

    private fun getPeerName(peer: Peer): String {
        val contact = contacts.getContactFromPublicKey(peer.publicKey)
        return if (contact == null || contact.name == "") {
            peer.bluetoothAddress!!.mac
        } else {
            contact.name
        }
    }
}

class BLEConnectedPeersViewModelFactory(
    private val community: EuroTokenCommunity,
    private val contacts: ContactStore
): ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BLEConnectedPeersViewModel::class.java)) {
            return BLEConnectedPeersViewModel(community, contacts) as T
        }
        throw IllegalStateException("Unknown ViewModel class")
    }
}
