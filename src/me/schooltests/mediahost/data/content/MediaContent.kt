package me.schooltests.mediahost.data.content

import me.schooltests.mediahost.sql.MediaPropertiesTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date

data class MediaContent(
    val contentId: String,
    val userId: Int,
    val privacy: PrivacyType,
    val dateCreated: Date,
    val fileName: String
) {
    companion object {
        private fun build(query: ResultRow): MediaContent {
            return MediaContent(
                    contentId = query[MediaPropertiesTable.contentId].toLowerCase(),
                    userId = query[MediaPropertiesTable.userId],
                    privacy = PrivacyType.from(query[MediaPropertiesTable.privacy]),
                    dateCreated = Date(query[MediaPropertiesTable.dateCreated]),
                    fileName = query[MediaPropertiesTable.fileName]
                )
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

    val contentSize: Int
    get() {
        return 0
    }

    val extension = fileName.substringAfterLast('.', "")
}