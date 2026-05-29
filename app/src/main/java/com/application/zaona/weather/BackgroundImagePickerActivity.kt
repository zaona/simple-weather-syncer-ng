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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.application.zaona.weather.service.ImageSyncManager
import com.application.zaona.weather.ui.theme.SimpleweathersyncerngTheme
import com.xiaomi.xms.wearable.Wearable
import com.xiaomi.xms.wearable.auth.AuthApi
import com.xiaomi.xms.wearable.auth.Permission
import com.xiaomi.xms.wearable.message.MessageApi
import com.xiaomi.xms.wearable.message.OnMessageReceivedListener
import com.xiaomi.xms.wearable.node.NodeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FabPosition
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

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

                val nodeApi = remember { Wearable.getNodeApi(context.applicationContext) }
                val messageApi = remember { Wearable.getMessageApi(context.applicationContext) }
                val authApi = remember { Wearable.getAuthApi(context.applicationContext) }

                val imagePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    if (uri != null && selectedCode != null) {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        val code = selectedCode!!
                        val path = uri.toString()
                        ImageSyncManager.setImagePath(code, path)
                        imagePaths[code] = path
                    }
                }

                val showLoadingDialog = remember { mutableStateOf(false) }
                var loadingDialogTitle by remember { mutableStateOf("") }
                var loadingDialogSummary by remember { mutableStateOf("") }
                val showMessageDialog = remember { mutableStateOf(false) }
                var messageDialogTitle by remember { mutableStateOf("") }
                var messageDialogSummary by remember { mutableStateOf("") }

                fun performSync() {
                    if (isSyncing) return
                    isSyncing = true
                    showLoadingDialog.value = true
                    loadingDialogTitle = "正在同步"
                    loadingDialogSummary = "正在连接到手表..."

                    nodeApi.connectedNodes.addOnSuccessListener { nodes ->
                        val node = nodes.firstOrNull()
                        if (node == null) {
                            showLoadingDialog.value = false
                            isSyncing = false
                            Toast.makeText(context, "未连接手表", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }

                        val permissions = arrayOf(Permission.DEVICE_MANAGER, Permission.NOTIFY)
                        authApi.checkPermissions(node.id, permissions).addOnSuccessListener { results ->
                            if (!results.all { it }) {
                                authApi.requestPermission(node.id, *permissions)
                            }

                            scope.launch {
                                try {
                                    val count = imagePaths.values.count { it != null }
                                    if (count == 0) {
                                        showLoadingDialog.value = false
                                        isSyncing = false
                                        Toast.makeText(context, "请先选择自定义背景图", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }

                                    loadingDialogSummary = "正在检查手表应用安装状态..."
                                    val isInstalled = checkWatchAppInstalled(nodeApi, node.id)
                                    if (!isInstalled) {
                                        showLoadingDialog.value = false
                                        isSyncing = false
                                        Toast.makeText(context, "手表端未安装应用，请先安装", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }

                                    val prefs = context.getSharedPreferences("weather_prefs", android.content.Context.MODE_PRIVATE)
                                    val advancedSyncMode = prefs.getBoolean("advanced_sync_mode", true)
                                    if (advancedSyncMode) {
                                        loadingDialogSummary = "正在启动应用并握手..."
                                        performWatchHandshake(nodeApi, messageApi, node.id)
                                        delay(160)
                                    }

                                    val result = ImageSyncManager.syncAllImages(context, messageApi, node.id) { current, total, code ->
                                        val label = ImageSyncManager.WEATHER_BG_CODES.find { it.first == code }?.second ?: code
                                        loadingDialogSummary = "正在发送 ($current/$total): $label"
                                    }
                                    showLoadingDialog.value = false
                                    isSyncing = false
                                    if (result.isSuccess) {
                                        messageDialogTitle = "同步完成"
                                        messageDialogSummary = "成功发送 ${result.getOrDefault(0)} 张背景图"
                                    } else {
                                        messageDialogTitle = "同步失败"
                                        messageDialogSummary = result.exceptionOrNull()?.message ?: "未知错误"
                                    }
                                    showMessageDialog.value = true
                                } catch (e: Exception) {
                                    showLoadingDialog.value = false
                                    isSyncing = false
                                    messageDialogTitle = "同步失败"
                                    messageDialogSummary = e.message ?: "未知错误"
                                    showMessageDialog.value = true
                                }
                            }
                        }.addOnFailureListener {
                            showLoadingDialog.value = false
                            isSyncing = false
                            Toast.makeText(context, "权限检查失败", Toast.LENGTH_SHORT).show()
                        }
                    }.addOnFailureListener {
                        showLoadingDialog.value = false
                        isSyncing = false
                        Toast.makeText(context, "获取设备失败", Toast.LENGTH_SHORT).show()
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
                                    }
                                )
                            }
                        }
                    }

                    OverlayDialog(
                        title = loadingDialogTitle,
                        summary = loadingDialogSummary,
                        show = showLoadingDialog.value,
                        onDismissRequest = { }
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

                    OverlayDialog(
                        title = messageDialogTitle,
                        summary = messageDialogSummary,
                        show = showMessageDialog.value,
                        onDismissRequest = { showMessageDialog.value = false }
                    ) {
                        TextButton(
                            text = "确定",
                            onClick = { showMessageDialog.value = false },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
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
                for (i in 0 until 20) {
                    delay(50)
                    if (isReady) break
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
    onClear: () -> Unit
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

    LaunchedEffect(imagePath) {
        if (imagePath == null) {
            thumbnail = null
            return@LaunchedEffect
        }
        thumbnail = withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(imagePath)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeStream(stream, null, opts)?.asImageBitmap()
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
                    } else if (hasImage) {
                        Text(
                            text = "...",
                            color = Color(0xFF4CAF50)
                        )
                    } else {
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
