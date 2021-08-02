package me.schooltests.mediahost.managers

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.schooltests.mediahost.ApplicationSettings
import me.schooltests.mediahost.data.auth.APIRights
import me.schooltests.mediahost.data.auth.APIRights.Companion.toBitMap
import me.schooltests.mediahost.data.auth.AuthType
import me.schooltests.mediahost.data.auth.User
import me.schooltests.mediahost.data.content.asJson
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.time.Instant
import java.util.Base64
import java.util.EnumSet
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec


object SessionManager {
    // Refresh Token Format:
    //   uuid@userid:ip
    // Refresh Token Example:
    //   XXXX-XXXX-XXXX-XXXX@23:91.192.93.100
    val refreshTokens: MutableSet<String> = mutableSetOf()
    fun refreshJwt(jwt: JsonObject): String? {
        val namespacedRefreshToken = "${ApplicationSettings.jwtNamespace}/refresh/token"
        val namespacedRefreshBefore = "${ApplicationSettings.jwtNamespace}/refresh/before"

        val subject = UserManager.queryUserById(jwt["sub"].asInt)!!
        val refreshToken = jwt[namespacedRefreshToken].asString
        val refreshBefore = Instant.ofEpochSecond(jwt[namespacedRefreshBefore].asLong)

        return if (refreshTokens.contains(refreshToken) && Instant.now().isBefore(refreshBefore)) {
            generateJwt(
                subject,
                issuedAt = Instant.ofEpochSecond(jwt["iat"].asLong),
                permissions = APIRights.fromBitMap(jwt["scope"].asLong),
                customClaims = { json ->
                    json.addProperty(namespacedRefreshToken, refreshToken.toString())
                    json.addProperty(namespacedRefreshBefore, refreshBefore.epochSecond)
                }
            )
        } else null
    }

    fun validateRefreshToken(token: String) = refreshTokens.add(token)
    fun invalidateRefreshToken(token: String) = refreshTokens.remove(token)

    const val JWT_HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}"
    fun generateJwt(subject: User,
                    issuedAt: Instant = Instant.now(),
                    expiration: Instant = Instant.now().plusSeconds(ApplicationSettings.jwtExpiration),
                    permissions: EnumSet<APIRights> = AuthType.SESSION.getRights(""),
                    customClaims: (JsonObject) -> (Unit) = {}
    ): String {
        val headerEncoded = Base64.getEncoder().encodeToString(JWT_HEADER.toByteArray())
        val payloadEncoded = Base64.getEncoder().encodeToString(JsonObject().apply {
            addProperty("sub", subject.userId)
            addProperty("iat", issuedAt.epochSecond)
            addProperty("exp", expiration.epochSecond)
            addProperty("scope", permissions.toBitMap())

            customClaims.invoke(this)
        }.asJson.toByteArray())
        val firstPart = "$headerEncoded.$payloadEncoded"
        val signature = signJwt(firstPart) ?: return ""
        val jwt = "$firstPart.$signature"
        return jwt
    }

    fun decodeJwt(jwt: String): JsonObject? {
        val parts = jwt.split(".")
        if (parts.size != 3
            || parts[2] != signJwt("${parts[0]}.${parts[1]}")) return null

        val payload = Base64.getDecoder().decode(parts[1]).toString(StandardCharsets.UTF_8)
        val payloadObj = JsonParser.parseString(payload).asJsonObject

        return payloadObj
    }

    private fun signJwt(data: String, secret: String = ApplicationSettings.jwtSecret): String? {
        return try {

            //MessageDigest digest = MessageDigest.getInstance("SHA-256");
            val hash: ByteArray =
                secret.toByteArray(StandardCharsets.UTF_8) //digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            val sha256Hmac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(hash, "HmacSHA256")
            sha256Hmac.init(secretKey)
            val signedBytes = sha256Hmac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
            Base64.getEncoder().encodeToString(signedBytes)
        } catch (ex: NoSuchAlgorithmException) {
            null
        } catch (ex: InvalidKeyException) {
            null
        }
    }
}