package me.schooltests.mediahost.routes.file

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveText
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.delete
import io.ktor.routing.get
import io.ktor.routing.options
import io.ktor.routing.route
import me.schooltests.mediahost.data.auth.APIRights
import me.schooltests.mediahost.data.auth.AuthFailure
import me.schooltests.mediahost.data.content.MediaContent
import me.schooltests.mediahost.data.content.PrivacyType
import me.schooltests.mediahost.data.auth.User
import me.schooltests.mediahost.gson
import me.schooltests.mediahost.sql.MediaContentTable
import me.schooltests.mediahost.sql.MediaPropertiesTable
import me.schooltests.mediahost.util.failedRequest
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

fun Route.fileRoutes() {
    route("/api/file") {
        options("/options/{fileId}") {
            val clientAuth = APIRights.MODIFY_FILE_OPTIONS.allowed(this)
            if (clientAuth is AuthFailure)
                return@options failedRequest(clientAuth.error, clientAuth.statusCode)
            val user = clientAuth.user!!
            val targetFileId = call.parameters["fileId"]?.toLowerCase()
                ?: return@options failedRequest(
                "Unspecified file ID",
                HttpStatusCode.BadRequest
            )

            val targetFile = MediaContent.query(targetFileId)
                ?: return@options failedRequest(
                    "The specified file ID is invalid",
                    HttpStatusCode.NotFound
                )

            if (targetFile.userId != user.userId) return@options failedRequest(
                "You do not own the specified file.",
                HttpStatusCode.Forbidden
            )

            val body = JsonParser.parseString(call.receiveText()).asJsonObject
            val newPrivacy = if (body.has("privacyType")) body["privacyType"].asString else null
            val newName = if (body.has("fileName")) body["fileName"].asString else null

            if (newPrivacy != null) {
                transaction { MediaPropertiesTable.update({ MediaPropertiesTable.contentId eq targetFileId }) {
                    it[MediaPropertiesTable.privacy] = PrivacyType.from(newPrivacy).id
                }}
            }

            if (newName != null) {
                transaction { MediaPropertiesTable.update({ MediaPropertiesTable.contentId eq targetFileId }) {
                    it[MediaPropertiesTable.fileName] = newName
                }}
            }
        }

        delete("/delete/{fileId}") {
            val clientAuth = APIRights.DELETE_FILE.allowed(this)
            if (clientAuth is AuthFailure)
                return@delete failedRequest(clientAuth.error, clientAuth.statusCode)
            val user = clientAuth.user!!

            val targetFileId = call.parameters["fileId"]?.toLowerCase()
                ?: return@delete failedRequest(
                    "Unspecified file ID",
                    HttpStatusCode.BadRequest
                )

            val targetFile = MediaContent.query(targetFileId)
                ?: return@delete failedRequest(
                    "The specified file ID is invalid",
                    HttpStatusCode.NotFound
                )

            if (targetFile.userId != user.userId) return@delete failedRequest(
                "You do not own the specified file.",
                HttpStatusCode.Forbidden
            )

            transaction {
                MediaPropertiesTable.deleteWhere { MediaPropertiesTable.contentId eq targetFileId }
                MediaContentTable.deleteWhere { MediaContentTable.contentId eq targetFileId }
            }

            return@delete call.response.status(HttpStatusCode.NoContent)
        }
    }
}