package me.schooltests.mediahost.data.auth

import me.schooltests.mediahost.sql.UserAPIKeysTable
import me.schooltests.mediahost.util.hash
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.EnumSet

enum class AuthType {
    BASIC, SESSION, API;

    fun getRights(data: String): EnumSet<APIRights> {
        when (this) {
            BASIC -> return EnumSet.of(APIRights.GENERATE_SESSION)
            SESSION -> return EnumSet.allOf(APIRights::class.java)
            API -> {
                val apiRights = transaction {
                    UserAPIKeysTable.select { UserAPIKeysTable.hashedToken eq hash(data) }.firstOrNull()?.get(UserAPIKeysTable.permissions)
                } ?: return EnumSet.noneOf(APIRights::class.java)

                return APIRights.fromBitMap(apiRights)
            }
        }
    }
}