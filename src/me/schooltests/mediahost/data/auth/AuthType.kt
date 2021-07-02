package me.schooltests.mediahost.data.auth

import me.schooltests.mediahost.sql.UserAPIKeysTable
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.EnumSet
import me.schooltests.mediahost.util.hash

enum class AuthType {
    BASIC, SESSION, API;

    fun getRights(data: String): EnumSet<APIRights> {
        when (this) {
            BASIC -> return EnumSet.of(
                APIRights.CHANGE_USERNAME,
                APIRights.RESET_PASSWORD,
                APIRights.GENERATE_API_KEY,
                APIRights.GENERATE_SESSION,
                APIRights.EXPIRE_API_KEY,
                APIRights.EXPIRE_SESSION,
                APIRights.DELETE_ACCOUNT
            )
            SESSION -> return EnumSet.of(
                APIRights.LIST_API_KEYS,
                APIRights.LIST_SESSIONS,
                APIRights.UPLOAD_FILE,
                APIRights.DELETE_FILE,
                APIRights.MODIFY_FILE_OPTIONS,
                APIRights.VIEW_PRIVATE_CONTENT
            )
            API -> {
                val apiRights = transaction {
                    UserAPIKeysTable.select { UserAPIKeysTable.hashedToken eq hash(data) }.firstOrNull()?.get(UserAPIKeysTable.permissions)
                } ?: return EnumSet.noneOf(APIRights::class.java)

                return APIRights.fromBitMap(apiRights)
            }
        }
    }
}