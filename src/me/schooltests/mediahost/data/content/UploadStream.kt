package me.schooltests.mediahost.data.content

import com.google.protobuf.BytesValue
import me.schooltests.mediahost.BLOB_PER_ROW
import me.schooltests.mediahost.data.auth.User
import me.schooltests.mediahost.sql.MediaContentTable
import me.schooltests.mediahost.util.CompressionUtil
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import java.lang.IllegalStateException

class UploadStream(val user: User, val contentId: String, val totalSize: Int) {
    var alreadyUploaded: Int = 0
    private set

    private var currentIndex: Int = 0
    private var buffer: ByteArray = ByteArray(BLOB_PER_ROW)
    private var bufferIndex = 0

    fun push(byte: Byte) {
        val arr = ByteArray(1)
        arr[0] = byte
        push(arr)
    }

    fun push(bytes: ByteArray) {
        if (alreadyUploaded + bytes.size > totalSize)
            throw IllegalStateException("Uploading ${bytes.size} bytes will make the stream exceed $totalSize bytes.")

        alreadyUploaded += bytes.size
        for (byte in bytes) {
            if (bufferIndex > BLOB_PER_ROW) pushBuffer()

            buffer[bufferIndex] = byte
            bufferIndex++
        }
    }

    fun clear(index: Int) {
        if (currentIndex > index) return

        alreadyUploaded -= buffer.size
        buffer = ByteArray(BLOB_PER_ROW)
        bufferIndex = 0

        if (currentIndex == index) return
        for (i in index until currentIndex) {
            transaction {
                MediaContentTable.deleteWhere { MediaContentTable.contentId eq this@UploadStream.contentId and (MediaContentTable.index eq i) }
            }

            alreadyUploaded -= BLOB_PER_ROW
        }
    }

    fun pushBuffer() {
        val data = CompressionUtil.gzipCompress(buffer)
        transaction {
            MediaContentTable.insert {
                it[contentId] = this@UploadStream.contentId
                it[totalSize] = this@UploadStream.totalSize
                it[compressed] = data.contentEquals(buffer)

                it[content] = ExposedBlob(data)
                it[index] = bufferIndex
            }
        }

        currentIndex += 1
        buffer = ByteArray(BLOB_PER_ROW)
        bufferIndex = 0
    }

    val isFinished: Boolean
    get() = alreadyUploaded == totalSize
}