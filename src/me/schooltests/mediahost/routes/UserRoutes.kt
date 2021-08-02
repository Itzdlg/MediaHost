package me.schooltests.mediahost.routes

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveText
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import me.schooltests.mediahost.ApplicationSettings
import me.schooltests.mediahost.data.auth.APIRights
import me.schooltests.mediahost.data.auth.APIRights.Companion.toBitMap
import me.schooltests.mediahost.data.auth.AuthFailure
import me.schooltests.mediahost.data.auth.AuthSuccessAPI
import me.schooltests.mediahost.data.auth.AuthSuccessSession
import me.schooltests.mediahost.data.auth.AuthType
import me.schooltests.mediahost.data.content.asJson
import me.schooltests.mediahost.data.content.asJsonObject
import me.schooltests.mediahost.managers.OTPManager
import me.schooltests.mediahost.managers.SessionManager
import me.schooltests.mediahost.managers.UserManager
import me.schooltests.mediahost.sql.UserAPIKeysTable
import me.schooltests.mediahost.sql.UserTable
import me.schooltests.mediahost.util.CharacterRandom
import me.schooltests.mediahost.util.failedRequest
import me.schooltests.mediahost.util.getFormattedDateTime
import me.schooltests.mediahost.util.getOriginAddress
import me.schooltests.mediahost.util.hash
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.Date
import java.util.EnumSet
import java.util.UUID

fun Route.userRoutes() {
    route("/api/user") {
        post("/create") {
            if (ApplicationSettings.requireSignupOrigin) {
                val origin = getOriginAddress()
                if (!ApplicationSettings.requiredSignupOrigins.contains(origin))
                    return@post failedRequest(
                        "Request must originate from a specific origin to proceed",
                        HttpStatusCode.Forbidden
                    )
            }

            val json = JsonParser.parseString(call.receiveText()).asJsonObject
            if (ApplicationSettings.requireSignupPasswd) {
                val password = json["__signupAuthorization"].asString
                if (password == null || ApplicationSettings.requiredSignupPasswd != password)
                    return@post failedRequest(
                        "Request must contain a signup authorization (password dictated by the server) to proceed",
                        HttpStatusCode.Forbidden
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

            val salt = CharacterRandom.random(ApplicationSettings.passwordSaltLength, ApplicationSettings.passwordSaltCharset)
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

            call.respondText(obj.asJson)
        }

        get("/info/id/{userId}") {
            val userId = call.parameters["userId"]?.toIntOrNull()
                ?: return@get failedRequest(
                    "No specified user ID",
                    HttpStatusCode.BadRequest
                )
            val user = UserManager.queryUserById(userId)
                ?: return@get failedRequest(
                    "The specified user ID is invalid",
                    HttpStatusCode.NotFound
                )

            return@get call.respondText(user.asJsonObject.asJson)
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

            return@get call.respondText(user.asJsonObject.asJson)
        }
    }

    route("/api/user/apikeys") {
        get("/") {
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

            call.respondText(json.asJson)
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

            val token = CharacterRandom.random(ApplicationSettings.apiTokenLength, ApplicationSettings.apiTokenCharset)
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

            call.respondText(obj.asJson)
        }
    }

    route("/api/user/sessions") {
        get("/") {
            val clientAuth = APIRights.LIST_SESSIONS.allowed(this)
            if (clientAuth is AuthFailure)
                return@get failedRequest(clientAuth.error, clientAuth.statusCode)
            val user = clientAuth.user!!

            val heldByUser = SessionManager.refreshTokens.filter { token -> token.contains("u" + user.userId) }

        }

        post("/generate") {
            val clientAuth = APIRights.GENERATE_SESSION.allowed(this)
            if (clientAuth is AuthFailure)
                return@post failedRequest(clientAuth.error, clientAuth.statusCode)
            val user = clientAuth.user!!

            // Refresh the current JWT
            if (clientAuth is AuthSuccessSession) {
                val newJwt = SessionManager.refreshJwt(clientAuth.jwt) ?: return@post failedRequest(
                    "The provided JWT refresh token is no longer valid.",
                    HttpStatusCode.Forbidden
                )

                return@post call.respondText(JsonObject().apply {
                    addProperty("jwt", newJwt)
                }.asJson)
            }

            var permissions = AuthType.SESSION.getRights("")
            if (clientAuth is AuthSuccessAPI)
                permissions = clientAuth.apiRights

            // Create a new JWT
            val newJwt = SessionManager.generateJwt(
                user,
                permissions = permissions,
                customClaims = { claim ->
                    val namespace = ApplicationSettings.jwtNamespace
                    val token = "${UUID.randomUUID()}@u${user.userId}:${getOriginAddress()}"
                    val before = Instant.now().plusSeconds(ApplicationSettings.jwtRefreshExpiration)

                    claim.addProperty(namespace + "refresh/token", token)
                    claim.addProperty(namespace + "refresh/before", before.epochSecond)

                    SessionManager.validateRefreshToken(token)
                }
            )

            return@post call.respondText(JsonObject().apply {
                addProperty("jwt", newJwt)
            }.asJson)
        }
    }
}