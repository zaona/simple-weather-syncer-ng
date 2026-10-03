package com.application.zaona.weather.service

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.application.zaona.weather.util.ImageProcessingUtil
import com.xiaomi.xms.wearable.message.MessageApi
import com.xiaomi.xms.wearable.message.OnMessageReceivedListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 自定义天气背景图同步管理器
 * 将用户选择的自定义天气背景图通过 MessageApi 分块传输到手表端
 *
 * 传输协议（JSON + Base64）：
 *   Header: {"type":"header","totalSize":N,"chunkSize":3072,"totalChunks":N,"width":N,"height":N,"weatherCode":"21","current":3,"total":12,"label":"阴-白天"}
 *   Data:   {"type":"data","index":N,"chunk":"<base64>"}
 *   End:    {"type":"end"}
 */
object ImageSyncManager {
    private const val PREFS_NAME = "custom_backgrounds"
    private const val KEY_PREFIX = "custom_bg_"
    private const val KEY_NAME_PREFIX = "custom_bg_name_"
    private const val CHUNK_SIZE = 3072
    private const val MAX_IMAGE_WIDTH = 432
    private const val MAX_IMAGE_HEIGHT = 514

    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context

    /**
     * 所有支持的天气背景图编号及其中文标签
     */
    val WEATHER_BG_CODES = listOf(
        "21" to "晴-白天",
        "22" to "晴-夜晚",
        "23" to "晴-日落",
        "11" to "多云-白天",
        "12" to "多云/阴-夜晚",
        "31" to "阴-白天",
        "41" to "雾霾-白天",
        "42" to "雾霾-夜晚",
        "51" to "雨-白天",
        "52" to "雨-夜晚",
        "61" to "雪-白天",
        "62" to "雪-夜晚"
    )

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        appContext = context.applicationContext
    }

    fun getImagePath(weatherCode: String): String? {
        return prefs.getString(KEY_PREFIX + weatherCode, null)
    }

    fun setImagePath(weatherCode: String, imagePath: String) {
        prefs.edit().putString(KEY_PREFIX + weatherCode, imagePath).apply()
    }

    fun setImageFileName(weatherCode: String, fileName: String) {
        prefs.edit().putString(KEY_NAME_PREFIX + weatherCode, fileName).apply()
    }

    fun getImageFileName(weatherCode: String): String? {
        return prefs.getString(KEY_NAME_PREFIX + weatherCode, null)
    }

    /**
     * 将用户选择的图片复制到应用本地存储，返回 FileProvider URI 字符串。
     * 在 Android 13+ 上，GetContent 返回的 MediaStore URI 仅有临时权限，
     * 必须立即复制到本地以避免后续访问时抛出 SecurityException。
     */
    fun copyImageToLocalStorage(context: Context, sourceUri: android.net.Uri, weatherCode: String): String? {
        return try {
            val bgDir = java.io.File(context.filesDir, "custom_backgrounds")
            if (!bgDir.exists()) bgDir.mkdirs()

            // 从源 URI 获取文件名和扩展名
            var originalName: String? = null
            var extension = "png"
            context.contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    originalName = cursor.getString(nameIndex)
                }
            }
            if (originalName != null) {
                val dotIndex = originalName.lastIndexOf('.')
                if (dotIndex >= 0 && dotIndex < originalName.length - 1) {
                    extension = originalName.substring(dotIndex + 1).lowercase()
                }
            }

            val outFile = java.io.File(bgDir, "${weatherCode}.$extension")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                java.io.FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            val fileProviderUri = FileProviderHelper.getUriForFile(context, outFile)
            fileProviderUri.toString()
        } catch (_: Exception) {
            null
        }
    }

    fun removeImagePath(weatherCode: String) {
        // 删除本地文件
        val path = prefs.getString(KEY_PREFIX + weatherCode, null)
        if (path != null) {
            try {
                val uri = android.net.Uri.parse(path)
                // 仅删除本应用 FileProvider 下的文件，不删除外部 URI 指向的文件
                if (uri.authority?.endsWith(".bgpreset.fileprovider") == true) {
                    val file = java.io.File(
                        java.io.File(appContext.filesDir, "custom_backgrounds"),
                        uri.lastPathSegment ?: ""
                    )
                    if (file.exists()) file.delete()
                }
            } catch (_: Exception) { }
        }
        prefs.edit()
            .remove(KEY_PREFIX + weatherCode)
            .remove(KEY_NAME_PREFIX + weatherCode)
            .apply()
    }

    fun getConfiguredCount(): Int {
        return WEATHER_BG_CODES.count { (code, _) ->
            prefs.getString(KEY_PREFIX + code, null) != null
        }
    }

    fun isConfigured(weatherCode: String): Boolean {
        return prefs.getString(KEY_PREFIX + weatherCode, null) != null
    }

    /**
     * 从 URI 读取并缩放图片到目标尺寸（阻塞 I/O，调用方应切换到 IO 线程）
     */
    private fun decodeAndScale(
        context: Context,
        uri: Uri,
        darkenStrength: Int = 0,
        blurRadius: Int = 0
    ): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            val sampleSize = calculateInSampleSize(
                options.outWidth, options.outHeight,
                MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT
            )

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return null

            val scaled = scaleBitmapIfNeeded(bitmap)

            applyImageEffects(scaled, darkenStrength, blurRadius)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 对缩放后的图片应用压暗和模糊效果（先模糊后压暗）
     */
    private fun applyImageEffects(
        bitmap: Bitmap,
        darkenStrength: Int,
        blurRadius: Int
    ): Bitmap {
        var result = bitmap
        if (blurRadius > 0) {
            result = ImageProcessingUtil.applyBlur(result, blurRadius)
        }
        if (darkenStrength > 0) {
            result = ImageProcessingUtil.applyDarken(result, darkenStrength)
        }
        return result
    }

    /**
     * 手表端主动取消传输时抛出的异常
     */
    class WatchCancelException(message: String = "手表端已取消传输") : Exception(message)

    /**
     * 通知手表端取消当前传输
     */
    suspend fun cancelTransfer(
        messageApi: MessageApi,
        nodeId: String
    ): Result<Unit> {
        return try {
            val json = JSONObject().apply { put("type", "cancel") }
            sendMessageRaw(messageApi, nodeId, json.toString())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 发送单张图片到手表。
     * 画质固定为 RGB_565，以尽量减小传输体积。
     * 传输过程中若收到手表 cancel，立即中止。
     */
    suspend fun sendImage(
        messageApi: MessageApi,
        nodeId: String,
        weatherCode: String,
        bitmap: Bitmap,
        current: Int = 0,
        total: Int = 0,
        label: String = "",
        onChunkProgress: ((sent: Int, totalChunks: Int) -> Unit)? = null
    ): Result<Unit> {
        return try {
            val rgb565 = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.RGB_565)
            val canvas = android.graphics.Canvas(rgb565)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            val baos = ByteArrayOutputStream()
            rgb565.compress(Bitmap.CompressFormat.PNG, 100, baos)
            val data = baos.toByteArray()

            val totalSize = data.size
            val totalChunks = (totalSize + CHUNK_SIZE - 1) / CHUNK_SIZE
            val width = bitmap.width
            val height = bitmap.height

            // 全程监听：手表确认保存 / 手表主动取消
            val latch = CompletableDeferred<Unit>()
            val ackListener = OnMessageReceivedListener { _, message ->
                try {
                    val json = JSONObject(String(message))
                    when (json.optString("type")) {
                        "cancel" -> latch.completeExceptionally(WatchCancelException())
                        "image_saved" -> {
                            if (json.optString("weatherCode") == weatherCode) {
                                latch.complete(Unit)
                            }
                        }
                    }
                } catch (_: Exception) { }
            }
            messageApi.addListener(nodeId, ackListener)
            try {
                // 1. 发送 header
                sendHeader(messageApi, nodeId, totalSize, totalChunks, width, height, weatherCode, current, total, label)

                // 2. 发送数据块
                for (i in 0 until totalChunks) {
                    if (latch.isCompleted) {
                        throw latch.getCompletionExceptionOrNull() ?: WatchCancelException()
                    }
                    val offset = i * CHUNK_SIZE
                    val length = minOf(CHUNK_SIZE, data.size - offset)
                    val chunkData = data.copyOfRange(offset, offset + length)
                    val base64Chunk = Base64.encodeToString(chunkData, Base64.NO_WRAP)

                    val json = JSONObject().apply {
                        put("type", "data")
                        put("index", i)
                        put("chunk", base64Chunk)
                    }
                    sendMessageRaw(messageApi, nodeId, json.toString())
                    onChunkProgress?.invoke(i + 1, totalChunks)
                }

                // 3. 发送 end，等待手表确认（超时 30 秒）
                val endJson = JSONObject().apply { put("type", "end") }
                sendMessageRaw(messageApi, nodeId, endJson.toString())
                withTimeout(30_000) { latch.await() }
            } finally {
                messageApi.removeListener(nodeId)
            }

            Result.success(Unit)
        } catch (e: WatchCancelException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 通知手表清除所有自定义背景图
     */
    suspend fun clearAllOnWatch(
        messageApi: MessageApi,
        nodeId: String
    ): Result<Unit> {
        return try {
            val latch = CompletableDeferred<Unit>()
            val listener = OnMessageReceivedListener { _, message ->
                try {
                    val json = JSONObject(String(message))
                    when (json.optString("type")) {
                        "clear_done" -> latch.complete(Unit)
                        "cancel" -> latch.completeExceptionally(WatchCancelException())
                    }
                } catch (_: Exception) { }
            }
            messageApi.addListener(nodeId, listener)
            try {
                val json = JSONObject().apply { put("type", "clear_all") }
                sendMessageRaw(messageApi, nodeId, json.toString())
                withTimeout(30_000) { latch.await() }
            } finally {
                messageApi.removeListener(nodeId)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 同步所有已配置的自定义背景图
     * @param onProgress 进度回调，参数为 (当前索引, 总数, 天气编号)
     * @return 成功发送的图片数量；若手表端取消则返回失败
     */
    suspend fun syncAllImages(
        context: Context,
        messageApi: MessageApi,
        nodeId: String,
        onProgress: ((current: Int, total: Int, weatherCode: String) -> Unit)? = null,
        onImageSent: ((weatherCode: String, success: Boolean) -> Unit)? = null,
        onChunkProgress: ((sent: Int, totalChunks: Int) -> Unit)? = null
    ): Result<Int> {
        var successCount = 0
        var errorCount = 0

        val imagePrefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
        val darkenStrength = imagePrefs.getInt("bg_darken_strength", 0)
        val blurRadius = imagePrefs.getInt("bg_blur_radius", 0)

        val configured = WEATHER_BG_CODES.filter { (code, _) ->
            getImagePath(code) != null
        }
        val total = configured.size

        for ((index, pair) in configured.withIndex()) {
            val (code, label) = pair
            onProgress?.invoke(index + 1, total, code)

            try {
                val uri = Uri.parse(getImagePath(code)!!)
                val bitmap = withContext(Dispatchers.IO) {
                    decodeAndScale(context, uri, darkenStrength, blurRadius)
                } ?: continue

                val result = sendImage(messageApi, nodeId, code, bitmap, index + 1, total, label, onChunkProgress)
                if (result.isSuccess) {
                    successCount++
                    onImageSent?.invoke(code, true)
                } else {
                    val err = result.exceptionOrNull()
                    if (err is WatchCancelException) {
                        onImageSent?.invoke(code, false)
                        return Result.failure(err)
                    }
                    errorCount++
                    onImageSent?.invoke(code, false)
                }
            } catch (e: WatchCancelException) {
                onImageSent?.invoke(code, false)
                return Result.failure(e)
            } catch (e: Exception) {
                errorCount++
                onImageSent?.invoke(code, false)
            }
        }

        return if (errorCount > 0 && successCount == 0) {
            Result.failure(Exception("所有图片发送失败"))
        } else {
            Result.success(successCount)
        }
    }

    // ---- 私有方法 ----

    private fun calculateInSampleSize(
        rawWidth: Int, rawHeight: Int,
        reqWidth: Int, reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight
                && (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= MAX_IMAGE_WIDTH && bitmap.height <= MAX_IMAGE_HEIGHT) {
            return bitmap
        }
        val ratio = minOf(
            MAX_IMAGE_WIDTH.toFloat() / bitmap.width,
            MAX_IMAGE_HEIGHT.toFloat() / bitmap.height
        )
        val newWidth = (bitmap.width * ratio).toInt()
        val newHeight = (bitmap.height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private suspend fun sendHeader(
        messageApi: MessageApi,
        nodeId: String,
        totalSize: Int,
        totalChunks: Int,
        width: Int,
        height: Int,
        weatherCode: String,
        current: Int,
        total: Int,
        label: String
    ) {
        val json = JSONObject().apply {
            put("type", "header")
            put("totalSize", totalSize)
            put("chunkSize", CHUNK_SIZE)
            put("totalChunks", totalChunks)
            put("width", width)
            put("height", height)
            put("weatherCode", weatherCode)
            put("current", current)
            put("total", total)
            put("label", label)
        }
        sendMessageRaw(messageApi, nodeId, json.toString())
    }

    private suspend fun sendMessageRaw(
        messageApi: MessageApi,
        nodeId: String,
        message: String
    ) {
        suspendCancellableCoroutine<Unit> { continuation ->
            messageApi.sendMessage(nodeId, message.toByteArray(Charsets.UTF_8))
                .addOnSuccessListener {
                    continuation.resume(Unit)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }
    }
}
