package me.schooltests.mediahost.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.schooltests.mediahost.BLOB_PER_ROW
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import kotlin.math.ceil

infix fun String?.eqIc(s: String?): Boolean {
    if (s == null && this == null) return true
    if (s == null || this == null) return false

    return this.equals(s, ignoreCase = true)
}

fun getFormattedDateTime(dateObj: Date, timeZone: String = "UTC", includeDate: Boolean = true, includeTime: Boolean = true): String {
    val datePattern = "MMM dd, yyyy"
    val dateFormat = SimpleDateFormat(datePattern)
    dateFormat.timeZone = TimeZone.getTimeZone(timeZone)
    val date = dateFormat.format(dateObj)

    val timePattern = "h:m:s a z"
    val timeFormat = SimpleDateFormat(timePattern)
    timeFormat.timeZone = TimeZone.getTimeZone(timeZone)
    val time = timeFormat.format(dateObj)

    var formatted = ""
    if (includeDate) {
        formatted += "$date "
    }

    if (includeTime) {
        formatted += time
    }

    return formatted.trim()
}

private fun hash(bytes: ByteArray): String {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(bytes)
    return digest.fold("") { str, it -> str + "%02x".format(it) }
}

fun hash(string: String): String = hash(string.toByteArray())

fun numOfSplits(bytesOfContent: Long): Int = ceil(bytesOfContent.toDouble() / BLOB_PER_ROW.toDouble()).toInt()

suspend fun <T> dbQuery(block: () -> T): T =
    withContext(Dispatchers.IO) {
        transaction { block() }
    }