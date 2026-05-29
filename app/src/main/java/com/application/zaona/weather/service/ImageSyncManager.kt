package com.application.zaona.weather.service

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.xiaomi.xms.wearable.message.MessageApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 自定义天气背景图同步管理器
 * 将用户选择的自定义天气背景图通过 MessageApi 分块传输到手表端
 *
 * 传输协议（JSON + Base64）：
 *   Header: {"type":"header","totalSize":N,"chunkSize":3072,"totalChunks":N,"width":N,"height":N,"weatherCode":"21"}
 *   Data:   {"type":"data","index":N,"chunk":"<base64>"}
 *   End:    {"type":"end"}
 */
object ImageSyncManager {
    private const val PREFS_NAME = "custom_backgrounds"
    private const val KEY_PREFIX = "custom_bg_"
    private const val CHUNK_SIZE = 3072
    private const val MAX_IMAGE_WIDTH = 432
    private const val MAX_IMAGE_HEIGHT = 514

    private lateinit var prefs: SharedPreferences

    /**
     * 所有支持的天气背景图编号及其中文标签
     */
    val WEATHER_BG_CODES = listOf(
        "21" to "晴-白天",
        "22" to "晴-夜晚",
        "23" to "晴-日落",
        "11" to "多云-白天",
        "12" to "多云-夜晚",
        "31" to "阴-全天",
        "41" to "雾霾-白天",
        "42" to "雾霾-夜晚",
        "51" to "雨-白天",
        "52" to "雨-夜晚",
        "61" to "雪-白天",
        "62" to "雪-夜晚"
    )

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getImagePath(weatherCode: String): String? {
        return prefs.getString(KEY_PREFIX + weatherCode, null)
    }

    fun setImagePath(weatherCode: String, imagePath: String) {
        prefs.edit().putString(KEY_PREFIX + weatherCode, imagePath).apply()
    }

    fun removeImagePath(weatherCode: String) {
        prefs.edit().remove(KEY_PREFIX + weatherCode).apply()
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
    private fun decodeAndScale(context: Context, uri: Uri): Bitmap? {
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

            scaleBitmapIfNeeded(bitmap)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 发送单张图片到手表
     */
    suspend fun sendImage(
        messageApi: MessageApi,
        nodeId: String,
        weatherCode: String,
        bitmap: Bitmap
    ): Result<Unit> {
        return try {
            // 压缩为 PNG
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            val data = baos.toByteArray()

            val totalSize = data.size
            val totalChunks = (totalSize + CHUNK_SIZE - 1) / CHUNK_SIZE
            val width = bitmap.width
            val height = bitmap.height

            // 1. 发送 header
            sendHeader(messageApi, nodeId, totalSize, totalChunks, width, height, weatherCode)

            // 2. 发送数据块
            for (i in 0 until totalChunks) {
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
            }

            // 3. 发送 end
            val endJson = JSONObject().apply {
                put("type", "end")
            }
            sendMessageRaw(messageApi, nodeId, endJson.toString())

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 同步所有已配置的自定义背景图
     * @param onProgress 进度回调，参数为 (当前索引, 总数, 天气编号)
     * @return 成功发送的图片数量
     */
    suspend fun syncAllImages(
        context: Context,
        messageApi: MessageApi,
        nodeId: String,
        onProgress: ((current: Int, total: Int, weatherCode: String) -> Unit)? = null
    ): Result<Int> {
        var successCount = 0
        var errorCount = 0

        val configured = WEATHER_BG_CODES.filter { (code, _) ->
            getImagePath(code) != null
        }
        val total = configured.size

        for ((index, pair) in configured.withIndex()) {
            val (code, _) = pair
            onProgress?.invoke(index + 1, total, code)

            try {
                val uri = Uri.parse(getImagePath(code)!!)
                val bitmap = withContext(Dispatchers.IO) { decodeAndScale(context, uri) }
                    ?: continue

                val result = sendImage(messageApi, nodeId, code, bitmap)
                if (result.isSuccess) {
                    successCount++
                } else {
                    errorCount++
                }
            } catch (e: Exception) {
                errorCount++
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
        weatherCode: String
    ) {
        val json = JSONObject().apply {
            put("type", "header")
            put("totalSize", totalSize)
            put("chunkSize", CHUNK_SIZE)
            put("totalChunks", totalChunks)
            put("width", width)
            put("height", height)
            put("weatherCode", weatherCode)
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
