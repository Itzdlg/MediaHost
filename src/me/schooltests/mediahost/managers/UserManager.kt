package me.schooltests.mediahost.managers

import me.schooltests.mediahost.ApplicationSettings
import me.schooltests.mediahost.data.auth.User

object UserManager {
    private val cachedUsersById: MutableMap<Int, User> = mutableMapOf()
    private val cachedUsersByName: MutableMap<String, User> = mutableMapOf()

    fun queryUserById(id: Int): User? {
        if (cachedUsersById.containsKey(id)) return cachedUsersById[id]
        return User.from(id)
    }

    fun queryUserByUsername(username: String): User? {
        if (cachedUsersByName.containsKey(username.toLowerCase())) return cachedUsersByName[username.toLowerCase()]
        return User.from(username.toLowerCase())
    }

    private val usernameRegex = Regex(ApplicationSettings.usernameRegex)
    fun meetsUsernameRequirements(username: String): Boolean = usernameRegex.matches(username)

    private val passwordRegex = Regex(ApplicationSettings.passwordRegex)
    fun meetsPasswordRequirements(password: String): Boolean = passwordRegex.matches(password)
}