package nl.tudelft.trustchain.eurotoken.entity
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import java.util.Base64

@Serializable
data class BillFaceToken(
    val id: String,
    var amount: Long,
    val intermediarySignature: ByteArray,
    val isSpent: Boolean = false,
    val dateCreated: Long,
    var dateReceived: Long? = null
) {
    companion object {
        fun createId(peerId: String, timestamp: Long): String {
            val rawId = "$peerId:$timestamp"
            val bytes = rawId.toByteArray()
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }

        @OptIn(ExperimentalSerializationApi::class)
        fun serializeTokenList(tokens: List<BillFaceToken>): String {
            val bytes = ProtoBuf.encodeToByteArray(
                ListSerializer(BillFaceToken.serializer()),
                tokens
            )
            return Base64.getEncoder().encodeToString(bytes)
        }

        @OptIn(ExperimentalSerializationApi::class)
        fun deserializeTokenList(serializedTokens: String): List<BillFaceToken> {
            val bytes = Base64.getDecoder().decode(serializedTokens)
            return ProtoBuf.decodeFromByteArray(
                ListSerializer(BillFaceToken.serializer()),
                bytes
            )
        }
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BillFaceToken

        if (id != other.id) return false
        if (amount != other.amount) return false
        if (!intermediarySignature.contentEquals(other.intermediarySignature)) return false
        if (isSpent != other.isSpent) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + amount.hashCode()
        result = 31 * result + intermediarySignature.contentHashCode()
        result = 31 * result + isSpent.hashCode()
        return result
    }
}

