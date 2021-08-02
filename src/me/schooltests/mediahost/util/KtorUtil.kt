package me.schooltests.mediahost.util

import com.google.gson.JsonObject
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.header
import io.ktor.response.respondText
import io.ktor.server.netty.NettyApplicationCall
import io.ktor.util.InternalAPI
import io.ktor.util.pipeline.PipelineContext
import me.schooltests.mediahost.ApplicationSettings
import me.schooltests.mediahost.data.content.asJson
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

@OptIn(InternalAPI::class, io.ktor.server.engine.EngineAPI::class)
fun PipelineContext<*, ApplicationCall>.getOriginAddress(): String {
    val f = context::class.memberProperties.find { it.name == "call" }

    f?.let {
        // Making it accessible
        it.isAccessible = true
        val w = it.getter.call(context) as NettyApplicationCall

        // Getting the remote address
        var ip = w.request.context.pipeline().channel().remoteAddress().toString()
            .replace("/", "")
        ip = ip.substring(0, ip.indexOf(":"))

        if (ip == ApplicationSettings.serverPublicIp || ip == "127.0.0.1" || ip == "localhost") {
            return call.request.header("X-Real-IP") ?: ip
        }

        return ip
    }

    return "0.0.0.0"
}

suspend fun PipelineContext<*, ApplicationCall>.failedRequest(reason: String, status: HttpStatusCode) {
    val json = JsonObject()
    json.addProperty("message", reason)
    json.addProperty("httpCode", status.value)

    call.respondText(json.asJson, status = status)
}