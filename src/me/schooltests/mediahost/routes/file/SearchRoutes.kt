package me.schooltests.mediahost.routes.file

import com.google.gson.JsonArray
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import me.schooltests.mediahost.data.auth.APIRights
import me.schooltests.mediahost.data.auth.AuthFailure
import me.schooltests.mediahost.data.content.MediaContent
import me.schooltests.mediahost.data.content.asJson
import me.schooltests.mediahost.data.content.asJsonObject
import me.schooltests.mediahost.sql.MediaPropertiesTable
import me.schooltests.mediahost.util.failedRequest
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.searchRoutes() {
    route("/api/file") {
        get("/search") {
            val clientAuth = APIRights.QUERY_CONTENT.allowed(this)
            if (clientAuth is AuthFailure)
                return@get failedRequest(clientAuth.error, clientAuth.statusCode)

            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 50).coerceAtMost(500)
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
            val fileName = call.request.queryParameters["name"] ?: ""

            val data: List<ResultRow> = transaction {
                    MediaPropertiesTable.select {
                        MediaPropertiesTable.fileName like "%${fileName}%"
                    }.limit(limit, offset = (page * limit).toLong())
                }.toList().sortedByDescending { it[MediaPropertiesTable.dateCreated] }

            if (data.isEmpty())
                return@get call.respondText("[]")

            val contentSeen = mutableListOf<String>()

            val result = JsonArray()
            for (res in data) {
                val id = res[MediaPropertiesTable.contentId]
                if (contentSeen.contains(id)) continue

                val content = MediaContent.query(id) ?: continue
                result.add(content.asJsonObject)
            }

            return@get call.respondText(
                result.asJson
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

            return@get call.respondText(targetFile.asJsonObject.asJson)
        }
    }
}