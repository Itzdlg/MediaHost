package me.schooltests.mediahost.routes.file

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.streamProvider
import io.ktor.request.receiveMultipart
import io.ktor.request.receiveText
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import me.schooltests.mediahost.ApplicationSettings
import me.schooltests.mediahost.data.auth.APIRights
import me.schooltests.mediahost.data.auth.AuthFailure
import me.schooltests.mediahost.data.content.PrivacyType
import me.schooltests.mediahost.gson
import me.schooltests.mediahost.managers.UploadManager
import me.schooltests.mediahost.managers.UserManager
import me.schooltests.mediahost.sql.MediaPropertiesTable
import me.schooltests.mediahost.util.failedRequest
import me.schooltests.mediahost.util.randomAlphanumerical
import org.jetbrains.exposed.sql.insert
import java.util.UUID

fun Route.uploadRoutes() {
    route("/api/file/upload") {
        // Upload a file straight, no chunks
        post("/") {
            var user = UserManager.queryUserByUsername(ApplicationSettings.defaultUser)!!
            val clientAuth = APIRights.UPLOAD_FILE.allowed(this)
            if (clientAuth is AuthFailure && !ApplicationSettings.allowNoUserUpload)
                return@post failedRequest(clientAuth.error, clientAuth.statusCode)
            else if (clientAuth.user != null)
                user = clientAuth.user!!

            val multipart = call.receiveMultipart()
            val filePart = multipart.readPart() as? PartData.FileItem ?: return@post failedRequest(
                "Unspecified multipart.",
                HttpStatusCode.BadRequest
            )

            val buffer = filePart.streamProvider().buffered()
            val maxLeft = if (user.maxUpload - user.bytesUploaded > Integer.MAX_VALUE) Integer.MAX_VALUE else (user.maxUpload - user.bytesUploaded).toInt()
            val maximumUpload = maxLeft.coerceAtMost(user.maxFileUpload)

            val stream = me.schooltests.mediahost.data.content.UploadStream(
                user,
                randomAlphanumerical(7),
                maximumUpload
            )

            for (byte in buffer.iterator()) {
                try {
                    stream.push(byte)
                } catch (ex: IllegalStateException) {
                    stream.clear(0)
                    return@post failedRequest(
                        "You may not exceed your upload limit (${user.bytesUploaded}/${user.maxUpload}) (max ${user.maxFileUpload} per/file)",
                        HttpStatusCode.PayloadTooLarge
                    )
                }
            }

            stream.pushBuffer() // Send the last bits of data into the database as a short row
            val fileName = filePart.originalFileName ?: "${stream.contentId}.unknown"

            MediaPropertiesTable.insert {
                it[userId] = stream.user.userId
                it[contentId] = stream.contentId
                it[MediaPropertiesTable.fileName] = fileName
                it[privacy] = PrivacyType.PUBLIC.id
                it[dateCreated] = java.util.Date().time
            }

            val obj = JsonObject()
            obj.addProperty("contentId", stream.contentId)
            obj.addProperty("fileSize", stream.alreadyUploaded)

            call.respondText(stream.contentId)
        }

        /*
        -------------------------
          CHUNKED UPLOAD API
        -------------------------
        [x] Required when the file is above a certain size
        [x] the totalSize in /begin must be accurate

        In truth, only /begin requires authentication, because that will result in a stream being created
        However, to access the other endpoints, all that is required is the
        stream's Unique Identifier. This may be considered a flaw, but in truth, only the client
        knows the ID unless it shares the ID, and it can't be guessed because there won't be many
        streams at any given time, making the possibilities of UUID's little and far between.
         */
        post("/begin") {
            var user = UserManager.queryUserByUsername(ApplicationSettings.defaultUser)!!
            val clientAuth = APIRights.UPLOAD_FILE.allowed(this)
            if (clientAuth is AuthFailure && !ApplicationSettings.allowNoUserUpload)
                return@post failedRequest(clientAuth.error, clientAuth.statusCode)
            else if (clientAuth.user != null)
                user = clientAuth.user!!

            val json = JsonParser.parseString(call.receiveText()).asJsonObject
            val totalSize = if (json.has("totalSize")) json["totalSize"].asInt else 0

            val exceedLimits = totalSize + user.bytesUploaded > user.maxUpload || totalSize > user.maxFileUpload
            if (exceedLimits)
                return@post failedRequest(
                    "You may not exceed your upload limit (${user.bytesUploaded}/${user.maxUpload}) (max ${user.maxFileUpload} per/file)",
                    HttpStatusCode.PayloadTooLarge
                )

            val contentId = randomAlphanumerical(7)
            return@post call.respondText(UploadManager.newStream(user, contentId, totalSize).toString())
        }

        post("/upstream/{id}") {
            val streamId = UUID.fromString(call.parameters["id"]
                ?: return@post failedRequest(
                    "Unspecified stream ID",
                    HttpStatusCode.BadRequest
                )
            )

            val stream = UploadManager.getStream(streamId) ?: return@post failedRequest(
                "The specified stream ID is invalid",
                HttpStatusCode.ResetContent
            )

            val multipartData = call.receiveMultipart()
            val part = multipartData.readPart()
            if (part is PartData.BinaryItem) {
                val provider = part.provider()
                while (!provider.endOfInput) {
                    val byte = provider.readByte()

                    try {
                        stream.push(byte)
                    } catch (ex: IllegalStateException) {
                        return@post failedRequest(ex.message ?: "Something went wrong.", HttpStatusCode.PayloadTooLarge)
                    }
                }
            }

            return@post call.response.status(HttpStatusCode.Continue)
        }

        post("/finish/{id}") {
            val streamId = UUID.fromString(call.parameters["id"]
                ?: return@post failedRequest(
                    "Unspecified stream ID",
                    HttpStatusCode.BadRequest
                )
            )

            val stream = UploadManager.getStream(streamId) ?: return@post failedRequest(
                "The specified stream ID is invalid",
                HttpStatusCode.ResetContent
            )

            stream.pushBuffer() // Send the last bits of data into the database as a short row

            val json = JsonParser.parseString(call.receiveText()).asJsonObject
            val fileName = if (json.has("fileName")) json["fileName"].asString else "${stream.contentId}.unknown"
            val privacyType = if (json.has("privacyType")) PrivacyType.from(json["privacyType"].asString) else PrivacyType.PUBLIC

            MediaPropertiesTable.insert {
                it[userId] = stream.user.userId
                it[contentId] = stream.contentId
                it[MediaPropertiesTable.fileName] = fileName
                it[privacy] = privacyType.id
                it[dateCreated] = java.util.Date().time
            }

            return@post call.respondText(stream.contentId, status = HttpStatusCode.Created)
        }

        post("/clear/{id}") {
            val streamId = UUID.fromString(call.parameters["id"]
                ?: return@post failedRequest(
                    "Unspecified stream ID",
                    HttpStatusCode.BadRequest
                )
            )

            val stream = UploadManager.getStream(streamId) ?: return@post failedRequest(
                "The specified stream ID is invalid",
                HttpStatusCode.ResetContent
            )

            val since = call.request.queryParameters["since"]?.toIntOrNull()
                ?: return@post failedRequest(
                    "Unspecified since parameter",
                    HttpStatusCode.BadRequest
                )

            stream.clear(since)
            return@post call.response.status(HttpStatusCode.Continue)
        }

        post("/abort/{id}") {
            val streamId = UUID.fromString(call.parameters["id"]
                ?: return@post failedRequest(
                    "Unspecified stream ID",
                    HttpStatusCode.BadRequest
                )
            )

            val stream = UploadManager.getStream(streamId) ?: return@post failedRequest(
                "The specified stream ID is invalid",
                HttpStatusCode.ResetContent
            )

            stream.clear(0)
            UploadManager.expireStream(streamId)

            return@post call.response.status(HttpStatusCode.NotFound)
        }

        get("/info/{id}") {
            val streamId = UUID.fromString(call.parameters["id"]
                ?: return@get failedRequest(
                    "Unspecified stream ID",
                    HttpStatusCode.BadRequest
                )
            )

            val stream = UploadManager.getStream(streamId) ?: return@get failedRequest(
                "The specified stream ID is invalid",
                HttpStatusCode.ResetContent
            )

            val obj = JsonObject()
            obj.addProperty("uploaded", stream.alreadyUploaded)
            obj.addProperty("expecting", stream.totalSize)
            obj.addProperty("finished", stream.isFinished)
            return@get call.respondText(gson.toJson(obj))
        }
    }
}