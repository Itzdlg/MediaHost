package me.schooltests.mediahost.data.auth

import me.schooltests.mediahost.data.content.MediaContent
import me.schooltests.mediahost.sql.UserTable
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

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
        fun from(id: Int): User? {
            return transaction {
                val results = UserTable.select { UserTable.userId eq id }
                if (results.empty()) {
                    return@transaction null
                }

                val result = results.first()
                return@transaction User(
                    result[UserTable.userId],
                    result[UserTable.username],
                    result[UserTable.hashedPassword],
                    result[UserTable.salt],
                    result[UserTable.otpSecretKey],
                    Date(result[UserTable.dateCreated]),
                    result[UserTable.fileUploadLimit],
                    result[UserTable.totalUploadLimit]
                )
            }
        }
    }

    val bytesUploaded: Long
    get() {
        val contentOwned = MediaContent.queryUser(userId)
        var bytes = 0L
        for (content in contentOwned) {
            bytes += content.getContentSize()
        }

        return bytes
    }
}