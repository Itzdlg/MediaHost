package me.schooltests.mediahost.data.auth

import me.schooltests.mediahost.util.randomAlphanumerical
import java.util.*

data class UserSession(
    val userId: Int,
    val ip: String,
    val created: Date,
    val expires: Date,
    val token: String = randomAlphanumerical(32)
) {
    val sessionId = UUID.randomUUID()
}
