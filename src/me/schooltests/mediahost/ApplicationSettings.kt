package me.schooltests.mediahost

import me.schooltests.mediahost.util.CharacterRandom
import me.schooltests.mediahost.util.hash
import java.io.File
import java.util.UUID

object ApplicationSettings {
    private val map: MutableMap<String, String> = mutableMapOf()
    init {
        val whitespaceRegex = Regex("""^\s+""")

        val properties = File("properties.conf")
        val lines = properties.readLines()

        var node = ""
        for (l in lines) {
            val line = l.replace(whitespaceRegex, "")

            if (line.startsWith("#")) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                node = line.substring(1, line.length - 1) + "."
                continue
            }

            val key = line.substringBefore(" ").trim()
            val value = line.substringAfter(" ").substringBefore("##").trim()

            map[node + key.toLowerCase()] = value
        }
    }

    val serverPublicIp = map["server_ip"] ?: "127.0.0.1"

    val allowNoUserUpload = map["upload.allow_no_owner"]?.toBoolean() ?: false
    val defaultUser = map["upload.default_user"] ?: "default"

    val requireSignupOrigin = map["signup.require_specific_origin"]?.toBoolean() ?: false
    val requiredSignupOrigins = map["signup.origins"]?.split(", ") ?: listOf()

    val requireSignupPasswd = map["signup.require_signup_password"]?.toBoolean() ?: false
    val requiredSignupPasswd = map["signup.password"] ?: ""

    val usernameRegex = map["signup regex.username.regex"] ?: """^[A-Za-z0-9]{6,16}$"""
    val usernameRegexDescription = map["signup regex.username.description"] ?: "between 6 and 16 alphanumerical characters"
    val passwordRegex = map["signup regex.password.regex"] ?: """^(?=.*([A-Z]){1,})(?=.*[!@#$&*]{1,})(?=.*[0-9]{1,}).{8,128}$"""
    val passwordRegexDescription = map["signup regex.password.description"] ?: "between 8 and 128 characters with <= 1 uppercase, <= 1 special (!@#$%^&*) character, and <= 1 digit"

    val defaultFileUploadLimit = map["upload limits.file_max"]?.toIntOrNull() ?: 5242880
    val defaultUploadLimit = map["upload limits.total_max"]?.toLongOrNull() ?: 2147482548L
    val maxUploadNotChunked = map["upload limits.unchunked_max"]?.toIntOrNull() ?: 15728640

    val apiTokenCharset = map["random.charsets.api_token"] ?: CharacterRandom.LOWERCASE_ALPHABET + CharacterRandom.UPPERCASE_ALPHABET + CharacterRandom.DIGITS + CharacterRandom.SYMBOLS
    val passwordSaltCharset = map["random.charsets.password_salt"] ?: CharacterRandom.LOWERCASE_ALPHABET + CharacterRandom.UPPERCASE_ALPHABET + CharacterRandom.DIGITS + CharacterRandom.SYMBOLS
    val contentIdCharset = map["random.charsets.content_id"] ?: CharacterRandom.LOWERCASE_ALPHABET + CharacterRandom.DIGITS

    val apiTokenLength = map["random.length.api_token"]?.toIntOrNull() ?: 32
    val passwordSaltLength = map["random.length.password_salt"]?.toIntOrNull() ?: 32
    val contentIdLength = map["random.length.content_id"]?.toIntOrNull() ?: 8

    val databaseUrl = map["database.url"] ?: "jdbc:mysql://127.0.0.1:3306/media"
    val databaseUser = map["database.username"] ?: "root"
    val databasePasswd = map["database.password"] ?: ""
    val databaseDriver = map["database.driver"] ?: "com.mysql.cj.jdbc.Driver"

    val jwtSecret = map["jwt.secret_key"] ?: hash(UUID.randomUUID().toString())
    val jwtExpiration = map["jwt.expiration"]?.toLongOrNull() ?: 900
    val jwtRefreshExpiration = map["jwt.refresh.expiration"]?.toLongOrNull() ?: 86400
    val jwtNamespace = map["jwt.namespace"] ?: "http://localhost/"


    val adminKeys: List<String>
        get() {
            val result = mutableListOf<String>()
            for (key in map.keys) {
                if (!key.startsWith("admin keys.")) continue
                result.add(map[key]!!)
            }

            return result
        }
}