package me.schooltests.mediahost.util

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.ktor.application.*
import io.ktor.http.HttpStatusCode
import io.ktor.request.*
import io.ktor.response.respondText
import io.ktor.server.netty.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import me.schooltests.mediahost.ApplicationSettings
import me.schooltests.mediahost.managers.UserManager
import me.schooltests.mediahost.data.auth.AuthType
import me.schooltests.mediahost.data.auth.User
import me.schooltests.mediahost.gson
import me.schooltests.mediahost.managers.OTPManager
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

@OptIn(InternalAPI::class, io.ktor.server.engine.EngineAPI::class)
fun PipelineContext<*, ApplicationCall>.getOriginAddress(): String {
    val f = context::class.memberProperties.find { it.name == "call" }

    f?.let {
        // Making it accessible
        it.isAccessible = true
        val w = it.getter.call(context) as NettyApplicationCall

        // Getting the remote address
        var ip = w.request.context.pipeline().channel().remoteAddress().toString()
            .replace("/", "")
        ip = ip.substring(0, ip.indexOf(":"))

        if (ip == ApplicationSettings.serverPublicIp || ip == "127.0.0.1" || ip eqIc "localhost") {
            return call.request.header("X-Real-IP") ?: ip
        }

        return ip
    }

    return "0.0.0.0"
}

@Deprecated("Replaced by APIRights#allowed(PipelineContext<*, ApplicationCall>)")
fun PipelineContext<Unit, ApplicationCall>.getAuthenticatedUser(allowed: Set<AuthType>, requireOTP: Boolean = false): User? {
    val errors: MutableList<String> = mutableListOf()
    fun calculateAuthString(type: AuthType, string: String): User? {
        val auth = string.replaceFirst("${type.name} ", "").trim()
        when (type) {
            AuthType.BASIC -> {
                val username = auth.split(" ")[0]

                val user = UserManager.queryUserByUsername(username)
                if (user == null) {
                    errors.add("The specified user does not exist")
                    return null
                }

                if (user.hashedPassword != hash(auth.split(" ")[1] + user.salt)) return null

                return user
            }
            AuthType.SESSION -> {
                val u = UserManager.queryUserBySessionToken(auth)
                if (u == null) {
                    errors.add("The specified session token is invalid")
                    return null
                }

                val session = UserManager.querySessions { it.token == auth }.first()
                if (session.ip != getOriginAddress()) {
                    errors.add("The specified session token does not map to the origin address")
                    return null
                }

                return u
            }
            AuthType.API -> {
                val u = UserManager.queryUserByAPIKey(auth)
                if (u == null) {
                    errors.add("The specified api key is invalid")
                }

                return u
            }
            else -> return null
        }
    }

    fun findHeaders(type: AuthType): User? {
        val authHeader = context.request.header("Client-Auth") ?: return@findHeaders null
        val otpHeader = context.request.header("Client-OTP")
        if (requireOTP && (otpHeader == null || otpHeader.isBlank())) {
            return null
        }

        if (authHeader.startsWith("${type.name} ")) {
            val user = calculateAuthString(type, authHeader)
            if (user != null && requireOTP) {
                val otpSecret = user.otpSecret
                val otp = OTPManager.generateOTP(otpSecret)
                if (otp != otpHeader) {
                    return null
                }
            }

            return user
        }

        return null
    }

    fun generateError(errors: List<String>): JsonObject {
        val obj = JsonObject()
        obj.addProperty("error_code", HttpStatusCode.BadRequest.value)

        val arr = JsonArray()
        for (error in errors) {
            arr.add(error)
        }

        obj.add("errors", arr)
        return obj
    }

    try {
        for (type in allowed) {
            val headers = findHeaders(type)
            if (headers != null) {
                return headers
            }
        }
    } catch (e: IndexOutOfBoundsException) {
        errors.add("Improper Client-OTP or Client-Auth-Header strings.")
        return null
    }

    return null
}

suspend fun PipelineContext<*, ApplicationCall>.failedRequest(reason: String, status: HttpStatusCode) {
    val json = JsonObject()
    json.addProperty("error", reason)
    json.addProperty("state", status.value)

    call.respondText(gson.toJson(json), status = status)
}