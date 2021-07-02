package me.schooltests.mediahost

import me.schooltests.mediahost.util.eqIc
import java.io.File

object ApplicationSettings {
    private val map: MutableMap<String, String> = mutableMapOf()
    init {
        println("applicationSettings : INIT")
        val properties = File("properties.conf")
        val lines = properties.readLines()

        var node = ""
        for (line in lines) {
            if (line.startsWith("#")) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                node = line.substring(1, line.length - 1) + "."
                continue
            }

            val key = line.substringBefore(" ").trim()
            var value = line.substringAfter(" ").substringBefore("#").trim()
            if (value eqIc "empty()") value = ""

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

    val defaultFileUploadLimit = map["upload limits.file_max"]?.toIntOrNull() ?: 5242880
    val defaultUploadLimit = map["upload limits.total_max"]?.toLongOrNull() ?: 2147482548L
    val maxUploadNotChunked = map["upload limits.unchunked_max"]?.toIntOrNull() ?: 15728640

    val databaseUrl = map["database.url"] ?: "jdbc:mysql://127.0.0.1:3306/media"
    val databaseUser = map["database.username"] ?: "root"
    val databasePasswd = map["database.password"] ?: ""
    val databaseDriver = map["database.driver"] ?: "com.mysql.cj.jdbc.Driver"

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