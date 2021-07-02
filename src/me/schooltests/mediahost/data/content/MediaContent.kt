package me.schooltests.mediahost.data.content

import me.schooltests.mediahost.sql.MediaContentTable
import me.schooltests.mediahost.sql.MediaPropertiesTable
import me.schooltests.mediahost.util.CompressionUtil
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.util.*

data class MediaContent(
    val contentId: String,
    val userId: Int,
    val privacy: PrivacyType,
    val dateCreated: Date,
    val fileName: String
) {
    companion object {
        private fun build(query: ResultRow): MediaContent {
            return transaction {
                return@transaction MediaContent(
                    contentId = query[MediaPropertiesTable.contentId].toLowerCase(),
                    userId = query[MediaPropertiesTable.userId],
                    privacy = PrivacyType.from(query[MediaPropertiesTable.privacy]),
                    dateCreated = Date(query[MediaPropertiesTable.dateCreated]),
                    fileName = query[MediaPropertiesTable.fileName]
                )
            }
        }

        fun query(contentId: String): MediaContent? {
            val query = MediaPropertiesTable.select { MediaPropertiesTable.contentId eq contentId.toLowerCase() }
            if (query.empty()) {
                return null
            }

            val media = query.first()
            return build(media)
        }

        fun queryUser(userId: Int): List<MediaContent> {
            return transaction {
                val query = MediaPropertiesTable.select { MediaPropertiesTable.userId eq userId }
                if (query.empty()) {
                    return@transaction listOf()
                }

                val media = mutableListOf<MediaContent>()
                query.forEach { row ->
                    media.add(build(row))
                }

                media.sortWith(Comparator.comparingLong {
                    it.dateCreated.time
                })

                return@transaction media
            }
        }
    }

    fun writeToFile(file: File = File("tmp/${contentId}/${fileName}")): File {
        return File("")
/*        val bytes = getBytes()
        if (bytes.isEmpty()) return file

        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.createNewFile()
        }

        file.writeBytes(bytes)
        return file*/
    }

    private var _bytes: ByteArray? = null
    fun getBytes(): ByteArray {
        return ByteArray(0)
/*        if (_bytes != null) {
            return _bytes ?: ByteArray(0)
        }

        return transaction {
            val query = MediaContentTable.select { MediaContentTable.contentId eq contentId }
            if (query.empty()) {
                return@transaction ByteArray(0)
            }

            val first = query.first()
            var bytes = first[MediaContentTable.contentPiece].bytes
            val compressed = first[MediaContentTable.compressed]

            if (compressed) {
                CompressionUtil.gzipUncompress(bytes)?.let { bytes = it }
            }

            if (first[MediaContentTable.contentSize] <= 5242880) { // Ensure we aren't caching huge blobs into memory
                _bytes = bytes
            }

            return@transaction bytes
        }*/
    }

    private var _contentSize: Int? = null
    fun getContentSize(): Int {
        return 0
/*        if (_contentSize != null) {
            return _contentSize ?: 0
        }

        val size: Int = transaction {
            val query = MediaContentTable.select { MediaContentTable.contentId eq contentId }
            if (query.empty()) {
                return@transaction 0
            }

            return@transaction query.first()[MediaContentTable.contentSize]
        }

        _contentSize = size
        return size*/
    }

    val extension = fileName.substringAfterLast('.', "")

    /*        return transaction {
            val query = MediaContentTable.select { MediaContentTable.contentId eq contentId }
            if (query.empty()) {
                return@transaction ByteArray(0)
            }

            var bytes = ByteArray(query.first()[MediaContentTable.contentSize])

            var i = 0
            for (piece in query.sortedBy { it[MediaContentTable.contentIndex] }) {
                piece[MediaContentTable.contentPiece].bytes.forEach {
                    bytes[i] = it
                    i++
                }
            }

            val compressed = query.first()[MediaContentTable.compressed]
            if (compressed) {
                CompressionUtil.gzipUncompress(bytes)?.let { bytes = it }
            }

            _bytes = bytes
            return@transaction bytes
        }*/
}