package me.schooltests.mediahost.sql

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

object MediaPropertiesTable : Table("media_properties") {
    val contentId: Column<String> = varchar("content_id", 7)
    val userId: Column<Int> = integer("user_id")

    val privacy: Column<Int> = integer("privacy")
    val dateCreated: Column<Long> = long("date_created")

    val fileName: Column<String> = varchar("file_name", 260)

    override val primaryKey = PrimaryKey(contentId)
}