package nl.tudelft.trustchain.eurotoken.entity

import android.content.Context
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64


private fun Context.readPemAsset(path: String): ByteArray =
    assets.open(path).bufferedReader().useLines { lines ->
        Base64.getDecoder().decode(
            lines.filterNot { it.startsWith("-----") }.joinToString("")
        )
    }

private fun Context.privateKey(): PrivateKey =
    KeyFactory.getInstance("EC")
        .generatePrivate(PKCS8EncodedKeySpec(readPemAsset("keys/bank_private.pem")))

private fun Context.publicKey(): PublicKey =
    KeyFactory.getInstance("EC")
        .generatePublic(X509EncodedKeySpec(readPemAsset("keys/bank_public.pem")))


class TokenSigner(ctx: Context) {

    private val priv = ctx.privateKey()
    private val pub = ctx.publicKey()

    init {
        // DEBUG ONLY
        val encoder = Base64.getEncoder()
        android.util.Log.d(
            "TokenSigner",
            "PublicKey (Base64): ${encoder.encodeToString(pub.encoded)}"
        )
        android.util.Log.d(
            "TokenSigner",
            "PrivateKey (Base64): ${encoder.encodeToString(priv.encoded)}"
        )
    }

    fun sign(id: String, amount: Long, dateCreated: Long): ByteArray =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(priv)
            update(toBytes(id, amount, dateCreated))
            sign()
        }

    fun verify(token: BillFaceToken): Boolean =
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(pub)
            update(toBytes(token.id, token.amount, token.dateCreated))
            verify(token.intermediarySignature)
        }

    private fun toBytes(id: String, amount: Long, dateCreated: Long): ByteArray {
        val idBytes = id.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(idBytes.size + 8 + 8)
            .put(idBytes)
            .putLong(amount)
            .putLong(dateCreated)
            .array()
    }
}
