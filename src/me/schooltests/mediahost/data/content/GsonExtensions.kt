package me.schooltests.mediahost.data.content

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import me.schooltests.mediahost.data.auth.User
import me.schooltests.mediahost.gson
import me.schooltests.mediahost.managers.UserManager

val JsonElement.asJson: String
get() {
    return gson.toJson(this)
}

val User.asJsonObject: JsonObject
get() {
    val obj = JsonObject()
    obj.addProperty("id", this.userId)
    obj.addProperty("name", this.username)
    obj.addProperty("created", this.dateCreated.time)
    obj.addProperty("uploaded", this.bytesUploaded)
    obj.addProperty("max_file_upload", this.maxFileUpload)
    obj.addProperty("max_total_upload", this.maxUpload)
    return obj
}

val MediaContent.asJsonObject: JsonObject
get() {
    val obj = JsonObject()
    obj.add("owner", UserManager.queryUserById(this.userId)?.asJsonObject)
    obj.addProperty("id", this.contentId)
    obj.addProperty("name", this.fileName)
    obj.addProperty("created", this.dateCreated.time)

    obj.addProperty("size", this.contentSize)
    obj.addProperty("privacy", this.privacy.displayName)
    obj.addProperty("extension", this.extension)
    return obj
}

val UploadStream.asJsonObject: JsonObject
get() {
    val obj = JsonObject()
    obj.addProperty("uploaded", this.alreadyUploaded)
    obj.addProperty("expecting", this.totalSize)
    obj.addProperty("finished", this.isFinished)
    return obj
}