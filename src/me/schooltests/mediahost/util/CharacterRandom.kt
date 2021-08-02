package me.schooltests.mediahost.util

import java.security.SecureRandom

object CharacterRandom {
    const val LOWERCASE_ALPHABET = "abcdefghijklmnopqrstuvwxyz"
    const val UPPERCASE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    const val DIGITS = "0123456789"
    const val SYMBOLS = "!@#$%^&*()-_=+[]{}:;,.<>/?~`\\"

    private val secureRandom = SecureRandom()

    fun random(size: Int, charset: String): String {
        if (size < 1) throw IllegalArgumentException("The size must be greater than 1")
        val builder = StringBuilder()

        val chars = charset.toCharArray()
        for (i in 1..size) {
            val char = chars[secureRandom.nextInt(chars.size)]
            builder.append(char)
        }

        return builder.toString()
    }
}