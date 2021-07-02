package me.schooltests.mediahost.routes.file

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveText
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import me.schooltests.mediahost.data.auth.APIRights
import me.schooltests.mediahost.data.auth.AuthFailure
import me.schooltests.mediahost.data.auth.User
import me.schooltests.mediahost.data.content.MediaContent
import me.schooltests.mediahost.gson
import me.schooltests.mediahost.sql.MediaPropertiesTable
import me.schooltests.mediahost.util.failedRequest
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.searchRoutes() {
    route("/api/file") {
        get("/search") {
            val clientAuth = APIRights.QUERY_CONTENT.allowed(this)
            if (clientAuth is AuthFailure)
                return@get failedRequest(clientAuth.error, clientAuth.statusCode)

            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 50).coerceAtMost(500)
            val fileName = (call.request.queryParameters["name"] ?: "")

            val data: List<ResultRow> = transaction {
                    MediaPropertiesTable.select {
                        MediaPropertiesTable.fileName like "%${fileName}%"
                    }.limit(limit)
                }.toList().sortedByDescending { it[MediaPropertiesTable.dateCreated] }

            if (data.isEmpty())
                return@get call.respondText("[]")

            val contentSeen = mutableListOf<String>()

            val result = JsonArray()
            for (res in data) {
                val id = res[MediaPropertiesTable.contentId]
                if (contentSeen.contains(id)) continue

                val content = MediaContent.query(id) ?: continue
                result.add(generateFileJSON(content))
            }

            return@get call.respondText(
                gson.toJson(result)
            )
        }

        get("/info/{fileId}") {
            val targetFileId = call.parameters["fileId"]?.toLowerCase()
                ?: return@get failedRequest(
                    "Unspecified file ID",
                    HttpStatusCode.BadRequest
                )

            val targetFile = MediaContent.query(targetFileId)
                ?: return@get failedRequest(
                    "The specified file ID is invalid",
                    HttpStatusCode.NotFound
                )

            val result = generateFileJSON(targetFile)
            return@get call.respondText(gson.toJson(result))
        }
    }
}

private val usernames = mutableMapOf<Int, String>()
private fun generateFileJSON(content: MediaContent): JsonObject {
    val result = JsonObject()

    val ownerObj = JsonObject()
    val userId = content.userId
    if (usernames.containsKey(userId)) {
        ownerObj.addProperty("id", userId)
        ownerObj.addProperty("name", usernames[userId])
    } else {
        val user = User.from(userId)!!
        ownerObj.addProperty("id", userId)
        ownerObj.addProperty("name", user.username)

        usernames[userId] = user.username
    }

    result.add("owner", ownerObj)

    val fileObj = JsonObject()
    fileObj.addProperty("id", content.contentId)
    fileObj.addProperty("name", content.fileName)
    fileObj.addProperty("created", content.dateCreated.time)

    fileObj.addProperty("size", content.getContentSize())
    fileObj.addProperty("privacy", content.privacy.displayName)
    fileObj.addProperty("extension", content.extension)
    result.add("file", fileObj)

    return result
}