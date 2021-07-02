package me.schooltests.mediahost.sql

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

object UserTable : Table("users") {
    val userId: Column<Int> = integer("user_id").autoIncrement()

    val username: Column<String> = varchar("username", 32)
    val hashedPassword: Column<String> = varchar("password", 256)
    val salt: Column<String> = varchar("salt", 32)
    val otpSecretKey: Column<String> = varchar("otp_secret", 256)

    val dateCreated: Column<Long> = long("date_created")

    val fileUploadLimit: Column<Int> = integer("max_file_upload")
    val totalUploadLimit: Column<Long> = long("max_upload")

    override val primaryKey = PrimaryKey(userId)
}