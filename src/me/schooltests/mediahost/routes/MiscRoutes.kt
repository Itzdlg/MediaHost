package me.schooltests.mediahost.routes

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.ktor.application.call
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.get
import me.schooltests.mediahost.ApplicationSettings
import me.schooltests.mediahost.data.auth.APIRights
import me.schooltests.mediahost.util.getFormattedDateTime
import java.util.Date

fun Route.miscRoutes() {
    // Tells the client what permissions the server accepts for API keys
    get("/api/info/apikey_permissions") {
        val json = JsonObject()
        val allPermissions = JsonObject()
        val requiresOTP = JsonArray()

        for (enum in APIRights.values()) {
            allPermissions.addProperty(enum.name, enum.descriptor)
            if (enum.requireOTP) {
                requiresOTP.add(enum.name)
            }
        }

        json.add("allPermissions", allPermissions)
        json.add("requiresOTP", requiresOTP)

        call.respondText(me.schooltests.mediahost.gson.toJson(json))
    }

    // Allows clients to convert a Unix Epoch to UTC, meant for manual API access
    get("/api/info/convert_unix/{unix}") {
        val date = if (call.parameters["unix"] == null) Date() else Date(call.parameters["unix"]!!.toLong())
        return@get call.respondText(getFormattedDateTime(date))
    }

    // Tells the client if the server requires a password to signup
    get("/api/info/require_signup_auth") {
        return@get call.respondText(ApplicationSettings.requireSignupPasswd.toString())
    }

    // Tells the client if the server allows unauthorized uploads
    get("/api/info/allow_unauthorized_uploads") {
        return@get call.respondText(ApplicationSettings.allowNoUserUpload.toString())
    }
}