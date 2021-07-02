package me.schooltests.mediahost.sql

import me.schooltests.mediahost.sql.columns.MediumBlobColumnType
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.statements.api.ExposedBlob

object MediaContentTable : Table("media_content") {
    val contentId: Column<String> = varchar("content_id", 7)
    val compressed: Column<Boolean> = bool("compressed")
    val totalSize: Column<Int> = integer("total_size") // The UNCOMPRESSED size

    val content: Column<ExposedBlob> = registerColumn("content", MediumBlobColumnType())
    val index: Column<Int> = integer("index")

    override val primaryKey = PrimaryKey(contentId)
}