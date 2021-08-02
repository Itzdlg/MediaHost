package me.schooltests.mediahost.data.auth

import com.google.gson.JsonObject
import io.ktor.http.HttpStatusCode
import me.schooltests.mediahost.ApplicationSettings
import java.util.EnumSet

sealed class ClientAuthentication(open val user: User?) {
    val isAdmin: Boolean
    get() {
        if (this is AuthSuccessAPI) {
            return ApplicationSettings.adminKeys.contains(this.apiToken)
        }

        return false
    }
}

data class AuthSuccessBasic(override val user: User) : ClientAuthentication(user)
data class AuthSuccessSession(override val user: User, val jwt: JsonObject) : ClientAuthentication(user)
data class AuthSuccessAPI(override val user: User, val apiToken: String, val apiRights: EnumSet<APIRights>) : ClientAuthentication(user)
data class AuthFailure(val error: String, val statusCode: HttpStatusCode) : ClientAuthentication(null)