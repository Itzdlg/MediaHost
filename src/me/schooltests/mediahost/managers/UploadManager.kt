package me.schooltests.mediahost.managers

import me.schooltests.mediahost.data.auth.User
import me.schooltests.mediahost.data.content.UploadStream
import me.schooltests.mediahost.timer
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID
import kotlin.concurrent.schedule

object UploadManager {
    private val currentUploads: MutableMap<UUID, UploadStream> = mutableMapOf()

    fun newStream(user: User, contentId: String, totalSize: Int): UUID {
        val stream = UploadStream(user, contentId, totalSize)
        val id = UUID.randomUUID()

        currentUploads[id] = stream

        timer.schedule(Date.from(Instant.now().plus(1, ChronoUnit.HOURS))) {
            expireStream(id)
        }

        return id
    }

    fun getStream(id: UUID): UploadStream? = currentUploads[id]

    fun expireStream(id: UUID) {
        val stream = getStream(id)
        if (stream != null) {
            if (!stream.isFinished) stream.clear(0)
            else stream.pushBuffer()
        }

        currentUploads.remove(id)
    }
}