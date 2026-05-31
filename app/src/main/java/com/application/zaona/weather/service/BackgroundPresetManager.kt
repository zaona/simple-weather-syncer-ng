package com.application.zaona.weather.service

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 背景预设包管理器
 *
 * 负责将用户自定义的背景图片及处理参数打包为 .swbg 文件（ZIP 格式），
 * 以及从 .swbg 文件导入预设。
 *
 * 文件格式 (.swbg = ZIP)：
 *   manifest.json  – 元数据、全局设置、预设列表
 *   images/         – 原始图片文件，按 weatherCode 命名
 *
 * 可拓展性：
 *   - formatVersion 用于版本控制，解析时向前兼容
 *   - metadata 对象可自由扩充
 *   - ZIP 容器可新增文件/目录而不破坏旧解析器
 *   - 每个 preset 的 settings 支持未来按图片独立设置
 */

object BackgroundPresetManager {
    const val FILE_EXTENSION = ".swbg"
    const val MIME_TYPE = "application/octet-stream"
    const val FORMAT_VERSION = 1

    private const val TAG = "BackgroundPresetManager"
    private const val MANIFEST_ENTRY = "manifest.json"
    private const val IMAGES_DIR = "images/"
    private const val PREFS_NAME = "custom_backgrounds"
    private const val KEY_PREFIX = "custom_bg_"
    private const val SETTINGS_PREFS_NAME = "weather_prefs"

    private val gson = Gson()

    // ---- 数据模型 ----

    /** 预设包的完整清单 */
    data class PresetManifest(
        @SerializedName("formatVersion")
        val formatVersion: Int = FORMAT_VERSION,

        @SerializedName("appVersion")
        val appVersion: String = "",

        @SerializedName("exportTimestamp")
        val exportTimestamp: Long = System.currentTimeMillis(),

        @SerializedName("metadata")
        val metadata: Map<String, String> = emptyMap(),

        @SerializedName("globalSettings")
        val globalSettings: GlobalSettings = GlobalSettings(),

        @SerializedName("presets")
        val presets: List<PresetEntry> = emptyList()
    )

    /** 全局处理设置 */
    data class GlobalSettings(
        @SerializedName("darkenStrength")
        val darkenStrength: Int = 0,

        @SerializedName("blurRadius")
        val blurRadius: Int = 0,

        @SerializedName("quality")
        val quality: Int = 85,

        @SerializedName("advancedSyncMode")
        val advancedSyncMode: Boolean = true
    )

    /** 单个预设条目 */
    data class PresetEntry(
        @SerializedName("weatherCode")
        val weatherCode: String,

        @SerializedName("weatherLabel")
        val weatherLabel: String = "",

        @SerializedName("imageFile")
        val imageFile: String,

        @SerializedName("imageFormat")
        val imageFormat: String = "png",

        @SerializedName("originalFileName")
        val originalFileName: String = "",

        @SerializedName("settings")
        val settings: PresetSettings = PresetSettings()
    )

    /** 单个图片的处理设置（预留未来按图片独立设置） */
    data class PresetSettings(
        @SerializedName("darkenStrength")
        val darkenStrength: Int = 0,

        @SerializedName("blurRadius")
        val blurRadius: Int = 0,

        @SerializedName("quality")
        val quality: Int = 85
    )

    // ---- 公开 API ----

    /**
     * 导出所有已配置的预设到指定文件
     * @return 成功时返回写入的文件
     */
    suspend fun exportToFile(context: Context, outputFile: File): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val settingsPrefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)

                val configuredCodes = ImageSyncManager.WEATHER_BG_CODES.filter { (code, _) ->
                    prefs.getString(KEY_PREFIX + code, null) != null
                }

                if (configuredCodes.isEmpty()) {
                    return@withContext Result.failure(IllegalStateException("没有已配置的背景图可导出"))
                }

                val globalSettings = GlobalSettings(
                    darkenStrength = settingsPrefs.getInt("bg_darken_strength", 0),
                    blurRadius = settingsPrefs.getInt("bg_blur_radius", 0),
                    quality = settingsPrefs.getInt("bg_quality", 85),
                    advancedSyncMode = settingsPrefs.getBoolean("advanced_sync_mode", true)
                )

                val presets = mutableListOf<PresetEntry>()

                ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
                    for ((code, label) in configuredCodes) {
                        val uriString = prefs.getString(KEY_PREFIX + code, null) ?: continue
                        val uri = Uri.parse(uriString)

                        // 获取原始文件名和格式
                        val (fileName, format) = getFileInfo(context, uri, code)

                        val zipEntryName = "$IMAGES_DIR$code.$format"
                        val entry = PresetEntry(
                            weatherCode = code,
                            weatherLabel = label,
                            imageFile = zipEntryName,
                            imageFormat = format,
                            originalFileName = fileName,
                            settings = PresetSettings(
                                darkenStrength = globalSettings.darkenStrength,
                                blurRadius = globalSettings.blurRadius,
                                quality = globalSettings.quality
                            )
                        )
                        presets.add(entry)

                        // 将原始图片数据写入 ZIP
                        zos.putNextEntry(ZipEntry(zipEntryName))
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            input.copyTo(zos)
                        } ?: throw IllegalStateException("无法读取图片: $label")
                        zos.closeEntry()
                    }

                    // 写入 manifest.json
                    val manifest = PresetManifest(
                        formatVersion = FORMAT_VERSION,
                        appVersion = getAppVersion(context),
                        exportTimestamp = System.currentTimeMillis(),
                        globalSettings = globalSettings,
                        presets = presets
                    )
                    zos.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                    val manifestJson = gson.toJson(manifest)
                    zos.write(manifestJson.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }

                Result.success(outputFile)
            } catch (e: Exception) {
                Log.e(TAG, "导出预设包失败", e)
                Result.failure(e)
            }
        }

    /**
     * 从 URI 导入预设包
     * 仅解析并返回预设数量，不实际执行导入。
     * 调用 [performImport] 执行实际导入。
     *
     * @return 成功时返回解析出的预设数量
     */
    suspend fun peekImportInfo(context: Context, inputUri: Uri): Result<PresetManifest> =
        withContext(Dispatchers.IO) {
            try {
                val manifest = readManifest(context, inputUri)

                // 版本检查
                if (manifest.formatVersion > FORMAT_VERSION) {
                    Log.w(TAG, "预设包版本 ($manifest.formatVersion) 高于当前支持版本 ($FORMAT_VERSION)，尝试兼容解析")
                }

                if (manifest.presets.isEmpty()) {
                    return@withContext Result.failure(IllegalStateException("预设包中没有图片"))
                }

                Result.success(manifest)
            } catch (e: Exception) {
                Log.e(TAG, "读取预设包信息失败", e)
                Result.failure(e)
            }
        }

    /**
     * 执行实际导入操作（单次遍历，避免对慢速 URI 的重复读取）
     * @return 成功导入的图片数量
     */
    suspend fun performImport(context: Context, inputUri: Uri): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                // 单次遍历 ZIP：先读 manifest，再提取图片
                var manifest: PresetManifest? = null
                val imageDataList = mutableListOf<Pair<String, ByteArray>>() // imageFileName -> bytes

                context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                when {
                                    entry.name == MANIFEST_ENTRY -> {
                                        val json = String(zis.readBytes(), Charsets.UTF_8)
                                        manifest = gson.fromJson(json, PresetManifest::class.java)
                                    }
                                    entry.name.startsWith(IMAGES_DIR) -> {
                                        val imageFileName = entry.name.removePrefix(IMAGES_DIR)
                                        val bytes = java.io.ByteArrayOutputStream().use { baos ->
                                            zis.copyTo(baos)
                                            baos.toByteArray()
                                        }
                                        imageDataList.add(imageFileName to bytes)
                                    }
                                }
                            }
                            entry = zis.nextEntry
                        }
                    }
                } ?: throw IllegalStateException("无法读取预设包文件")

                if (manifest == null) {
                    throw IllegalStateException("预设包中未找到 manifest.json")
                }

                // 保存全局设置
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val settingsPrefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
                settingsPrefs.edit()
                    .putInt("bg_darken_strength", manifest!!.globalSettings.darkenStrength)
                    .putInt("bg_blur_radius", manifest!!.globalSettings.blurRadius)
                    .putInt("bg_quality", manifest!!.globalSettings.quality)
                    .putBoolean("advanced_sync_mode", manifest!!.globalSettings.advancedSyncMode)
                    .apply()

                // 准备存储目录并写入图片
                val bgDir = File(context.filesDir, "custom_backgrounds")
                if (!bgDir.exists()) bgDir.mkdirs()

                var importedCount = 0
                val presetCodes = manifest!!.presets.map { it.weatherCode }.toSet()

                for ((imageFileName, bytes) in imageDataList) {
                    val code = imageFileName.substringBefore(".")
                    if (code !in presetCodes) continue

                    val outFile = File(bgDir, imageFileName)
                    outFile.writeBytes(bytes)
                    val contentUri = FileProviderHelper.getUriForFile(context, outFile)
                    prefs.edit()
                        .putString(KEY_PREFIX + code, contentUri.toString())
                        .apply()
                    importedCount++
                }

                if (importedCount == 0) {
                    return@withContext Result.failure(IllegalStateException("未找到可导入的图片"))
                }

                Result.success(importedCount)
            } catch (e: Exception) {
                Log.e(TAG, "导入预设包失败", e)
                Result.failure(e)
            }
        }

    /**
     * 导出到缓存目录并返回文件用于分享
     */
    suspend fun prepareShareFile(context: Context): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val shareFile = File(context.cacheDir, "swbg_share$FILE_EXTENSION")
                if (shareFile.exists()) shareFile.delete()
                val result = exportToFile(context, shareFile)
                if (result.isFailure) {
                    return@withContext Result.failure(result.exceptionOrNull()!!)
                }
                Result.success(shareFile)
            } catch (e: Exception) {
                Log.e(TAG, "准备分享文件失败", e)
                Result.failure(e)
            }
        }

    // ---- 私有方法 ----

    /** 读取并解析 manifest.json */
    private fun readManifest(context: Context, uri: Uri): PresetManifest {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == MANIFEST_ENTRY) {
                        val json = String(zis.readBytes(), Charsets.UTF_8)
                        return gson.fromJson(json, PresetManifest::class.java)
                    }
                    entry = zis.nextEntry
                }
            }
        } ?: throw IllegalStateException("无法读取预设包文件")

        throw IllegalStateException("预设包中未找到 manifest.json")
    }

    /** 获取原始文件名和格式 */
    private fun getFileInfo(context: Context, uri: Uri, weatherCode: String): Pair<String, String> {
        var fileName = "$weatherCode.png"
        var format = "png"

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex) ?: fileName
                    }
                }
            }
        } catch (_: Exception) { }

        // 从文件名提取格式
        val dotIndex = fileName.lastIndexOf('.')
        if (dotIndex >= 0 && dotIndex < fileName.length - 1) {
            format = fileName.substring(dotIndex + 1).lowercase()
        }

        return Pair(fileName, format)
    }

    /** 获取应用版本号 */
    private fun getAppVersion(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }
}

/**
 * FileProvider URI 生成辅助类
 * 与 AndroidManifest 中声明的 authority 保持一致
 */
object FileProviderHelper {
    private const val AUTHORITY_SUFFIX = ".bgpreset.fileprovider"

    fun getUriForFile(context: Context, file: File): Uri {
        val authority = context.packageName + AUTHORITY_SUFFIX
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            authority,
            file
        )
    }
}
