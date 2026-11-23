package io.github.xccxcxxc.runnel.ui.reader

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import io.github.xccxcxxc.runnel.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 阅读页里长按图片：保存到系统相册（Pictures/Runnel）或交给系统分享面板。 */
object ImageActions {
    class Picture(val bytes: ByteArray, val mime: String, val name: String)

    private const val SHARE_DIR = "shared-images"

    /** 取 [url] 的图片（缓存优先）；[referer] 是文章地址，有的图床靠它防盗链。失败返回 null。 */
    suspend fun load(url: String, referer: String?): Picture? = withContext(Dispatchers.IO) {
        val bytes: ByteArray
        val declared: String?
        if (url.startsWith("data:")) {
            val comma = url.indexOf(',')
            if (comma < 0) return@withContext null
            val head = url.substring(5, comma)
            declared = head.substringBefore(';').ifBlank { null }
            bytes = runCatching {
                if (head.endsWith(";base64")) Base64.decode(url.substring(comma + 1), Base64.DEFAULT)
                else Uri.decode(url.substring(comma + 1)).toByteArray()
            }.getOrNull() ?: return@withContext null
        } else {
            val got = Graph.get().readerImages.download(url, referer) ?: return@withContext null
            bytes = got.bytes
            declared = got.mime
        }
        if (bytes.isEmpty()) return@withContext null
        val mime = declared?.takeIf { it.startsWith("image/") } ?: sniff(bytes) ?: "image/jpeg"
        Picture(bytes, mime, fileName(url, mime))
    }

    /** 存进 MediaStore（Android 10+ 不需要存储权限）。返回是否成功。 */
    suspend fun save(context: Context, picture: Picture): Boolean = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, picture.name)
                put(MediaStore.Images.Media.MIME_TYPE, picture.mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Runnel")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("insert failed")
            try {
                resolver.openOutputStream(uri)!!.use { it.write(picture.bytes) }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        }.isSuccess
    }

    /** 写到应用缓存里，经 FileProvider 交给系统分享面板。返回是否成功。 */
    suspend fun share(context: Context, picture: Picture): Boolean {
        val uri = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() } // 只留最近一张
                val file = File(dir, picture.name).apply { writeBytes(picture.bytes) }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.getOrNull()
        } ?: return false
        val send = Intent(Intent.ACTION_SEND).setType(picture.mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        return runCatching { context.startActivity(Intent.createChooser(send, null)) }.isSuccess
    }

    private fun fileName(url: String, mime: String): String {
        val last = if (url.startsWith("data:")) "" else Uri.parse(url).lastPathSegment.orEmpty()
        val base = last.substringBeforeLast('.').replace(Regex("[^\\w-]"), "_").take(60).ifBlank { "image_${System.currentTimeMillis()}" }
        return "$base.${extension(mime)}"
    }

    private fun extension(mime: String) = when (mime.lowercase()) {
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/avif" -> "avif"
        "image/svg+xml" -> "svg"
        "image/bmp" -> "bmp"
        else -> "jpg"
    }

    /** 服务器没给图片类型时按文件头判断。 */
    private fun sniff(b: ByteArray): String? = when {
        b.size >= 4 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() && b[3] == 'G'.code.toByte() -> "image/png"
        b.size >= 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> "image/jpeg"
        b.size >= 4 && b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte() -> "image/gif"
        b.size >= 12 && String(b, 0, 4, Charsets.ISO_8859_1) == "RIFF" && String(b, 8, 4, Charsets.ISO_8859_1) == "WEBP" -> "image/webp"
        else -> null
    }
}
