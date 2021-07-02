package me.schooltests.mediahost.routes

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import me.schooltests.mediahost.ApplicationSettings
import me.schooltests.mediahost.data.auth.APIRights
import me.schooltests.mediahost.data.auth.APIRights.Companion.toBitMap
import me.schooltests.mediahost.data.auth.AuthFailure
import me.schooltests.mediahost.data.auth.AuthSuccessSession
import me.schooltests.mediahost.managers.UserManager
import me.schooltests.mediahost.data.auth.User
import me.schooltests.mediahost.data.auth.UserSession
import me.schooltests.mediahost.gson
import me.schooltests.mediahost.managers.OTPManager
import me.schooltests.mediahost.sql.UserAPIKeysTable
import me.schooltests.mediahost.sql.UserTable
import me.schooltests.mediahost.util.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

fun Route.userRoutes() {
    route("/api/user") {
        post("/create") {
            if (ApplicationSettings.requireSignupOrigin) {
                val origin = getOriginAddress()
                if (!ApplicationSettings.requiredSignupOrigins.contains(origin))
                    return@post failedRequest(
                        "Request must originate from a specific origin to proceed",
                        HttpStatusCode.Unauthorized
                    )
            }

            val json = JsonParser.parseString(call.receiveText()).asJsonObject
            if (ApplicationSettings.requireSignupPasswd) {
                val password = json["__signupAuthorization"].asString
                if (password == null || ApplicationSettings.requiredSignupPasswd != password)
                    return@post failedRequest(
                        "Request must contain a signup authorization (password dictated by the server) to proceed",
                        HttpStatusCode.Unauthorized
                    )
            }

            val username = json["username"].asString.toLowerCase()
            val password = json["password"].asString

            if (UserManager.queryUserByUsername(username) != null)
                return@post failedRequest(
                    "The specified username already exists as a user",
                    HttpStatusCode.Conflict
                )

            if (!UserManager.meetsUsernameRequirements(username) || UserManager.meetsPasswordRequirements(password))
                return@post failedRequest(
                    "The specified username or password does not meet the requirements.",
                    HttpStatusCode.BadRequest
                )

            val salt = randomAlphanumerical(32)
            val otpKey = OTPManager.generateSecretKey()
            val hashedSaltedPassword = hash(password + salt)

            transaction {
                UserTable.insert {
                    it[UserTable.username] = username
                    it[UserTable.hashedPassword] = hashedSaltedPassword
                    it[UserTable.salt] = salt

                    it[UserTable.otpSecretKey] = otpKey
                    it[UserTable.dateCreated] = Date().time
                    it[UserTable.fileUploadLimit] = ApplicationSettings.defaultFileUploadLimit
                    it[UserTable.totalUploadLimit] = ApplicationSettings.defaultUploadLimit
                }
            }

            val obj = JsonObject()
            obj.addProperty("otp_secret", otpKey)

            call.respondText(gson.toJson(obj))
        }

        fun userObj(user: User): JsonObject {
            val ownerObj = JsonObject()
            ownerObj.addProperty("id", user.userId)
            ownerObj.addProperty("name", user.username)
            ownerObj.addProperty("created", user.dateCreated.time)
            ownerObj.addProperty("uploaded", user.bytesUploaded)
            ownerObj.addProperty("max_file_upload", user.maxFileUpload)
            ownerObj.addProperty("max_total_upload", user.maxUpload)
            return ownerObj
        }

        get("/info/id/{userId}") {
            val userId = call.parameters["userId"]?.toIntOrNull()
                ?: return@get failedRequest(
                    "No specified user ID",
                    HttpStatusCode.BadRequest
                )
            val user = User.from(userId)
                ?: return@get failedRequest(
                    "The specified user ID is invalid",
                    HttpStatusCode.NotFound
                )

            return@get call.respondText(gson.toJson(userObj(user)))
        }

        get("/info/username/{username}") {
            val username = call.parameters["username"]
                ?: return@get failedRequest(
                    "No specified username",
                    HttpStatusCode.BadRequest
                )
            val user = UserManager.queryUserByUsername(username)
                ?: return@get failedRequest(
                    "The specified username is invalid",
                    HttpStatusCode.NotFound
                )

            return@get call.respondText(gson.toJson(userObj(user)))
        }
    }

    route("/api/user/apikeys") {
        get("/list") {
            val clientAuth = APIRights.LIST_API_KEYS.allowed(this)
            if (clientAuth is AuthFailure)
                return@get failedRequest(clientAuth.error, clientAuth.statusCode)
            val user = clientAuth.user!!

            val keys = transaction {
                return@transaction UserAPIKeysTable.select { UserAPIKeysTable.userId eq user.userId }.toList()
            }

            val json = JsonArray()
            for (key in keys) {
                val obj = JsonObject()
                obj.addProperty("id", key[UserAPIKeysTable.apiKeyId].toString())
                obj.addProperty("generated_at", getFormattedDateTime(Date(key[UserAPIKeysTable.dateCreated])))
                obj.addProperty("description", key[UserAPIKeysTable.description])

                json.add(obj)
            }

            call.respondText(gson.toJson(json))
        }

        post("/generate") {
            val clientAuth = APIRights.GENERATE_API_KEY.allowed(this)
            if (clientAuth is AuthFailure)
                return@post failedRequest(clientAuth.error, clientAuth.statusCode)
            val user = clientAuth.user!!

            val json = JsonParser.parseString(call.receiveText()).asJsonObject
            val permissions = if (json.has("permissions")) json["permissions"].asJsonArray else JsonArray()

            val permSet = EnumSet.noneOf(APIRights::class.java)
            for (p in permissions) {
                if (p.isJsonPrimitive) {
                    permSet.add(APIRights.getById(p.asInt))
                } else {
                    permSet.add(APIRights.valueOf(p.asString))
                }
            }

            val token = randomAlphanumerical(48)
            val id = UUID.randomUUID()
            transaction {
                UserAPIKeysTable.insert {
                    it[UserAPIKeysTable.userId] = user.userId
                    it[UserAPIKeysTable.dateCreated] = Date().time
                    it[UserAPIKeysTable.description] = json["description"].asString
                    it[UserAPIKeysTable.apiKeyId] = id
                    it[UserAPIKeysTable.hashedToken] = hash(token)
                    it[UserAPIKeysTable.permissions] = permSet.toBitMap()
                }
            }

            val obj = JsonObject()
            obj.addProperty("id", id.toString())
            obj.addProperty("token", token)

            call.respondText(gson.toJson(obj))
        }
    }

    route("/api/user/sessions") {
        get("/list") {
            val clientAuth = APIRights.LIST_SESSIONS.allowed(this)
            if (clientAuth is AuthFailure)
                return@get failedRequest(clientAuth.error, clientAuth.statusCode)
            val user = clientAuth.user!!

            val sessions = UserManager.querySessions { it.userId == user.userId }
            val arr = JsonArray()

            for (session in sessions) {
                val obj = JsonObject()
                obj.addProperty("id", session.sessionId.toString())
                obj.addProperty("ip", session.ip)
                obj.addProperty("generated_at", getFormattedDateTime(session.created))
                obj.addProperty("expires_at", getFormattedDateTime(session.expires))
                arr.add(obj)
            }

            call.respondText(gson.toJson(arr))
        }

        post("/expire") {
            val clientAuth = APIRights.EXPIRE_SESSION.allowed(this)
            if (clientAuth is AuthFailure)
                return@post failedRequest(clientAuth.error, clientAuth.statusCode)

            val json = JsonParser.parseString(call.receiveText()).asJsonObject
            val sessionId = json["id"].asString
            if (sessionId eqIc "self") {
                if (clientAuth !is AuthSuccessSession) return@post failedRequest(
                    "To expire your own session, you must authenticate with a session token.",
                    HttpStatusCode.BadRequest
                )

                UserManager.invalidateSession(clientAuth.session.sessionId)
                return@post call.response.status(HttpStatusCode.OK)
            }

            val session = UserManager.querySessions { it.sessionId.toString() == sessionId }.firstOrNull()
                ?: return@post failedRequest("The provided session ID is invalid.", HttpStatusCode.BadRequest)

            UserManager.invalidateSession(session.sessionId)
            call.response.status(HttpStatusCode.OK)
        }

        post("/generate") {
            val clientAuth = APIRights.GENERATE_SESSION.allowed(this)
            if (clientAuth is AuthFailure)
                return@post failedRequest(clientAuth.error, clientAuth.statusCode)
            val user = clientAuth.user!!

            val session = UserSession(
                userId = user.userId,
                ip = getOriginAddress(),
                created = Date(),
                expires = Date.from(Instant.now().plus(3L, ChronoUnit.HOURS))
            )

            UserManager.insertSession(session)

            val obj = JsonObject()
            obj.addProperty("id", session.sessionId.toString())
            obj.addProperty("token", session.token)

            call.respondText(gson.toJson(obj))
        }
    }
}