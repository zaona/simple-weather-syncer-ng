package com.application.zaona.weather

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import com.application.zaona.weather.service.ImageSyncManager
import com.application.zaona.weather.ui.theme.SimpleweathersyncerngTheme
import com.application.zaona.weather.util.ImageProcessingUtil
import com.xiaomi.xms.wearable.Wearable
import com.xiaomi.xms.wearable.auth.AuthApi
import com.xiaomi.xms.wearable.auth.Permission
import com.xiaomi.xms.wearable.message.MessageApi
import com.xiaomi.xms.wearable.node.Node
import com.xiaomi.xms.wearable.node.NodeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Create
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Close2
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Send
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
                val configuredCount = imagePaths.values.count { it != null }

                val prefs = remember { context.getSharedPreferences("weather_prefs", android.content.Context.MODE_PRIVATE) }
                var darkenStrength by remember { mutableStateOf(prefs.getInt("bg_darken_strength", 0)) }
                var blurRadius by remember { mutableStateOf(prefs.getInt("bg_blur_radius", 0)) }
                var quality by remember { mutableStateOf(prefs.getInt("bg_quality", 100)) }

                val nodeApi = remember { Wearable.getNodeApi(context.applicationContext) }
                val messageApi = remember { Wearable.getMessageApi(context.applicationContext) }
                val authApi = remember { Wearable.getAuthApi(context.applicationContext) }

                val imagePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    if (uri != null && selectedCode != null) {
                        try {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (_: SecurityException) {
                            // 部分设备不支持持久化权限，忽略错误继续
                        }
                        val code = selectedCode!!
                        val path = uri.toString()
                        ImageSyncManager.setImagePath(code, path)
                        imagePaths[code] = path
                    }
                }

                val showSyncSheet = remember { mutableStateOf(false) }
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
                    isSyncing = true
                    syncSteps.clear()
                    syncSteps.addAll(listOf(
                        SyncStep("连接手表"),
                        SyncStep("检查权限"),
                        SyncStep("清除自定义背景图")
                    ))
                    syncFinished = false
                    syncResultSummary = ""
                    syncSheetTitle = "正在清除"
                    showSyncSheet.value = true

                    syncJob = scope.launch {
                        // Step 1: 连接
                        syncSteps[0] = syncSteps[0].copy(status = StepStatus.IN_PROGRESS)
                        try {
                            val nodes = withTimeout(10_000) { getConnectedNodes(nodeApi) }
                            val node = nodes.firstOrNull()
                            if (node == null) {
                                syncSteps[0] = syncSteps[0].copy(status = StepStatus.ERROR)
                                syncResultSummary = "未连接手表"
                                syncFinished = true; isSyncing = false; return@launch
                            }
                            syncSteps[0] = syncSteps[0].copy(status = StepStatus.DONE)

                            // Step 2: 权限
                            syncSteps[1] = syncSteps[1].copy(status = StepStatus.IN_PROGRESS)
                            val granted = suspendCancellableCoroutine { cont ->
                                checkAndRequestPermission(authApi, node.id) { cont.resume(it) }
                            }
                            if (!granted) {
                                syncSteps[1] = syncSteps[1].copy(status = StepStatus.ERROR)
                                syncResultSummary = "权限被拒绝"
                                syncFinished = true; isSyncing = false; return@launch
                            }
                            syncSteps[1] = syncSteps[1].copy(status = StepStatus.DONE)

                            // Step 3: 清除
                            syncSteps[2] = syncSteps[2].copy(status = StepStatus.IN_PROGRESS)
                            val result = ImageSyncManager.clearAllOnWatch(messageApi, node.id)
                            if (result.isSuccess) {
                                syncSteps[2] = syncSteps[2].copy(status = StepStatus.DONE)
                                syncResultSummary = "手表端自定义背景图已全部清除"
                            } else {
                                syncSteps[2] = syncSteps[2].copy(status = StepStatus.ERROR)
                                syncResultSummary = result.exceptionOrNull()?.message ?: "未知错误"
                            }
                            syncFinished = true; isSyncing = false
                        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                            syncSteps[0] = syncSteps[0].copy(status = StepStatus.ERROR)
                            syncResultSummary = "连接手表超时"
                            syncFinished = true; isSyncing = false
                        } catch (e: Exception) {
                            val idx = syncSteps.indexOfFirst { it.status == StepStatus.IN_PROGRESS }
                            if (idx >= 0) syncSteps[idx] = syncSteps[idx].copy(status = StepStatus.ERROR)
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

                    isSyncing = true
                    syncSteps.clear()
                    syncSteps.addAll(listOf(
                        SyncStep("连接手表"),
                        SyncStep("检查权限")
                    ))
                    // 为每张已配置的图片添加步骤
                    configuredCodes.forEach { (_, label) ->
                        syncSteps.add(SyncStep(label))
                    }
                    syncFinished = false
                    syncResultSummary = ""
                    syncSheetTitle = "同步自定义背景图"
                    showSyncSheet.value = true
                    val fixedSteps = 2  // 连接 + 权限

                    syncJob = scope.launch {
                        // Step 1: 连接
                        syncSteps[0] = syncSteps[0].copy(status = StepStatus.IN_PROGRESS)
                        try {
                            val nodes = withTimeout(10_000) { getConnectedNodes(nodeApi) }
                            val node = nodes.firstOrNull()
                            if (node == null) {
                                syncSteps[0] = syncSteps[0].copy(status = StepStatus.ERROR)
                                syncResultSummary = "未连接手表"
                                syncFinished = true; isSyncing = false; return@launch
                            }
                            syncSteps[0] = syncSteps[0].copy(status = StepStatus.DONE)

                            // Step 2: 权限
                            syncSteps[1] = syncSteps[1].copy(status = StepStatus.IN_PROGRESS)
                            val granted = suspendCancellableCoroutine { cont ->
                                checkAndRequestPermission(authApi, node.id) { cont.resume(it) }
                            }
                            if (!granted) {
                                syncSteps[1] = syncSteps[1].copy(status = StepStatus.ERROR)
                                syncResultSummary = "权限被拒绝"
                                syncFinished = true; isSyncing = false; return@launch
                            }
                            syncSteps[1] = syncSteps[1].copy(status = StepStatus.DONE)

                            // Step 3+: 逐图同步
                            val result = ImageSyncManager.syncAllImages(
                                context, messageApi, node.id,
                                onProgress = { current, _, _ ->
                                    val idx = fixedSteps + current - 1
                                    if (idx < syncSteps.size) {
                                        syncSteps[idx] = syncSteps[idx].copy(status = StepStatus.IN_PROGRESS)
                                    }
                                    syncProgress = 0f
                                    syncProgressText = ""
                                },
                                onImageSent = { code, success ->
                                    val idx = fixedSteps + configuredCodes.indexOfFirst { it.first == code }
                                    if (idx in fixedSteps until syncSteps.size) {
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
                            syncSteps[0] = syncSteps[0].copy(status = StepStatus.ERROR)
                            syncResultSummary = "连接手表超时"
                            syncFinished = true; isSyncing = false
                        } catch (e: Exception) {
                            val idx = syncSteps.indexOfFirst { it.status == StepStatus.IN_PROGRESS }
                            if (idx >= 0) syncSteps[idx] = syncSteps[idx].copy(status = StepStatus.ERROR)
                            syncResultSummary = e.message ?: "未知错误"
                            syncFinished = true; isSyncing = false
                        }
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
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            SmallTitle(text = "已配置 $configuredCount/12 张背景图")
                        }

                        // 图片处理卡片
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    // 压暗滑块
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            MiuixIcons.Heavy.Background,
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
                                        },
                                        valueRange = 0f..100f
                                    )

                                    Spacer(modifier = Modifier.height(20.dp))

                                    // 模糊滑块
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            MiuixIcons.Heavy.Create,
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
                                        },
                                        valueRange = 0f..25f
                                    )

                                    Spacer(modifier = Modifier.height(20.dp))

                                    // 画质滑块
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            MiuixIcons.Heavy.Tune,
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
                                        valueRange = 10f..100f
                                    )
                                }
                            }
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
                                    },
                                    darkenStrength = darkenStrength,
                                    blurRadius = blurRadius
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(72.dp))
                        }
                    }

                    // 同步/清除步骤 BottomSheet
                    OverlayBottomSheet(
                        show = showSyncSheet.value,
                        title = syncSheetTitle,
                        allowDismiss = syncFinished,
                        onDismissRequest = { if (syncFinished) showSyncSheet.value = false }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 32.dp)
                        ) {
                            if (!syncFinished) {
                                Text(
                                    text = "发送过程中请不要操作手表",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                                )
                            }
                            syncSteps.forEach { step ->
                                val showProgress = step.status == StepStatus.IN_PROGRESS && syncProgress > 0f && !syncFinished
                                BasicComponent(
                                    title = step.label,
                                    summary = if (showProgress) "传输中 $syncProgressText" else null,
                                    startAction = {
                                        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                            when {
                                                step.status == StepStatus.IN_PROGRESS -> CircularProgressIndicator(
                                                    progress = if (showProgress) syncProgress else null,
                                                    size = 22.dp,
                                                    strokeWidth = 3.dp,
                                                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                                        foregroundColor = MiuixTheme.colorScheme.primary
                                                    )
                                                )
                                                step.status == StepStatus.PENDING -> CircularProgressIndicator(
                                                    progress = 0f,
                                                    size = 22.dp,
                                                    strokeWidth = 3.dp,
                                                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                                        foregroundColor = MiuixTheme.colorScheme.disabledSecondary
                                                    )
                                                )
                                                step.status == StepStatus.DONE -> Icon(
                                                    imageVector = MiuixIcons.Basic.Check,
                                                    contentDescription = "完成",
                                                    tint = MiuixTheme.colorScheme.primary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                step.status == StepStatus.ERROR -> Icon(
                                                    imageVector = MiuixIcons.Heavy.Close2,
                                                    contentDescription = "错误",
                                                    tint = MiuixTheme.colorScheme.error,
                                                    modifier = Modifier.size(22.dp)
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
                                                imageVector = MiuixIcons.Heavy.Backup,
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

                            Spacer(modifier = Modifier.height(12.dp))
                            if (syncFinished) {
                                TextButton(
                                    text = "完成",
                                    onClick = { showSyncSheet.value = false },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.textButtonColorsPrimary()
                                )
                            } else {
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
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
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
}

@Composable
private fun BackgroundImageItem(
    code: String,
    label: String,
    imagePath: String?,
    onSelect: () -> Unit,
    onClear: () -> Unit,
    darkenStrength: Int = 0,
    blurRadius: Int = 0
) {
    val hasImage = imagePath != null
    val context = LocalContext.current
    var thumbnail by remember(imagePath) { mutableStateOf<ImageBitmap?>(null) }

    val fileName = remember(imagePath) {
        if (imagePath == null) null
        else try {
            val uri = Uri.parse(imagePath)
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
            }
        } catch (_: Exception) { null }
    }

    LaunchedEffect(imagePath, darkenStrength, blurRadius) {
        if (imagePath == null) {
            thumbnail = null
            return@LaunchedEffect
        }
        thumbnail = withContext(Dispatchers.IO) {
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
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
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
                            bitmap = thumbnail!!,
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
