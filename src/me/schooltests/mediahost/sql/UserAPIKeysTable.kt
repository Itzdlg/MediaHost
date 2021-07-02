package me.schooltests.mediahost.sql

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import java.util.*

object UserAPIKeysTable : Table("user_apikeys") {
    val userId: Column<Int> = integer("user_id")

    val apiKeyId: Column<UUID> = uuid("key_id")
    val description: Column<String> = varchar("description", 128)
    val dateCreated: Column<Long> = long("date_created")

    val hashedToken: Column<String> = varchar("token", 256)
    val permissions: Column<Long> = long("api_permissions")

    override val primaryKey = PrimaryKey(apiKeyId)
}