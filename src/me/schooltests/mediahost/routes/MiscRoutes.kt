package me.schooltests.mediahost.routes

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.ktor.application.call
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import me.schooltests.mediahost.ApplicationSettings
import me.schooltests.mediahost.data.auth.APIRights
import me.schooltests.mediahost.data.content.asJson

fun Route.miscRoutes() {
    route("/api/info") {
        get("/") {
            val obj = JsonObject()

            // [config] Permissions
            val permissionsArr = JsonArray()

            for (enum in APIRights.values()) {
                val enumObj = JsonObject()
                enumObj.addProperty("id", enum.permId)
                enumObj.addProperty("name", enum.name)
                enumObj.addProperty("display", enum.name.replace("_", " ").capitalize())
                enumObj.addProperty("description", enum.description)
                permissionsArr.add(enumObj)
            }

            // [config] Signup Endpoint Protection
            val signupProtectionObj = JsonObject()
            signupProtectionObj.addProperty("from_origin", ApplicationSettings.requireSignupOrigin)
            signupProtectionObj.addProperty("has_password", ApplicationSettings.requireSignupPasswd)

            // [config] Signup Regex
            val signupRegexObj = JsonObject()
            signupRegexObj.addProperty("username", ApplicationSettings.usernameRegexDescription)
            signupRegexObj.addProperty("password", ApplicationSettings.passwordRegexDescription)

            // [config] Default User
            val defaultUserObj = JsonObject()
            defaultUserObj.addProperty("username", ApplicationSettings.defaultUser)
            defaultUserObj.addProperty("allowed", ApplicationSettings.allowNoUserUpload)

            // Add property objects
            obj.add("permissions", permissionsArr)
            obj.add("signup_protection", signupProtectionObj)
            obj.add("signup_requirements", signupRegexObj)
            obj.add("default_user", defaultUserObj)

            return@get call.respondText(obj.asJson)
        }
    }
}