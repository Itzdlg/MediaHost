package me.schooltests.mediahost.managers

import me.schooltests.mediahost.data.auth.User
import me.schooltests.mediahost.data.auth.UserSession
import me.schooltests.mediahost.sql.UserAPIKeysTable
import me.schooltests.mediahost.sql.UserTable
import me.schooltests.mediahost.timer
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.concurrent.schedule
import me.schooltests.mediahost.util.*

object UserManager {
    private val sessions_byId = mutableMapOf<UUID, UserSession>()
    private val sessions_byToken = mutableMapOf<String, UserSession>()

    fun queryUserByUsername(username: String): User? {
        val userId: Int = transaction {
            val results = UserTable.select { UserTable.username eq username.toLowerCase() }
            if (results.empty()) {
                return@transaction -1
            }

            val result = results.first()
            return@transaction result[UserTable.userId]
        }

        if (userId < 0) return null
        return User.from(userId)
    }

    fun queryUserByAPIKey(apiKey: String): User? {
        val userId: Int = transaction {
            val results = UserAPIKeysTable.select { UserAPIKeysTable.hashedToken eq hash(apiKey) }
            if (results.empty()) {
                return@transaction -1
            }

            val result = results.first()
            return@transaction result[UserAPIKeysTable.userId]
        }

        if (userId < 0) return null
        return User.from(userId)
    }

    fun queryUserBySessionToken(sessionToken: String): User? = sessions_byToken[sessionToken]?.let { User.from(it.userId) }

    fun insertSession(session: UserSession) {
        sessions_byId[session.sessionId] = session
        sessions_byToken[session.token] = session

        timer.schedule(session.expires) {
            invalidateSession(session.sessionId)
        }
    }

    fun invalidateSession(sessionId: UUID) {
        val session = sessions_byId[sessionId] ?: return

        sessions_byToken.remove(session.token)
        sessions_byId.remove(sessionId)
    }

    fun querySessions(predicate: (UserSession) -> Boolean): List<UserSession> {
        return sessions_byId.values.filter { predicate.invoke(it) }
    }

    fun meetsUsernameRequirements(username: String): Boolean {
        return username.length in 5..16 // Ensure the length is between 5-16 inclusive
                && username.matches(Regex("""[a-zA-Z0-9]*""")) // Ensure that string is only a-z 0-9
    }

    fun meetsPasswordRequirements(password: String): Boolean {
        return password.length in 8..48 // Ensure the length is between 8-48 inclusive
                && password.matches(Regex("^[A-Za-z0-9_@&.\\-$]*\$"))
    }
}