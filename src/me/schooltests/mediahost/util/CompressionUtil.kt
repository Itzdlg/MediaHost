package me.schooltests.mediahost.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object CompressionUtil {
    fun gzipCompress(uncompressedData: ByteArray): ByteArray {
        var result: ByteArray
        try {
            ByteArrayOutputStream(uncompressedData.size).use { bos ->
                GZIPOutputStream(bos).use { gzipOS ->
                    gzipOS.write(uncompressedData)
                    // You need to close it before using bos
                    gzipOS.close()
                    result = bos.toByteArray()
                }
            }
        } catch (e: IOException) {
            return uncompressedData
        }

        return result
    }

    fun gzipUncompress(compressedData: ByteArray): ByteArray {
        var result: ByteArray
        try {
            ByteArrayInputStream(compressedData).use { bis ->
                ByteArrayOutputStream().use { bos ->
                    GZIPInputStream(bis).use { gzipIS ->
                        val buffer = ByteArray(1024)
                        var len: Int
                        while (gzipIS.read(buffer).also { len = it } != -1) {
                            bos.write(buffer, 0, len)
                        }
                        result = bos.toByteArray()
                    }
                }
            }
        } catch (e: IOException) {
            return compressedData
        }

        return result
    }
}