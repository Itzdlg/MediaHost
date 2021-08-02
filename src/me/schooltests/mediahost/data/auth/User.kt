package me.schooltests.mediahost.data.auth

import me.schooltests.mediahost.data.content.MediaContent
import me.schooltests.mediahost.sql.UserTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date

data class User(
    val userId: Int,
    val username: String,
    val hashedPassword: String,
    val salt: String,
    val otpSecret: String,
    val dateCreated: Date,
    val maxFileUpload: Int,
    val maxUpload: Long
) {
    companion object {
        private fun build(row: ResultRow): User {
            return User(
                row[UserTable.userId],
                row[UserTable.username],
                row[UserTable.hashedPassword],
                row[UserTable.salt],
                row[UserTable.otpSecretKey],
                Date(row[UserTable.dateCreated]),
                row[UserTable.fileUploadLimit],
                row[UserTable.totalUploadLimit]
            )
        }

        fun from(id: Int): User? {
            return transaction {
                val results = UserTable.select { UserTable.userId eq id }
                if (results.empty()) {
                    return@transaction null
                }

                val result = results.first()
                return@transaction build(result)
            }
        }

        fun from(username: String): User? {
            return transaction {
                val results = UserTable.select { UserTable.username eq username }
                if (results.empty()) {
                    return@transaction null
                }

                val result = results.first()
                return@transaction build(result)
            }
        }
    }

    val bytesUploaded: Long
    get() {
        val contentOwned = MediaContent.queryUser(userId)
        var bytes = 0L
        for (content in contentOwned) {
            bytes += content.contentSize
        }

        return bytes
    }
}