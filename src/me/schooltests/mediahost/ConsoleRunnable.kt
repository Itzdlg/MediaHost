package me.schooltests.mediahost

import me.schooltests.mediahost.data.auth.User
import me.schooltests.mediahost.data.auth.UserSession
import me.schooltests.mediahost.managers.OTPManager
import me.schooltests.mediahost.managers.UserManager
import me.schooltests.mediahost.sql.UserTable
import me.schooltests.mediahost.util.hash
import me.schooltests.mediahost.util.randomAlphanumerical
import org.jetbrains.exposed.sql.update
import java.lang.NumberFormatException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

/*
    Administrator Commands

    generateSession <userId>
    This command will create a default session that expires after 3 hours for the specified user, granted for any
    IP address, but as an irrevocable session.

    generateOTP <userId>
    This command will generate a OTP from the user's OTP secret

    resetPassword <userId>
    This command will change the user's password to a new random password one.

    temporaryPassword set <userId>
    This command will change the user's password to a random password

    temporaryPassword reset <userId>
    This command will undo the 'temporaryPassword set' changes by resetting the password to the old one
 */

object ConsoleRunnable : Runnable {
    private val temporaryPasswords = mutableMapOf<Int, String>()

    override fun run() {
        val scanner = Scanner(System.`in`)
        while (scanner.hasNextLine()) {
            val line = scanner.nextLine()

            if (!line.contains(" ")) continue
            val args = line.split(" ")
            if (line.startsWith("generateSession")) {
                val userId = try {
                    args[1].toInt()
                } catch (e: NumberFormatException) {
                    continue
                }

                val user = User.from(userId) ?: continue
                val session = UserSession(
                    userId = userId,
                    ip = "*",
                    created = Date(),
                    expires = Date.from(Instant.now().plus(3L, ChronoUnit.HOURS))
                )

                UserManager.insertSession(session)
                println("New session for ${user.username} (${userId}) expiring in 3h: ${session.token}")
            } else if (line.startsWith("generateOTP")) {
                val userId = try {
                    args[1].toInt()
                } catch (e: NumberFormatException) {
                    continue
                }

                val user = User.from(userId) ?: continue
                val otp = OTPManager.generateOTP(user.otpSecret)

                println("One Time Password for ${user.username} (${userId}): $otp")
            } else if (line.startsWith("resetPassword")) {
                val userId = try {
                    args[1].toInt()
                } catch (e: NumberFormatException) {
                    continue
                }

                val user = User.from(userId) ?: continue
                val tmpPassword = hash(randomAlphanumerical(12))
                UserTable.update({ UserTable.userId eq userId }) {
                    it[hashedPassword] = tmpPassword
                }

                println("New password for ${user.username} (${userId}): $tmpPassword")
            } else if (line.startsWith("temporaryPassword set")) {
                val userId = try {
                    args[1].toInt()
                } catch (e: NumberFormatException) {
                    continue
                }

                if (temporaryPasswords.containsKey(userId)) {
                    println("Unable to assign a temporary password to user $userId because they already have one.")
                    continue
                }

                val user = User.from(userId) ?: continue
                val newPassword = hash(randomAlphanumerical(12))
                val oldPassword = user.hashedPassword

                temporaryPasswords[user.userId] = oldPassword

                UserTable.update ({UserTable.userId eq user.userId}) {
                    it[UserTable.hashedPassword] = newPassword
                }
            } else if (line.startsWith("temporaryPassword reset")) {
                val userId = try {
                    args[1].toInt()
                } catch (e: NumberFormatException) {
                    continue
                }

                if (!temporaryPasswords.containsKey(userId)) {
                    println("Unable to reset the users temporary password because they do not have one.")
                    continue
                }

                val user = User.from(userId) ?: continue
                val oldPassword = temporaryPasswords[userId]!!

                UserTable.update ({UserTable.userId eq user.userId}) {
                    it[UserTable.hashedPassword] = oldPassword
                }
            }
        }
    }
}