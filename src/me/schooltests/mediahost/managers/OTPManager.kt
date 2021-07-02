package me.schooltests.mediahost.managers

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator
import org.apache.commons.codec.binary.Base32
import java.time.Instant
import javax.crypto.KeyGenerator
import javax.crypto.spec.SecretKeySpec

object OTPManager {
    private val totp = TimeBasedOneTimePasswordGenerator()

    fun generateSecretKey(): String {
        val keyGen = KeyGenerator.getInstance(totp.algorithm)
        keyGen.init(160)

        return Base32().encodeAsString(keyGen.generateKey().encoded)
    }

    fun generateOTP(secret: String): String {
        val keyGen = KeyGenerator.getInstance(totp.algorithm)
        val bytes: ByteArray = Base32().decode(secret)
        val secretKey = SecretKeySpec(bytes, 0, bytes.size, keyGen.algorithm)

        return String.format("%06d", totp.generateOneTimePassword(secretKey, Instant.now()))
    }
}