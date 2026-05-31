package com.application.zaona.weather.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.annotations.SerializedName
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * BRZ 格式适配器
 *
 * 将 BRZ 预设包（Band 9 等设备）转换为我们的 .swbg 格式，
 * 然后走标准 SWBG 导入流程，不污染 BackgroundPresetManager。
 */
object BrzAdapter {
    private const val TAG = "BrzAdapter"
    private const val MANIFEST_ENTRY = "manifest.json"
    private const val BASE_DIR = "base/"

    // ---- BRZ 数据模型 ----

    data class BrzManifest(
        @SerializedName("schemaVersion")
        val schemaVersion: Int = 1,
        @SerializedName("presetName")
        val presetName: String = "",
        @SerializedName("authorName")
        val authorName: String = "",
        @SerializedName("appVersion")
        val appVersion: String = "",
        @SerializedName("targetModel")
        val targetModel: String = "",
        @SerializedName("targetDisplayName")
        val targetDisplayName: String = "",
        @SerializedName("customEnabled")
        val customEnabled: Boolean = true,
        @SerializedName("effects")
        val effects: BrzEffects = BrzEffects(),
        @SerializedName("bindings")
        val bindings: Map<String, String> = emptyMap(),
        @SerializedName("items")
        val items: List<BrzItem> = emptyList()
    )

    data class BrzEffects(
        @SerializedName("visibilityEnabled")
        val visibilityEnabled: Boolean = true,
        @SerializedName("visibilityLevel")
        val visibilityLevel: Int = 35,
        @SerializedName("blurEnabled")
        val blurEnabled: Boolean = false,
        @SerializedName("blurLevel")
        val blurLevel: Int = 0
    )

    data class BrzItem(
        @SerializedName("id")
        val id: String,
        @SerializedName("title")
        val title: String = "",
        @SerializedName("width")
        val width: Int = 192,
        @SerializedName("height")
        val height: Int = 490,
        @SerializedName("baseEntry")
        val baseEntry: String = ""
    )

    // ---- 天气代码映射 ----

    /**
     * 将 BRZ 的 binding key（如 "morning.clear"）映射为我们的 weatherCode 列表。
     * 返回空列表表示无法映射。
     */
    private fun mapBindingToCodes(bindingKey: String): List<String> {
        val parts = bindingKey.split(".")
        if (parts.size != 2) return emptyList()
        val period = parts[0]   // morning, noon, evening, midnight
        val weather = parts[1]  // clear, cloud, rain, snow, mist

        val isNight = period == "midnight"
        val isSunsetClear = period == "evening" && weather == "clear"

        val codes = mutableListOf<String>()
        when (weather) {
            "clear" -> codes.add(when {
                isSunsetClear -> "23"  // 晴-日落
                isNight -> "22"        // 晴-夜晚
                else -> "21"           // 晴-白天
            })
            "cloud" -> {
                codes.add(if (isNight) "12" else "11")  // 多云
                codes.add("31")                          // 阴-全天
            }
            "rain"  -> codes.add(if (isNight) "52" else "51")
            "snow"  -> codes.add(if (isNight) "62" else "61")
            "mist"  -> codes.add(if (isNight) "42" else "41")
        }
        return codes
    }

    // ---- 公开 API ----

    /**
     * 读取 BRZ 清单，返回预设名称供 UI 显示
     */
    fun peekBrzInfo(context: Context, uri: Uri): Result<BrzManifest> {
        return try {
            val gson = Gson()
            val manifest = readBrzManifest(gson, context, uri)
            if (manifest.bindings.isEmpty()) {
                Result.failure(IllegalStateException("BRZ 预设包中没有绑定配置"))
            } else if (manifest.items.isEmpty()) {
                Result.failure(IllegalStateException("BRZ 预设包中没有图片"))
            } else {
                Result.success(manifest)
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取 BRZ 预设包失败", e)
            Result.failure(e)
        }
    }

    /**
     * 将 BRZ 文件转换为 SWBG 文件
     * @return 转换后的 .swbg 文件
     */
    suspend fun convertToSwbg(context: Context, inputUri: Uri): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val gson = Gson()
                val manifest = readBrzManifest(gson, context, inputUri)
                val itemsById = manifest.items.associateBy { it.id }

                // 构建 mediaId → 目标 weatherCode 集合
                val mediaToCodes = mutableMapOf<String, MutableSet<String>>()
                for ((bindingKey, mediaId) in manifest.bindings) {
                    val codes = mapBindingToCodes(bindingKey)
                    if (codes.isNotEmpty()) {
                        mediaToCodes.getOrPut(mediaId) { mutableSetOf() }.addAll(codes)
                    }
                }

                // 将 BRZ 效果映射为全局设置。BRZ 无画质参数，默认最低。
                val globalDarken = if (manifest.effects.visibilityEnabled)
                    manifest.effects.visibilityLevel else 0
                val globalBlur = if (manifest.effects.blurEnabled)
                    manifest.effects.blurLevel else 0

                val outputFile = File(context.cacheDir, "swbg_brz_converted${BackgroundPresetManager.FILE_EXTENSION}")
                if (outputFile.exists()) outputFile.delete()

                val swbgGson = Gson()

                ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
                    val presets = mutableListOf<BackgroundPresetManager.PresetEntry>()
                    val writtenCodes = mutableSetOf<String>()  // 防止同一 code 被多个 binding 重复写入

                    // 重新打开流，提取图片并写入 SWBG
                    context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                        ZipInputStream(inputStream).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                if (!entry.isDirectory && entry.name.startsWith(BASE_DIR)) {
                                    val entryFileName = entry.name.removePrefix(BASE_DIR)
                                    val mediaId = entryFileName.substringBeforeLast('.')
                                    val format = entryFileName.substringAfterLast('.', "png").takeIf { it.isNotEmpty() } ?: "png"
                                    val codes = mediaToCodes[mediaId]
                                    if (codes != null && codes.isNotEmpty()) {
                                        // 读取图片字节
                                        val imageBytes = java.io.ByteArrayOutputStream().use { baos ->
                                            val buffer = ByteArray(4096)
                                            var len: Int
                                            while (zis.read(buffer).also { len = it } != -1) {
                                                baos.write(buffer, 0, len)
                                            }
                                            baos.toByteArray()
                                        }

                                        val item = itemsById[mediaId]
                                        val originalFileName = item?.title ?: "$mediaId.$format"

                                        for (code in codes) {
                                            if (!writtenCodes.add(code)) continue  // 已写入过，跳过
                                            val imageEntryName = "images/${code}.$format"
                                            zos.putNextEntry(ZipEntry(imageEntryName))
                                            zos.write(imageBytes)
                                            zos.closeEntry()

                                            presets.add(BackgroundPresetManager.PresetEntry(
                                                weatherCode = code,
                                                weatherLabel = ImageSyncManager.WEATHER_BG_CODES
                                                    .firstOrNull { it.first == code }?.second ?: code,
                                                imageFile = imageEntryName,
                                                imageFormat = format,
                                                originalFileName = originalFileName
                                            ))
                                        }
                                    }
                                }
                                entry = zis.nextEntry
                            }
                        }
                    } ?: throw IllegalStateException("无法读取 BRZ 预设包文件")

                    // 写入 SWBG manifest.json
                    val swbgManifest = BackgroundPresetManager.PresetManifest(
                        formatVersion = BackgroundPresetManager.FORMAT_VERSION,
                        globalSettings = BackgroundPresetManager.GlobalSettings(
                            darkenStrength = globalDarken,
                            blurRadius = globalBlur,
                            quality = 10
                        ),
                        presets = presets
                    )
                    zos.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                    val manifestJson = swbgGson.toJson(swbgManifest)
                    zos.write(manifestJson.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }

                Result.success(outputFile)
            } catch (e: Exception) {
                Log.e(TAG, "BRZ 转换失败", e)
                Result.failure(e)
            }
        }

    // ---- 内部方法 ----

    private fun readBrzManifest(gson: Gson, context: Context, uri: Uri): BrzManifest {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == MANIFEST_ENTRY) {
                        val json = String(zis.readBytes(), Charsets.UTF_8)
                        return gson.fromJson(json, BrzManifest::class.java)
                    }
                    entry = zis.nextEntry
                }
            }
        } ?: throw IllegalStateException("无法读取 BRZ 预设包文件")
        throw IllegalStateException("BRZ 预设包中未找到 manifest.json")
    }
}
