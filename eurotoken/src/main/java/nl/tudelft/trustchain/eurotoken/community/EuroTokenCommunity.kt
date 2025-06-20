package nl.tudelft.trustchain.eurotoken.community

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import android.util.Log
import kotlin.random.Random
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.trustchain.TrustChainTransaction
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.common.eurotoken.GatewayStore
import nl.tudelft.trustchain.common.eurotoken.Transaction
import nl.tudelft.trustchain.common.eurotoken.TransactionRepository
import nl.tudelft.trustchain.common.eurotoken.blocks.EuroTokenOfflineTransferValidator
import nl.tudelft.trustchain.eurotoken.EuroTokenMainActivity.EurotokenPreferences.DEMO_MODE_ENABLED
import nl.tudelft.trustchain.eurotoken.EuroTokenMainActivity.EurotokenPreferences.EUROTOKEN_SHARED_PREF_NAME
import nl.tudelft.trustchain.eurotoken.db.BloomFilterTransmissionPayload
import nl.tudelft.trustchain.eurotoken.db.TokenStore
import nl.tudelft.trustchain.eurotoken.db.TrustStore
import nl.tudelft.trustchain.eurotoken.entity.BFSpentMoniesManager
import nl.tudelft.trustchain.eurotoken.entity.BillFaceToken
import nl.tudelft.trustchain.eurotoken.entity.Event
import nl.tudelft.trustchain.eurotoken.entity.TokenSigner
import nl.tudelft.trustchain.eurotoken.ui.settings.DefaultGateway

data class BroadcastMessage(val senderId: String, val message: String)

class EuroTokenCommunity(
    store: GatewayStore,
    trustStore: TrustStore,
    tokenStore: TokenStore,
    context: Context,
) : Community() {
    override val serviceId = "f0eb36102436bd55c7a3cdca93dcaefb08df0750"

    private lateinit var transactionRepository: TransactionRepository

    private var myTokenStore: TokenStore

    val bfManager: BFSpentMoniesManager

    private val tokenSigner by lazy { TokenSigner(context) }

    /**
     * The [TrustStore] used to fetch and update trust scores from peers.
     */
    private var myTrustStore: TrustStore

    /**
     * The context used to access the shared preferences.
     */
    private var myContext: Context

    private val _bluetoothBroadcasts = MutableLiveData<List<BroadcastMessage>>(emptyList())
    val bluetoothBroadcasts: LiveData<List<BroadcastMessage>> get() = _bluetoothBroadcasts

    private val _securityAlerts = MutableLiveData<Event<String>>()
    val securityAlerts: LiveData<Event<String>> = _securityAlerts

    init {
        messageHandlers[MessageId.ROLLBACK_REQUEST] = ::onRollbackRequestPacket
        messageHandlers[MessageId.ATTACHMENT] = ::onLastAddressPacket
        messageHandlers[MessageId.BLUETOOTH_BROADCAST] = ::onBluetoothBroadcastPacket
        messageHandlers[MessageId.BLOOM_FILTER] = ::onBloomFilterTransmissionPacket

        if (store.getPreferred().isEmpty()) {
            DefaultGateway.addGateway(store)
        }
        myTokenStore = tokenStore
        myTrustStore = trustStore
        myContext = context
        bfManager = BFSpentMoniesManager(
            myTokenStore,
            "shared",
        )
    }

    private fun myCheckSpending(transaction: TrustChainTransaction) {
        // Validate the transaction payload
        Log.d("EuroOfflineValidator", "Validating transaction")
        val serializedTokens = transaction[TransactionRepository.KEY_SERIALIZED_TOKENS] as? String
            ?: throw EuroTokenOfflineTransferValidator.InvalidTokenPayload("Tokens not found in transaction")
        // Deserialize the tokens
        Log.d("EuroOfflineValidator", "Found tokens in transaction, deserializing...")
        val tokens = try {
            BillFaceToken.deserializeTokenList(serializedTokens)
        } catch (e: Exception) {
            throw EuroTokenOfflineTransferValidator.InvalidTokenPayload("Failed to deserialize tokens")
        }


        val invalid = tokens.firstOrNull { !tokenSigner.verify(it) }
        if (invalid != null) {
            _securityAlerts.postValue(
                Event("Forged token detected. Transaction rejected.")
            )
            Log.d("EuroOfflineValidator", "Invalid token signature for token: ${invalid.id}, amount: ${invalid.amount}, dateCreated: ${invalid.dateCreated}")
            throw EuroTokenOfflineTransferValidator.ForgedTokenSignature(
                "Invalid signature for token ${invalid.id}"
            )
        }
        if (bfManager.isDoubleSpent(tokens)){
            _securityAlerts.postValue(
                Event("Double spending detected! Transaction rejected.")
            )
            Log.d("EuroOfflineValidator", "Double spending detected for tokens: $tokens")
            throw EuroTokenOfflineTransferValidator.OfflineDoubleSpendingDetected("Double spending detected for tokens: $tokens")
        }
    }

    private fun onBluetoothBroadcastPacket(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(BroadcastBluetoothPayload.Deserializer)
        val message = payload.message.toString(Charsets.UTF_8)
        val senderId = peer.key.toString()
        val current = _bluetoothBroadcasts.value.orEmpty().toMutableList()
        current.add(BroadcastMessage(senderId, message))
        _bluetoothBroadcasts.postValue(current)
        Log.d("EuroTokenCommunity", "Received Bluetooth broadcast from ${peer.key} with message: $message")
    }

    private fun onBloomFilterTransmissionPacket(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(BloomFilterTransmissionPayload.Deserializer)
        val bloomBytes: ByteArray = payload.bloomFilterData
        val mergeSuccess = bfManager.processReceivedBloomFilter(bloomBytes)
        val senderId = peer.key.toString()
        val messageText = if (mergeSuccess) {
            "Received bloom filter from $senderId"
        } else {
            "Error during merge process, bf from $senderId"
        }
        val currentList = _bluetoothBroadcasts.value.orEmpty().toMutableList()
        currentList.add(BroadcastMessage(senderId, messageText))
        _bluetoothBroadcasts.postValue(currentList)
        Log.d(
            "EuroTokenCommunity",
            "onBloomFilter: peer=${peer.address}, mergeSuccess=$mergeSuccess"
        )
    }


    /**
     * Broadcasts a message to all connected peers that have a Bluetooth address.
     *
     * @param messageText The text message to broadcast.
     */
    fun broadcastBluetoothMessage(messageText: String) {
        val payload = BroadcastBluetoothPayload(
            id      = "bluetooth_broadcast",
            message = messageText.toByteArray(Charsets.UTF_8)
        )
        val packet = serializePacket(
            MessageId.BLUETOOTH_BROADCAST,
            payload,
            encrypt = false
        )
        val bluetoothPeers = getPeers().filter { peer ->
            peer.bluetoothAddress != null
        }

        bluetoothPeers.forEach { peer ->
            send(peer, packet)
        }
    }

    fun broadcastBloomFilter(update_on_send: Boolean = false) {
        val bloomFilter = myTokenStore.getBloomFilter("shared")
        if (update_on_send) {
            // TODO
        }
        val payload = BloomFilterTransmissionPayload(bloomFilter!!.toByteArray())
        val packet = serializePacket(
            MessageId.BLOOM_FILTER,
            payload,
            encrypt = false
        )
        getPeers().forEach { peer ->
            send(peer, packet)
        }
    }


    @JvmName("setTransactionRepository1")
    fun setTransactionRepository(transactionRepositoryLocal: TransactionRepository) {
        transactionRepository = transactionRepositoryLocal
        transactionRepository.trustChainCommunity.registerTransactionValidator(
            TransactionRepository.BLOCK_TYPE_OFFLINE_TRANSFER,
            EuroTokenOfflineTransferValidator(transactionRepository, ::myCheckSpending)
        )
    }

    private fun onRollbackRequestPacket(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(RollbackRequestPayload.Deserializer)
        onRollbackRequest(peer, payload)
    }

    /**
     * Called upon receiving MessageId.ATTACHMENT packet.
     * Payload consists out of latest 50 public keys the sender transacted with.
     * This function parses the packet and increments the trust by 1 for every
     * key received.
     * Format is: '<key>, <key>, ..., <key>' where key is the hex-encoded public key.
     * @param packet : the corresponding packet that contains the right payload.
     */
    private fun onLastAddressPacket(packet: Packet) {
        val (_, payload) =
            packet.getDecryptedAuthPayload(
                TransactionsPayload.Deserializer,
                myPeer.key as PrivateKey
            )

        val addresses: List<ByteArray> = String(payload.data).split(",").map { it.toByteArray() }
        for (i in addresses.indices) {
            myTrustStore.incrementTrust(addresses[i])
        }
    }

    private fun onRollbackRequest(
        peer: Peer,
        payload: RollbackRequestPayload
    ) {
        transactionRepository.attemptRollback(peer, payload.transactionHash)
    }

    fun connectToGateway(
        public_key: String,
        ip: String,
        port: Int,
        payment_id: String
    ) {
        val key = defaultCryptoProvider.keyFromPublicBin(public_key.hexToBytes())
        val address = IPv4Address(ip, port)
        val peer = Peer(key, address)

        val payload = MessagePayload(payment_id)

        val packet =
            serializePacket(
                MessageId.GATEWAY_CONNECT,
                payload
            )

        send(peer, packet)
    }

    fun requestRollback(
        transactionHash: ByteArray,
        peer: Peer
    ) {
        val payload = RollbackRequestPayload(transactionHash)

        val packet =
            serializePacket(
                MessageId.ROLLBACK_REQUEST,
                payload
            )

        send(peer, packet)
    }

    object MessageId {
        const val GATEWAY_CONNECT = 1
        const val ROLLBACK_REQUEST = 1
        const val ATTACHMENT = 4
        const val BLUETOOTH_BROADCAST = 5
        const val BLOOM_FILTER = 6
    }

    class Factory(
        private val store: GatewayStore,
        private val trustStore: TrustStore,
        private val context: Context,
        private val tokenStore: TokenStore,
    ) : Overlay.Factory<EuroTokenCommunity>(EuroTokenCommunity::class.java) {
        override fun create(): EuroTokenCommunity {
            return EuroTokenCommunity(store, trustStore, tokenStore, context)
        }
    }

    /**
     * Generate a public key based on the [seed].
     * @param seed : the seed used to generate the public key.
     */
    private fun generatePublicKey(seed: Long): String {
        // Initialize Random with seed
        val random = Random(seed)

        // Generate a random public key of 148 hexadecimal characters
        val key = random.nextBytes(148)
        return key.toHex()
    }

    /**
     * Generate [numberOfKeys] public keys based on the [seed].
     * @param numberOfKeys : the number of keys to generate.
     * @param seed : the seed used to generate the public keys.
     */
    private fun generatePublicKeys(
        numberOfKeys: Int,
        seed: Long = 1337
    ): List<String> {
        val publicKeys = mutableListOf<String>()
        for (i in 0 until numberOfKeys) {
            publicKeys.add(generatePublicKey(seed + i))
        }
        return publicKeys
    }

    /**
     * Called after the user has finished a transaction with the other party.
     * Sends the [num] public keys of latest transaction counterparties to the receiver.
     * When DEMO mode is enabled, it generates 50 random keys instead.
     * @param peer : the peer to send the keys to.
     * @param num : the number of keys to send.
     */
    fun sendAddressesOfLastTransactions(
        peer: Peer,
        num: Int = 50
    ) {
        val pref = myContext.getSharedPreferences(EUROTOKEN_SHARED_PREF_NAME, Context.MODE_PRIVATE)
        val demoModeEnabled = pref.getBoolean(DEMO_MODE_ENABLED, false)

        val addresses: ArrayList<String> = ArrayList()
        // Add own public key to list of addresses.
        addresses.add(myPeer.publicKey.keyToBin().toHex())
        if (demoModeEnabled) {
            // Generate [num] addresses if in demo mode
            addresses.addAll(generatePublicKeys(num))
        } else {
            // Get all addresses of the last [num] incoming transactions
            addresses.addAll(
                transactionRepository.getTransactions(num).map { transaction: Transaction ->
                    transaction.sender.toString()
                }
            )
        }

        val payload = TransactionsPayload(EVAId.EVA_LAST_ADDRESSES, addresses.joinToString(separator = ",").toByteArray())

        val packet =
            serializePacket(
                MessageId.ATTACHMENT,
                payload,
                encrypt = true,
                recipient = peer
            )

        // Send the list of addresses to the peer using EVA
        if (evaProtocolEnabled) {
            evaSendBinary(
                peer,
                EVAId.EVA_LAST_ADDRESSES,
                payload.id,
                packet
            )
        } else {
            send(peer, packet)
        }
    }

    /**
     * Every community initializes a different version of the EVA protocol (if enabled).
     * To distinguish the incoming packets/requests an ID must be used to hold/let through the
     * EVA related packets.
     */
    object EVAId {
        const val EVA_LAST_ADDRESSES = "eva_last_addresses"
    }
}
