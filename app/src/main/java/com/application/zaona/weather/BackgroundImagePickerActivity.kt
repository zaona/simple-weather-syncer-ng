package com.application.zaona.weather

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.application.zaona.weather.service.BackgroundPresetManager
import com.application.zaona.weather.service.BrzAdapter
import com.application.zaona.weather.service.ImageSyncManager
import com.application.zaona.weather.ui.theme.SimpleweathersyncerngTheme
import com.application.zaona.weather.ui.component.MarkdownText
import com.application.zaona.weather.util.ImageProcessingUtil
import com.xiaomi.xms.wearable.Wearable
import com.xiaomi.xms.wearable.auth.AuthApi
import com.xiaomi.xms.wearable.auth.Permission
import com.xiaomi.xms.wearable.message.MessageApi
import com.xiaomi.xms.wearable.message.OnMessageReceivedListener
import com.xiaomi.xms.wearable.node.Node
import com.xiaomi.xms.wearable.node.NodeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.FabPosition
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.menu.OverlayIconCascadingDropdownMenu
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Create
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Close2
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private enum class StepStatus { PENDING, IN_PROGRESS, DONE, ERROR }

private data class SyncStep(val label: String, val status: StepStatus = StepStatus.PENDING)

class BackgroundImagePickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimpleweathersyncerngTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val topBarState = rememberTopAppBarState()
                val scrollBehavior = MiuixScrollBehavior(state = topBarState)
                var selectedCode by remember { mutableStateOf<String?>(null) }
                var isSyncing by remember { mutableStateOf(false) }

                val imagePaths = remember {
                    val map = mutableStateMapOf<String, String?>()
                    ImageSyncManager.WEATHER_BG_CODES.forEach { (code, _) ->
                        map[code] = ImageSyncManager.getImagePath(code)
                    }
                    map
                }
                // 缩略图缓存，避免 LazyColumn 回收 item 时重新加载
                val thumbnailCache = remember { mutableStateMapOf<String, ImageBitmap?>() }
                val configuredCount = imagePaths.values.count { it != null }

                val prefs = remember { context.getSharedPreferences("weather_prefs", android.content.Context.MODE_PRIVATE) }
                var darkenStrength by remember { mutableStateOf(prefs.getInt("bg_darken_strength", 0)) }
                var blurRadius by remember { mutableStateOf(prefs.getInt("bg_blur_radius", 0)) }
                var quality by remember { mutableStateOf(prefs.getInt("bg_quality", 10)) }

                // 预览参数仅在松手时更新，避免拖动滑块时频繁重新生成缩略图
                var darkenPreview by remember { mutableStateOf(darkenStrength) }
                var blurPreview by remember { mutableStateOf(blurRadius) }

                val nodeApi = remember { Wearable.getNodeApi(context.applicationContext) }
                val messageApi = remember { Wearable.getMessageApi(context.applicationContext) }
                val authApi = remember { Wearable.getAuthApi(context.applicationContext) }

                val imagePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    if (uri != null && selectedCode != null) {
                        val code = selectedCode!!
                        // 在 Android 13+ 上，GetContent 返回的 MediaStore URI 仅有
                        // 临时读取权限，必须立即复制到本地存储，否则后续访问（分享、
                        // 缩略图加载、同步）会抛出 SecurityException。
                        scope.launch {
                            val localUri = withContext(Dispatchers.IO) {
                                // 读取原始文件名（用于 UI 显示）
                                val originalName: String? = try {
                                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                        if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
                                    }
                                } catch (_: Exception) { null }

                                val resultPath = ImageSyncManager.copyImageToLocalStorage(context, uri, code)
                                if (resultPath != null && originalName != null) {
                                    ImageSyncManager.setImageFileName(code, originalName)
                                }
                                resultPath
                            }
                            if (localUri != null) {
                                ImageSyncManager.setImagePath(code, localUri)
                                imagePaths[code] = localUri
                            } else {
                                Toast.makeText(context, "读取图片失败，请重试", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                val showImportConfirmDialog = remember { mutableStateOf(false) }
                var importConfirmPresetCount by remember { mutableStateOf(0) }
                var importConfirmTitle by remember { mutableStateOf("确认导入") }
                var importConfirmSummary by remember { mutableStateOf("") }
                var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
                var isBrzImport by remember { mutableStateOf(false) }
                var showLoadingDialog by remember { mutableStateOf(false) }
                var loadingMessage by remember { mutableStateOf("") }
                var showResultDialog by remember { mutableStateOf(false) }
                var resultTitle by remember { mutableStateOf("") }
                var resultSummary by remember { mutableStateOf("") }
                val showPermissionDeniedDialog = remember { mutableStateOf(false) }

                val importLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    if (uri != null) {
                        // 检查文件后缀名
                        val fileName = runCatching {
                            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
                            }
                        }.getOrNull()
                        val isBrz = fileName?.endsWith(".brz") == true
                        if (fileName != null &&
                            !fileName.endsWith(BackgroundPresetManager.FILE_EXTENSION) &&
                            !isBrz) {
                            resultTitle = "导入失败"
                            resultSummary = "请选择 .swbg 或 .brz 格式的预设包文件"
                            showResultDialog = true
                            return@rememberLauncherForActivityResult
                        }

                        isBrzImport = isBrz
                        scope.launch {
                            loadingMessage = "正在解析预设包"
                            showLoadingDialog = true
                            if (isBrz) {
                                val result = BrzAdapter.peekBrzInfo(context, uri)
                                showLoadingDialog = false
                                if (result.isSuccess) {
                                    pendingImportUri = uri
                                    val manifest = result.getOrNull()
                                    val name = manifest?.presetName ?: "BRZ 预设"
                                    importConfirmTitle = "导入 BRZ 预设"
                                    importConfirmSummary = "BRZ 预设包「$name」包含 ${manifest?.bindings?.size ?: 0} 个绑定配置，将映射导入到对应天气类型。若已有配置将被覆盖。确定继续吗？"
                                    showImportConfirmDialog.value = true
                                } else {
                                    resultTitle = "导入失败"
                                    resultSummary = result.exceptionOrNull()?.message ?: "未知错误"
                                    showResultDialog = true
                                }
                            } else {
                                val result = BackgroundPresetManager.peekImportInfo(context, uri)
                                showLoadingDialog = false
                                if (result.isSuccess) {
                                    pendingImportUri = uri
                                    importConfirmTitle = "确认导入"
                                    importConfirmSummary = "预设包包含 ${result.getOrNull()?.presets?.size ?: 0} 张背景图，若已有配置将被覆盖。确定继续吗？"
                                    showImportConfirmDialog.value = true
                                } else {
                                    resultTitle = "导入失败"
                                    resultSummary = result.exceptionOrNull()?.message ?: "未知错误"
                                    showResultDialog = true
                                }
                            }
                        }
                    }
                }

                val exportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument(BackgroundPresetManager.MIME_TYPE)
                ) { uri: Uri? ->
                    if (uri != null) {
                        scope.launch {
                            loadingMessage = "正在导出预设包"
                            showLoadingDialog = true
                            try {
                                val outFile = java.io.File(
                                    context.cacheDir,
                                    "swbg_export${BackgroundPresetManager.FILE_EXTENSION}"
                                )
                                if (outFile.exists()) outFile.delete()
                                val result = BackgroundPresetManager.exportToFile(context, outFile)
                                if (result.isSuccess) {
                                    // 复制到用户选择的位置
                                    context.contentResolver.openOutputStream(uri)?.use { os ->
                                        outFile.inputStream().use { it.copyTo(os) }
                                    }
                                    showLoadingDialog = false
                                    resultTitle = "导出成功"
                                    resultSummary = "预设包已保存"
                                    showResultDialog = true
                                } else {
                                    showLoadingDialog = false
                                    resultTitle = "导出失败"
                                    resultSummary = result.exceptionOrNull()?.message ?: "未知错误"
                                    showResultDialog = true
                                }
                            } catch (e: Exception) {
                                showLoadingDialog = false
                                resultTitle = "导出失败"
                                resultSummary = e.message ?: "未知错误"
                                showResultDialog = true
                            }
                        }
                    }
                }

                val showSyncSheet = remember { mutableStateOf(false) }
                val showHelpSheet = remember { mutableStateOf(false) }
                var syncSheetTitle by remember { mutableStateOf("") }
                val syncSteps = remember { mutableStateListOf<SyncStep>() }
                var syncFinished by remember { mutableStateOf(false) }
                var syncResultSummary by remember { mutableStateOf("") }
                var syncProgress by remember { mutableStateOf(0f) }
                var syncProgressText by remember { mutableStateOf("") }
                var syncJob by remember { mutableStateOf<Job?>(null) }
                val showClearConfirmDialog = remember { mutableStateOf(false) }

                fun performClear() {
                    if (isSyncing) return

                    val advancedMode = prefs.getBoolean("advanced_sync_mode", true)
                    val preambleSteps = mutableListOf(
                        SyncStep("连接手表"),
                        SyncStep("检查应用安装")
                    )
                    if (advancedMode) {
                        preambleSteps.add(SyncStep("启动应用并握手"))
                    }
                    preambleSteps.add(SyncStep("检查权限"))
                    preambleSteps.add(SyncStep("清除自定义背景图"))

                    syncSteps.clear()
                    syncSteps.addAll(preambleSteps)
                    syncFinished = false
                    syncResultSummary = ""
                    syncSheetTitle = "清除自定义背景图"
                    showSyncSheet.value = true
                }

                fun startClear() {
                    if (isSyncing) return
                    isSyncing = true

                    val advancedMode = prefs.getBoolean("advanced_sync_mode", true)

                    syncJob = scope.launch {
                        var stepIdx = 0
                        try {
                            // Step: 连接
                            syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.IN_PROGRESS)
                            val nodes = withTimeout(10_000) { getConnectedNodes(nodeApi) }
                            val node = nodes.firstOrNull()
                            if (node == null) {
                                syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.ERROR)
                                syncResultSummary = "未连接手表"
                                syncFinished = true; isSyncing = false; return@launch
                            }
                            syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.DONE)
                            stepIdx++

                            // Step: 检查应用安装
                            syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.IN_PROGRESS)
                            val isInstalled = try {
                                checkWatchAppInstalled(nodeApi, node.id)
                            } catch (e: Exception) {
                                val msg = e.message ?: ""
                                if (msg.contains("permission denied", ignoreCase = true)) {
                                    syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.ERROR)
                                    syncResultSummary = "权限不足"
                                    syncFinished = true; isSyncing = false
                                    showPermissionDeniedDialog.value = true
                                    return@launch
                                } else {
                                    throw e
                                }
                            }
                            if (!isInstalled) {
                                syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.ERROR)
                                syncResultSummary = "手表端未安装应用，请先安装"
                                syncFinished = true; isSyncing = false; return@launch
                            }
                            syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.DONE)
                            stepIdx++

                            // Step: 握手 (if advanced)
                            if (advancedMode) {
                                syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.IN_PROGRESS)
                                try {
                                    performWatchHandshake(nodeApi, messageApi, node.id)
                                    syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.DONE)
                                } catch (e: Exception) {
                                    syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.ERROR)
                                    syncResultSummary = "握手失败: ${e.message}"
                                    syncFinished = true; isSyncing = false; return@launch
                                }
                                stepIdx++
                            }

                            // Step: 权限
                            syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.IN_PROGRESS)
                            val granted = suspendCancellableCoroutine { cont ->
                                checkAndRequestPermission(authApi, node.id) { cont.resume(it) }
                            }
                            if (!granted) {
                                syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.ERROR)
                                syncResultSummary = "权限被拒绝"
                                syncFinished = true; isSyncing = false; return@launch
                            }
                            syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.DONE)
                            stepIdx++

                            // Step: 清除
                            syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.IN_PROGRESS)
                            val result = ImageSyncManager.clearAllOnWatch(messageApi, node.id)
                            if (result.isSuccess) {
                                syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.DONE)
                                syncResultSummary = "已清除所有自定义背景图"
                            } else {
                                syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.ERROR)
                                syncResultSummary = result.exceptionOrNull()?.message ?: "未知错误"
                            }
                            syncFinished = true; isSyncing = false
                        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                            syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.ERROR)
                            syncResultSummary = "连接手表超时"
                            syncFinished = true; isSyncing = false
                        } catch (e: Exception) {
                            syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.ERROR)
                            syncResultSummary = e.message ?: "未知错误"
                            syncFinished = true; isSyncing = false
                        }
                    }
                }

                fun performSync() {
                    if (isSyncing) return
                    val configuredCodes = ImageSyncManager.WEATHER_BG_CODES.filter { (code, _) ->
                        ImageSyncManager.getImagePath(code) != null
                    }
                    if (configuredCodes.isEmpty()) {
                        showClearConfirmDialog.value = true
                        return
                    }

                    val advancedMode = prefs.getBoolean("advanced_sync_mode", true)
                    val preambleSteps = mutableListOf(
                        SyncStep("连接手表"),
                        SyncStep("检查应用安装")
                    )
                    if (advancedMode) {
                        preambleSteps.add(SyncStep("启动应用并握手"))
                    }
                    preambleSteps.add(SyncStep("检查权限"))

                    syncSteps.clear()
                    syncSteps.addAll(preambleSteps)
                    // 为每张已配置的图片添加步骤
                    configuredCodes.forEach { (_, label) ->
                        syncSteps.add(SyncStep(label))
                    }
                    syncFinished = false
                    syncResultSummary = ""
                    syncSheetTitle = "同步自定义背景图"
                    showSyncSheet.value = true
                }

                fun startSync() {
                    if (isSyncing) return
                    val configuredCodes = ImageSyncManager.WEATHER_BG_CODES.filter { (code, _) ->
                        ImageSyncManager.getImagePath(code) != null
                    }
                    isSyncing = true
                    val advancedMode = prefs.getBoolean("advanced_sync_mode", true)
                    val preambleCount = if (advancedMode) 4 else 3 // 连接 + 安装 + [握手] + 权限

                    syncJob = scope.launch {
                        var stepIdx = 0
                        try {
                            // Step: 连接
                            syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.IN_PROGRESS)
                            val nodes = withTimeout(10_000) { getConnectedNodes(nodeApi) }
                            val node = nodes.firstOrNull()
                            if (node == null) {
                                syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.ERROR)
                                syncResultSummary = "未连接手表"
                                syncFinished = true; isSyncing = false; return@launch
                            }
                            syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.DONE)
                            stepIdx++

                            // Step: 检查应用安装
                            syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.IN_PROGRESS)
                            val isInstalled = try {
                                checkWatchAppInstalled(nodeApi, node.id)
                            } catch (e: Exception) {
                                val msg = e.message ?: ""
                                if (msg.contains("permission denied", ignoreCase = true)) {
                                    syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.ERROR)
                                    syncResultSummary = "权限不足"
                                    syncFinished = true; isSyncing = false
                                    showPermissionDeniedDialog.value = true
                                    return@launch
                                } else {
                                    throw e
                                }
                            }
                            if (!isInstalled) {
                                syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.ERROR)
                                syncResultSummary = "手表端未安装应用，请先安装"
                                syncFinished = true; isSyncing = false; return@launch
                            }
                            syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.DONE)
                            stepIdx++

                            // Step: 握手 (if advanced)
                            if (advancedMode) {
                                syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.IN_PROGRESS)
                                try {
                                    performWatchHandshake(nodeApi, messageApi, node.id)
                                    syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.DONE)
                                } catch (e: Exception) {
                                    syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.ERROR)
                                    syncResultSummary = "握手失败: ${e.message}"
                                    syncFinished = true; isSyncing = false; return@launch
                                }
                                stepIdx++
                            }

                            // Step: 权限
                            syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.IN_PROGRESS)
                            val granted = suspendCancellableCoroutine { cont ->
                                checkAndRequestPermission(authApi, node.id) { cont.resume(it) }
                            }
                            if (!granted) {
                                syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.ERROR)
                                syncResultSummary = "权限被拒绝"
                                syncFinished = true; isSyncing = false; return@launch
                            }
                            syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.DONE)
                            stepIdx++

                            // 逐图同步
                            val result = ImageSyncManager.syncAllImages(
                                context, messageApi, node.id,
                                onProgress = { current, _, _ ->
                                    val idx = preambleCount + current - 1
                                    if (idx < syncSteps.size) {
                                        syncSteps[idx] = syncSteps[idx].copy(status = StepStatus.IN_PROGRESS)
                                    }
                                    syncProgress = 0f
                                    syncProgressText = ""
                                },
                                onImageSent = { code, success ->
                                    val idx = preambleCount + configuredCodes.indexOfFirst { it.first == code }
                                    if (idx in preambleCount until syncSteps.size) {
                                        syncSteps[idx] = syncSteps[idx].copy(
                                            status = if (success) StepStatus.DONE else StepStatus.ERROR
                                        )
                                    }
                                    syncProgress = 0f
                                    syncProgressText = ""
                                },
                                onChunkProgress = { sent, totalChunks ->
                                    syncProgress = sent.toFloat() / totalChunks
                                    syncProgressText = "$sent/$totalChunks"
                                }
                            )
                            if (result.isSuccess) {
                                syncResultSummary = "成功发送 ${result.getOrDefault(0)} 张背景图"
                            } else {
                                syncResultSummary = result.exceptionOrNull()?.message ?: "未知错误"
                            }
                            syncFinished = true; isSyncing = false
                        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                            syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.ERROR)
                            syncResultSummary = "连接手表超时"
                            syncFinished = true; isSyncing = false
                        } catch (e: Exception) {
                            syncSteps[stepIdx] = syncSteps[stepIdx].copy(status = StepStatus.ERROR)
                            syncResultSummary = e.message ?: "未知错误"
                            syncFinished = true; isSyncing = false
                        }
                    }
                }

                fun performExport() {
                    if (isSyncing) return
                    val count = imagePaths.values.count { it != null }
                    if (count == 0) {
                        resultTitle = "无法导出"
                        resultSummary = "没有可导出的背景图"
                        showResultDialog = true
                        return
                    }
                    exportLauncher.launch("weather_backgrounds${BackgroundPresetManager.FILE_EXTENSION}")
                }

                fun performShare() {
                    if (isSyncing) return
                    val count = imagePaths.values.count { it != null }
                    if (count == 0) {
                        resultTitle = "无法分享"
                        resultSummary = "没有可分享的背景图"
                        showResultDialog = true
                        return
                    }
                    scope.launch {
                        loadingMessage = "正在准备分享"
                        showLoadingDialog = true
                        val result = BackgroundPresetManager.prepareShareFile(context)
                        showLoadingDialog = false
                        if (result.isSuccess) {
                            val file = result.getOrThrow()
                            val uri = com.application.zaona.weather.service.FileProviderHelper.getUriForFile(context, file)
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = BackgroundPresetManager.MIME_TYPE
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "分享背景预设包"))
                        } else {
                            resultTitle = "分享失败"
                            resultSummary = result.exceptionOrNull()?.message ?: "未知错误"
                            showResultDialog = true
                        }
                    }
                }

                fun performConfirmedImport() {
                    val uri = pendingImportUri ?: return
                    scope.launch {
                        loadingMessage = if (isBrzImport) "正在转换 BRZ 预设包" else "正在导入预设包"
                        showLoadingDialog = true
                        val importUri: Uri
                        if (isBrzImport) {
                            val converted = BrzAdapter.convertToSwbg(context, uri)
                            if (converted.isFailure) {
                                showLoadingDialog = false
                                resultTitle = "导入失败"
                                resultSummary = "转换失败: ${converted.exceptionOrNull()?.message ?: "未知错误"}"
                                showResultDialog = true
                                pendingImportUri = null
                                isBrzImport = false
                                return@launch
                            }
                            loadingMessage = "正在导入预设包"
                            importUri = Uri.fromFile(converted.getOrThrow())
                        } else {
                            importUri = uri
                        }

                        val result = BackgroundPresetManager.performImport(context, importUri)
                        showLoadingDialog = false
                        if (result.isSuccess) {
                            val count = result.getOrDefault(0)
                            // 刷新 imagePaths
                            ImageSyncManager.WEATHER_BG_CODES.forEach { (code, _) ->
                                imagePaths[code] = ImageSyncManager.getImagePath(code)
                            }
                            // 刷新滑块：SWBG 导入会覆盖处理参数
                            darkenStrength = prefs.getInt("bg_darken_strength", 0)
                            blurRadius = prefs.getInt("bg_blur_radius", 0)
                            quality = prefs.getInt("bg_quality", 10)
                            darkenPreview = darkenStrength
                            blurPreview = blurRadius
                            resultTitle = "导入成功"
                            resultSummary = "已导入 $count 张背景图"
                            showResultDialog = true
                        } else {
                            resultTitle = "导入失败"
                            resultSummary = result.exceptionOrNull()?.message ?: "未知错误"
                            showResultDialog = true
                        }
                        pendingImportUri = null
                        isBrzImport = false
                    }
                }

                Scaffold(
                    contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
                    topBar = {
                        TopAppBar(
                            title = "自定义背景图",
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(MiuixIcons.Back, contentDescription = "返回")
                                }
                            },
                            actions = {
                                IconButton(onClick = { showHelpSheet.value = true }) {
                                    Icon(MiuixIcons.Help, contentDescription = "帮助")
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                OverlayIconCascadingDropdownMenu(entry = DropdownEntry(
                                    items = listOf(
                                        DropdownItem(
                                            text = "导入",
                                            icon = { modifier -> Icon(MiuixIcons.Add, contentDescription = null, modifier = modifier) },
                                            onClick = { importLauncher.launch(arrayOf("*/*")) }
                                        ),
                                        DropdownItem(
                                            text = "导出",
                                            icon = { modifier -> Icon(MiuixIcons.Backup, contentDescription = null, modifier = modifier) },
                                            onClick = { performExport() }
                                        ),
                                        DropdownItem(
                                            text = "分享",
                                            icon = { modifier -> Icon(MiuixIcons.Share, contentDescription = null, modifier = modifier) },
                                            onClick = { performShare() }
                                        ),
                                    )
                                )) {
                                    Icon(MiuixIcons.MoreCircle, contentDescription = "菜单")
                                }
                            },
                            scrollBehavior = scrollBehavior
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { performSync() },
                            modifier = Modifier.padding(end = 20.dp, bottom = 20.dp)
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Send,
                                contentDescription = "同步到手表",
                                tint = Color.White
                            )
                        }
                    },
                    floatingActionButtonPosition = FabPosition.End
                ) { padding ->
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .overScrollVertical()
                            .scrollEndHaptic()
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                    ) {
                        // 图片处理
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            SmallTitle(text = "图片处理")
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    // 压暗滑块
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            MiuixIcons.Demibold.Background,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "压暗",
                                            style = MiuixTheme.textStyles.body1
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Slider(
                                        value = darkenStrength.toFloat(),
                                        onValueChange = { darkenStrength = it.toInt() },
                                        onValueChangeFinished = {
                                            prefs.edit().putInt("bg_darken_strength", darkenStrength).apply()
                                            darkenPreview = darkenStrength
                                        },
                                        valueRange = 0f..100f
                                    )

                                    Spacer(modifier = Modifier.height(24.dp))

                                    // 模糊滑块
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            MiuixIcons.Demibold.Create,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "模糊",
                                            style = MiuixTheme.textStyles.body1
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Slider(
                                        value = blurRadius.toFloat(),
                                        onValueChange = { blurRadius = it.toInt() },
                                        onValueChangeFinished = {
                                            prefs.edit().putInt("bg_blur_radius", blurRadius).apply()
                                            blurPreview = blurRadius
                                        },
                                        valueRange = 0f..100f
                                    )

                                    Spacer(modifier = Modifier.height(24.dp))

                                    // 画质滑块
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            MiuixIcons.Demibold.Tune,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "画质",
                                            style = MiuixTheme.textStyles.body1
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Slider(
                                        value = quality.toFloat(),
                                        onValueChange = { quality = it.toInt() },
                                        onValueChangeFinished = {
                                            prefs.edit().putInt("bg_quality", quality).apply()
                                        },
                                        valueRange = 10f..50f
                                    )
                                }
                            }
                        }

                        item {
                            SmallTitle(text = "已配置 $configuredCount/12 张背景图")
                        }

                        ImageSyncManager.WEATHER_BG_CODES.forEach { (code, label) ->
                            item {
                                val imagePath = imagePaths[code]
                                BackgroundImageItem(
                                    code = code,
                                    label = label,
                                    imagePath = imagePath,
                                    onSelect = {
                                        selectedCode = code
                                        imagePickerLauncher.launch("image/*")
                                    },
                                    onClear = {
                                        ImageSyncManager.removeImagePath(code)
                                        imagePaths[code] = null
                                        thumbnailCache.remove(code)
                                    },
                                    darkenStrength = darkenPreview,
                                    blurRadius = blurPreview,
                                    thumbnailCache = thumbnailCache
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(96.dp))
                        }
                    }

                    // 同步/清除步骤 BottomSheet
                    OverlayBottomSheet(
                        show = showSyncSheet.value,
                        title = syncSheetTitle,
                        allowDismiss = syncFinished || !isSyncing,
                        onDismissRequest = { if (syncFinished || !isSyncing) showSyncSheet.value = false },
                        enableNestedScroll = false,
                        insideMargin = DpSize.Zero
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 108.dp)
                            ) {
                            Text(
                                text = "发送过程中请不要操作手表",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                            )
                            syncSteps.forEach { step ->
                                val showProgress = step.status == StepStatus.IN_PROGRESS && syncProgress > 0f && !syncFinished
                                BasicComponent(
                                    title = step.label,
                                    summary = if (showProgress) "传输中 $syncProgressText" else null,
                                    startAction = {
                                        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                            when {
                                                step.status == StepStatus.IN_PROGRESS && showProgress -> CircularProgressIndicator(
                                                    progress = syncProgress,
                                                    size = 22.dp,
                                                    strokeWidth = 3.dp,
                                                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                                        foregroundColor = MiuixTheme.colorScheme.primary
                                                    )
                                                )
                                                step.status == StepStatus.DONE -> Icon(
                                                    imageVector = MiuixIcons.Basic.Check,
                                                    contentDescription = "完成",
                                                    tint = MiuixTheme.colorScheme.primary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                step.status == StepStatus.ERROR -> Icon(
                                                    imageVector = MiuixIcons.Demibold.Close2,
                                                    contentDescription = "错误",
                                                    tint = MiuixTheme.colorScheme.error,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                else -> CircularProgressIndicator(
                                                    progress = 0f,
                                                    size = 22.dp,
                                                    strokeWidth = 3.dp,
                                                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                                        foregroundColor = MiuixTheme.colorScheme.disabledSecondary
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    insideMargin = PaddingValues(0.dp),
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }

                            if (syncFinished && syncResultSummary.isNotEmpty()) {
                                BasicComponent(
                                    title = syncResultSummary,
                                    startAction = {
                                        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = MiuixIcons.Demibold.Backup,
                                                contentDescription = null,
                                                tint = MiuixTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    },
                                    insideMargin = PaddingValues(0.dp),
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }

                        // 底部渐变叠层
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(100.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MiuixTheme.colorScheme.background
                                        )
                                    )
                                )
                        )

                        // 底部固定按钮
                        if (syncFinished) {
                            TextButton(
                                text = "完成",
                                onClick = { showSyncSheet.value = false },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 28.dp, vertical = 28.dp),
                                colors = ButtonDefaults.textButtonColorsPrimary()
                            )
                        } else if (isSyncing) {
                            TextButton(
                                text = "取消",
                                onClick = {
                                    syncJob?.cancel()
                                    val idx = syncSteps.indexOfFirst { it.status == StepStatus.IN_PROGRESS }
                                    if (idx >= 0) syncSteps[idx] = syncSteps[idx].copy(status = StepStatus.ERROR)
                                    syncResultSummary = "已取消"
                                    syncFinished = true
                                    isSyncing = false
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 28.dp, vertical = 28.dp)
                            )
                        } else {
                            TextButton(
                                text = if (syncSheetTitle.contains("清除")) "开始清除" else "开始同步",
                                onClick = {
                                    if (syncSheetTitle.contains("清除")) startClear() else startSync()
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 28.dp, vertical = 28.dp),
                                colors = ButtonDefaults.textButtonColorsPrimary()
                            )
                        }
                        } // closes Box
                    }

                    // 帮助指南 BottomSheet
                    OverlayBottomSheet(
                        show = showHelpSheet.value,
                        title = "自定义背景图指南",
                        onDismissRequest = { showHelpSheet.value = false },
                        enableNestedScroll = false
                    ) {
                        MarkdownText(
                            markdown = """
## 选择背景图
点击每种天气类型右侧的 + 按钮，从相册中选择一张图片作为该天气的自定义背景。支持 12 种天气类型，每种可单独设置。

## 同步到手表
配置好背景图后，点击右下角的发送按钮即可将所有背景图同步到手表端。同步过程中请不要操作手表。

## 导入 / 导出预设包
点击右上角菜单可导入或导出 `.swbg` 格式的预设包，方便备份和分享。也支持从「微风天气」导入 `.brz` 格式的预设。

## 分享预设包
将当前所有背景配置打包分享给其他人，对方可直接导入使用。

## 图片处理参数
- **压暗**：调整背景图亮度，数值越大越暗。
- **模糊**：对背景图应用高斯模糊效果。
- **画质**：控制同步到手表时的图片质量，数值越高画质越好但传输更慢。
调节滑块后可实时预览效果。

## 清除背景图
若所有天气都未选图，点击同步按钮会弹出清除确认，可将手表端已存储的自定义背景图全部清除，恢复默认背景。
                            """.trimIndent()
                        )
                    }

                    // 加载中弹窗
                    OverlayDialog(
                        title = loadingMessage,
                        summary = "请稍候...",
                        show = showLoadingDialog,
                        onDismissRequest = { /* 阻塞关闭 */ }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            InfiniteProgressIndicator(
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    // 操作结果弹窗
                    OverlayDialog(
                        title = resultTitle,
                        summary = resultSummary,
                        show = showResultDialog,
                        onDismissRequest = { showResultDialog = false }
                    ) {
                        TextButton(
                            text = "确定",
                            onClick = { showResultDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 权限不足弹窗
                    WindowDialog(
                        title = "权限不足",
                        summary = null,
                        show = showPermissionDeniedDialog.value,
                        onDismissRequest = { showPermissionDeniedDialog.value = false }
                    ) {
                        Text(
                            text = """
                                1. 若手机已解锁BL，请将小米运动健康和本同步器隐藏
                                2. 打开小米运动健康→我的→设备授权管理→简明天气→确保两个权限开关都处于开启状态
                            """.trimIndent(),
                            modifier = Modifier.fillMaxWidth(),
                            fontSize = MiuixTheme.textStyles.body1.fontSize,
                            textAlign = TextAlign.Start,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            TextButton(
                                text = "确定",
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { showPermissionDeniedDialog.value = false }
                            )
                        }
                    }

                    // 导入确认弹窗
                    OverlayDialog(
                        title = importConfirmTitle,
                        summary = importConfirmSummary,
                        show = showImportConfirmDialog.value,
                        onDismissRequest = {
                            showImportConfirmDialog.value = false
                            pendingImportUri = null
                            isBrzImport = false
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TextButton(
                                text = "取消",
                                onClick = {
                                    showImportConfirmDialog.value = false
                                    pendingImportUri = null
                                    isBrzImport = false
                                },
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                            TextButton(
                                text = "确认",
                                onClick = {
                                    showImportConfirmDialog.value = false
                                    performConfirmedImport()
                                },
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColorsPrimary()
                            )
                        }
                    }

                    // 空配置确认清除弹窗
                    OverlayDialog(
                        title = "确认清除",
                        summary = "当前没有选择任何背景图，继续同步将会删除手表上已存储的所有自定义背景图。确定继续吗？",
                        show = showClearConfirmDialog.value,
                        onDismissRequest = { showClearConfirmDialog.value = false }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TextButton(
                                text = "取消",
                                onClick = { showClearConfirmDialog.value = false },
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                text = "确认",
                                onClick = {
                                    showClearConfirmDialog.value = false
                                    performClear()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.textButtonColors(
                                    textColor = MiuixTheme.colorScheme.error
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermission(authApi: AuthApi, nodeId: String, callback: (Boolean) -> Unit) {
        val permissions = arrayOf(Permission.DEVICE_MANAGER, Permission.NOTIFY)
        authApi.checkPermissions(nodeId, permissions)
            .addOnSuccessListener { results ->
                val allGranted = results.all { it }
                if (allGranted) {
                    callback(true)
                } else {
                    authApi.requestPermission(nodeId, Permission.DEVICE_MANAGER, Permission.NOTIFY)
                        .addOnSuccessListener { grantedPermissions ->
                            val hasDeviceManager = grantedPermissions.any { it == Permission.DEVICE_MANAGER }
                            callback(hasDeviceManager)
                        }
                        .addOnFailureListener {
                            callback(false)
                        }
                }
            }
            .addOnFailureListener {
                authApi.requestPermission(nodeId, Permission.DEVICE_MANAGER, Permission.NOTIFY)
                    .addOnSuccessListener { grantedPermissions ->
                        val hasDeviceManager = grantedPermissions.any { it == Permission.DEVICE_MANAGER }
                        callback(hasDeviceManager)
                    }
                    .addOnFailureListener {
                        callback(false)
                    }
            }
    }

    private suspend fun getConnectedNodes(nodeApi: NodeApi): List<Node> =
        suspendCancellableCoroutine { cont ->
            nodeApi.connectedNodes
                .addOnSuccessListener { nodes -> cont.resume(nodes) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }

    private suspend fun checkWatchAppInstalled(nodeApi: NodeApi, nodeId: String): Boolean =
        suspendCancellableCoroutine { cont ->
            nodeApi.isWearAppInstalled(nodeId)
                .addOnSuccessListener { isInstalled -> cont.resume(isInstalled) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }

    private suspend fun performWatchHandshake(
        nodeApi: NodeApi,
        messageApi: MessageApi,
        nodeId: String
    ) {
        var isReady = false
        val listener = OnMessageReceivedListener { _, message ->
            if (String(message).contains("ready")) {
                isReady = true
            }
        }

        try {
            messageApi.addListener(nodeId, listener)
            nodeApi.launchWearApp(nodeId, "/home")

            var attempts = 0
            while (attempts < 5 && !isReady) {
                attempts++
                messageApi.sendMessage(nodeId, "start".toByteArray(Charsets.UTF_8))

                repeat(20) {
                    if (!isReady) Thread.sleep(50)
                }
            }
        } finally {
            messageApi.removeListener(nodeId)
        }

        if (!isReady) {
            throw Exception("握手失败：设备未响应")
        }
    }
}

@Composable
private fun BackgroundImageItem(
    code: String,
    label: String,
    imagePath: String?,
    onSelect: () -> Unit,
    onClear: () -> Unit,
    darkenStrength: Int = 0,
    blurRadius: Int = 0,
    thumbnailCache: MutableMap<String, ImageBitmap?>
) {
    val hasImage = imagePath != null
    val context = LocalContext.current
    val thumbnail = thumbnailCache[code]

    val fileName = remember(imagePath) {
        if (imagePath == null) null
        else ImageSyncManager.getImageFileName(code) ?: "自定义图片"
    }

    LaunchedEffect(imagePath, darkenStrength, blurRadius) {
        if (imagePath == null) {
            thumbnailCache.remove(code)
            return@LaunchedEffect
        }
        val bmp = withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(imagePath)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    val bmp = BitmapFactory.decodeStream(stream, null, opts) ?: return@withContext null
                    var processed = bmp
                    if (blurRadius > 0) processed = ImageProcessingUtil.applyBlur(processed, blurRadius)
                    if (darkenStrength > 0) processed = ImageProcessingUtil.applyDarken(processed, darkenStrength)
                    processed.asImageBitmap()
                }
            } catch (_: Exception) { null }
        }
        thumbnailCache[code] = bmp
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
    ) {
        BasicComponent(
            title = label,
            summary = fileName ?: "使用默认背景",
            startAction = {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MiuixTheme.colorScheme.disabledSecondary),
                    contentAlignment = Alignment.Center
                ) {
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail,
                            contentDescription = label,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (!hasImage) {
                        Icon(
                            imageVector = MiuixIcons.Backup,
                            contentDescription = "默认",
                            tint = Color(0xFFBBBBBB),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            },
            endActions = {
                if (hasImage) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = MiuixIcons.Delete,
                            contentDescription = "清除",
                            tint = MiuixTheme.colorScheme.error
                        )
                    }
                } else {
                    IconButton(onClick = onSelect) {
                        Icon(
                            imageVector = MiuixIcons.Add,
                            contentDescription = "选择"
                        )
                    }
                }
            }
        )
    }
}