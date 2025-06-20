package nl.tudelft.trustchain.eurotoken

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

import nl.tudelft.trustchain.eurotoken.entity.BillFaceToken
import nl.tudelft.trustchain.eurotoken.entity.TokenSigner

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TokenSignerTest {

    private lateinit var ctx: Context
    private lateinit var signer: TokenSigner

    @Before
    fun setUp() {
        ctx = InstrumentationRegistry.getInstrumentation().targetContext
        signer = TokenSigner(ctx)
    }

    @Test
    fun sign_and_verify_succeeds() {
        val now    = System.currentTimeMillis()
        val id     = BillFaceToken.createId("peer123", now)
        val amount = 500L                            // 5,00 €

        val signature = signer.sign(id, amount, now)

        val token = BillFaceToken(
            id = id,
            amount = amount,
            intermediarySignature = signature,
            dateCreated = now
        )

        assertTrue(signer.verify(token))
    }

    @Test
    fun verify_fails_if_amount_tampered() {
        val now    = System.currentTimeMillis()
        val id     = BillFaceToken.createId("peer123", now)
        val amount = 500L

        val signature = signer.sign(id, amount, now)
        val altered = BillFaceToken(
            id = id,
            amount = amount + abs(100),
            intermediarySignature = signature,
            dateCreated = now
        )

        assertFalse("Verification should not succeed, forged token", signer.verify(altered))
    }
}
