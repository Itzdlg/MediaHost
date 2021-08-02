@file:Suppress("unused")

package me.schooltests.mediahost.data.auth

import io.ktor.application.ApplicationCall
import io.ktor.http.HttpStatusCode
import io.ktor.request.header
import io.ktor.util.pipeline.PipelineContext
import me.schooltests.mediahost.managers.OTPManager
import me.schooltests.mediahost.managers.SessionManager
import me.schooltests.mediahost.managers.UserManager
import me.schooltests.mediahost.sql.UserAPIKeysTable
import me.schooltests.mediahost.util.eqIc
import me.schooltests.mediahost.util.hash
import org.apache.commons.codec.binary.Base64
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.EnumSet

/*
To allow API's to properly interface with MediaHost, they have specific permissions.
Rights with requireOTP=true can be executed if the permission is set, but the API
would require a OTP from the user OR the OTP Secret to make one.
 */

enum class APIRights(val permId: Int, val description: String, val requireOTP: Boolean = false) {
    CHANGE_USERNAME(1, "Change your username"),
    RESET_PASSWORD(2, "Reset your password", requireOTP = true),
    GENERATE_API_KEY(3, "Generate an API key"),
    GENERATE_SESSION(4, "Generate a session"),
    EXPIRE_API_KEY(5, "Delete an API key"),
    EXPIRE_SESSION(6, "Expire a session"),
    LIST_API_KEYS(7, "List API keys"),
    LIST_SESSIONS(8, "List sessions"),
    UPLOAD_FILE(9, "Upload files"),
    DELETE_FILE(10, "Delete files"),
    MODIFY_FILE_OPTIONS(11, "Modify file privacy/name"),
    VIEW_PRIVATE_CONTENT(12, "View private files"),
    QUERY_CONTENT(13, "Query uploads"),
    DELETE_ACCOUNT(14, "Delete account", requireOTP = true);

    val binary = 1L shl permId

    companion object {
        fun getById(id: Int): APIRights? {
            return values().toList().stream().filter{ it.permId == id }.findFirst().orElse(null)
        }

        fun EnumSet<APIRights>.toBitMap(): Long {
            var ret = 0L
            for (right in this) {
                ret = ret or right.binary
            }

            return ret
        }

        fun fromBitMap(code: Long): EnumSet<APIRights> {
            val rights = EnumSet.noneOf(APIRights::class.java)
            for (right in values()) {
                if (code and right.binary == right.binary) rights.add(right)
            }

            return rights
        }
    }

    fun allowed(type: AuthType, data: String, otp: String?): ClientAuthentication {
        fun allowedBasic(username: String, password: String): ClientAuthentication {
            val user = UserManager.queryUserByUsername(username)
                ?: return AuthFailure("There is no user with that username.", HttpStatusCode.NotFound)
            if (user.hashedPassword == hash(password + user.salt)) {
                val userRights = AuthType.BASIC.getRights("$username:$password")
                return if (userRights.contains(this)) {
                    AuthSuccessBasic(user)
                } else AuthFailure("You may not perform this action.", HttpStatusCode.Unauthorized)
            }

            return AuthFailure("The provided password is incorrect.", HttpStatusCode.Unauthorized)
        }

        fun allowedAPI(apikey: String): ClientAuthentication {
            val userId = transaction {
                UserAPIKeysTable.select { UserAPIKeysTable.hashedToken eq hash(apikey) }.firstOrNull()?.get(UserAPIKeysTable.userId)
            } ?: return AuthFailure("The specified API key is invalid.", HttpStatusCode.NotFound)

            val user = UserManager.queryUserById(userId) ?: return AuthFailure("Something went wrong.", HttpStatusCode.InternalServerError)
            val rights = AuthType.API.getRights(apikey)

            return if (rights.contains(this)) AuthSuccessAPI(user, apikey, rights)
            else AuthFailure("You may not perform this action.", HttpStatusCode.Unauthorized)
        }

        fun allowedSession(jwt: String): ClientAuthentication {
            val obj = SessionManager.decodeJwt(jwt) ?: return AuthFailure("Improper JWT provided.", HttpStatusCode.BadRequest)
            val expiration = Instant.ofEpochSecond(obj["exp"].asLong)
            if (Instant.now().isAfter(expiration)) return AuthFailure("Expired JWT provided.", HttpStatusCode.Unauthorized)

            val permissions = APIRights.fromBitMap(obj["scope"].asLong)
            if (!permissions.contains(this)) return AuthFailure("You may not perform this action.", HttpStatusCode.Unauthorized)

            val user = UserManager.queryUserById(obj["sub"].asInt) ?: return AuthFailure("Improper JWT provided.", HttpStatusCode.BadRequest)
            return AuthSuccessSession(user, obj)
        }

        val clientAuthentication = when (type) {
            AuthType.BASIC -> {
                val decoded = String(Base64.decodeBase64(data))
                if (!decoded.contains(":")) AuthFailure("Improper Authentication Header.", HttpStatusCode.BadRequest)
                allowedBasic(decoded.split(":")[0], decoded.split(":")[1])
            }

            AuthType.API -> allowedAPI(data)
            AuthType.SESSION -> allowedSession(data)
        }

        if (clientAuthentication is AuthFailure) return clientAuthentication
        if (requireOTP) {
            if (otp == null) return AuthFailure("OTP not provided.", HttpStatusCode.BadRequest)
            val user: User = clientAuthentication.user!!
            val newOTP = OTPManager.generateOTP(user.otpSecret)
            if (otp != newOTP) {
                return AuthFailure("Incorrect OTP provided.", HttpStatusCode.Unauthorized)
            }
        }

        return clientAuthentication
    }

    fun allowed(call: PipelineContext<*, ApplicationCall>): ClientAuthentication {
        val authHeader = call.context.request.header("Authorization")
        val otpHeader = call.context.request.header("X-OTP")

        if (authHeader != null && authHeader.contains(" ")) {
            val type = authHeader.split(" ")[0]
            val data = authHeader.split(" ")[1]

            if (type eqIc "Basic") return allowed(AuthType.BASIC, data, otpHeader)
            else if (type eqIc "Bearer") return allowed(AuthType.SESSION, data, otpHeader)
            else if (type eqIc "Apikey") return allowed(AuthType.API, data, otpHeader)
        }

        return AuthFailure("Improper Authentication Header.", HttpStatusCode.BadRequest)
    }
}