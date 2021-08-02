package me.schooltests.mediahost

import com.google.gson.GsonBuilder
import com.zaxxer.hikari.HikariDataSource
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.http.HttpMethod
import io.ktor.http.content.default
import io.ktor.http.content.files
import io.ktor.http.content.static
import io.ktor.routing.routing
import me.schooltests.mediahost.routes.file.fileRoutes
import me.schooltests.mediahost.routes.file.searchRoutes
import me.schooltests.mediahost.routes.miscRoutes
import me.schooltests.mediahost.routes.userRoutes
import me.schooltests.mediahost.sql.MediaContentTable
import me.schooltests.mediahost.sql.MediaPropertiesTable
import me.schooltests.mediahost.sql.UserAPIKeysTable
import me.schooltests.mediahost.sql.UserTable
import me.schooltests.mediahost.util.getOriginAddress
import me.schooltests.mediahost.util.ratelimiting.RateLimiting
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.util.Timer

const val BLOB_PER_ROW: Int = 15728640

val timer: Timer = Timer("mainTimer")
val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().enableComplexMapKeySerialization().create()

fun main(_args: Array<String>): Unit {
    io.ktor.server.netty.EngineMain.main(_args)
}

@Suppress("unused") // Referenced in application.conf
fun Application.module() {
    initDb()

    install(RateLimiting) {
        this.limit = 120
        this.resetTime = Duration.ofMinutes(1)
        this.keyExtraction = { getOriginAddress() }
        this.pathExclusion = { method: HttpMethod, path: String ->
            method == HttpMethod.Get && path.contains("/file")
        }
    }

    routing {
/*        get("/") {
            val bytes = File("image.jpg").readBytes()
            val mime = MimetypesFileTypeMap().getContentType("image2323.jpg").split("/")
            call.respondBytes(bytes, ContentType(mime[0], mime[1], listOf()))
        }*/

        userRoutes()
        fileRoutes()
        searchRoutes()
        miscRoutes()

        static("/") {
            files("public")
            default("public/index.html")
        }
    }
}

fun initDb() {
    val dataSource = HikariDataSource().apply {
        maximumPoolSize = 20
        driverClassName = ApplicationSettings.databaseDriver
        jdbcUrl = ApplicationSettings.databaseUrl
        addDataSourceProperty("user", ApplicationSettings.databaseUser)
        addDataSourceProperty("password", ApplicationSettings.databasePasswd)
        isAutoCommit = false
    }
    Database.connect(dataSource)
    transaction {
        SchemaUtils.create(MediaPropertiesTable, MediaContentTable, UserAPIKeysTable, UserTable)
    }
}