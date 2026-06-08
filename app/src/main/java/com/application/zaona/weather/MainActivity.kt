package com.application.zaona.weather

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import com.application.zaona.weather.model.CityLocation
import com.application.zaona.weather.service.UpdateService
import com.application.zaona.weather.service.WeatherService
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.application.zaona.weather.ui.effect.AboutCardBackground
import com.application.zaona.weather.ui.theme.SimpleweathersyncerngTheme
import com.xiaomi.xms.wearable.Wearable
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

import com.xiaomi.xms.wearable.message.OnMessageReceivedListener
import com.xiaomi.xms.wearable.message.MessageApi
import com.xiaomi.xms.wearable.node.NodeApi
import com.xiaomi.xms.wearable.auth.AuthApi
import com.xiaomi.xms.wearable.auth.Permission
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.microsoft.clarity.Clarity
import com.microsoft.clarity.ClarityConfig
import com.microsoft.clarity.models.LogLevel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope

import com.application.zaona.weather.service.DeviceReportService
import com.application.zaona.weather.service.ImageSyncManager

class MainActivity : ComponentActivity() {
    private data class WatchInfoPayload(
        val action: String,
        val versionName: String,
        val deviceId: String,
        val timestamp: Long?
    )

    private data class WatchStoragePayload(
        val action: String,
        val totalStorage: Long,
        val availableStorage: Long,
        val timestamp: Long?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize device reporting service
        DeviceReportService.init(applicationContext)
        ImageSyncManager.init(applicationContext)
        
        val config = ClarityConfig(
            projectId = "v9ht6u2tnu",
            logLevel = LogLevel.None
        )
        Clarity.initialize(applicationContext, config)

        enableEdgeToEdge()
        setContent {
            SimpleweathersyncerngTheme {
                val topBarState = rememberTopAppBarState()
                val scrollBehavior = MiuixScrollBehavior(state = topBarState)
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                
                // Hoisted update state
                val showUpdateDialog = remember { mutableStateOf(false) }
                var updateDialogTitle by remember { mutableStateOf("") }
                var updateDialogSummary by remember { mutableStateOf("") }
                var updateDownloadUrl by remember { mutableStateOf<String?>(null) }
                var isForceUpdate by remember { mutableStateOf(false) }
                val showWatchUpdateDialog = remember { mutableStateOf(false) }
                var watchUpdateDialogTitle by remember { mutableStateOf("") }
                var watchUpdateDialogSummary by remember { mutableStateOf("") }
                var watchUpdateDownloadUrl by remember { mutableStateOf<String?>(null) }
                var watchIsForceUpdate by remember { mutableStateOf(false) }
                val showDeviceActionDialog = remember { mutableStateOf(false) }
                val showDeviceConnectionWizardDialog = remember { mutableStateOf(false) }
                val showPermissionDeniedDialog = remember { mutableStateOf(false) }
                val showLoadingDialog = remember { mutableStateOf(false) }
                var loadingDialogTitle by remember { mutableStateOf("") }
                var loadingDialogSummary by remember { mutableStateOf("") }
                var loadingDialogShownAt by remember { mutableStateOf(0L) }
                var loadingDialogDismissJob by remember { mutableStateOf<Job?>(null) }
                val showMessageDialog = remember { mutableStateOf(false) }
                var messageDialogTitle by remember { mutableStateOf("") }
                var messageDialogSummary by remember { mutableStateOf("") }
                val showWeatherDataDialog = remember { mutableStateOf(false) }
                var weatherDataPreview by remember { mutableStateOf("") }
                val showWatchStorageDialog = remember { mutableStateOf(false) }
                var watchStorageTotal by remember { mutableStateOf(0L) }
                var watchStorageAvailable by remember { mutableStateOf(0L) }
                var watchStorageTimestamp by remember { mutableStateOf<Long?>(null) }

                fun showLoadingDialog(title: String, summary: String) {
                    loadingDialogDismissJob?.cancel()
                    loadingDialogDismissJob = null
                    showMessageDialog.value = false
                    loadingDialogTitle = title
                    loadingDialogSummary = summary
                    loadingDialogShownAt = SystemClock.elapsedRealtime()
                    showLoadingDialog.value = true
                }

                fun updateLoadingDialog(summary: String, title: String = loadingDialogTitle) {
                    loadingDialogTitle = title
                    loadingDialogSummary = summary
                }

                fun hideLoadingDialog(onDismissed: (() -> Unit)? = null) {
                    if (showLoadingDialog.value) {
                        val elapsed = SystemClock.elapsedRealtime() - loadingDialogShownAt
                        val remaining = (500L - elapsed).coerceAtLeast(0L)

                        if (remaining > 0L) {
                            loadingDialogDismissJob?.cancel()
                            loadingDialogDismissJob = scope.launch {
                                delay(remaining)
                                showLoadingDialog.value = false
                                loadingDialogDismissJob = null
                                onDismissed?.invoke()
                            }
                        } else {
                            showLoadingDialog.value = false
                            onDismissed?.invoke()
                        }
                    } else {
                        onDismissed?.invoke()
                    }
                }

                fun showMessageDialog(title: String, summary: String) {
                    val presentMessage: () -> Unit = {
                        messageDialogTitle = title
                        messageDialogSummary = summary
                        showMessageDialog.value = true
                    }

                    if (showLoadingDialog.value) {
                        hideLoadingDialog(onDismissed = presentMessage)
                    } else {
                        presentMessage()
                    }
                }

                fun dismissMessageDialog() {
                    showMessageDialog.value = false
                }
                
                LaunchedEffect(Unit) {
                    // Auto check for update
                    launch {
                        try {
                            val result = UpdateService.checkForUpdateManually(context)
                            if (result.hasUpdate && result.updateInfo != null) {
                                val info = result.updateInfo
                                updateDialogTitle = "发现新版本：${info.versionName}"
                                updateDialogSummary = info.updateDescription
                                updateDownloadUrl = info.downloadUrl
                                isForceUpdate = info.forceUpdate
                                showUpdateDialog.value = true
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                val topBarBackdrop = if (isRenderEffectSupported()) {
                    val surfaceColor = MiuixTheme.colorScheme.surface
                    rememberLayerBackdrop {
                        drawRect(surfaceColor)
                        drawContent()
                    }
                } else {
                    null
                }
                val topBarColor = if (topBarBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface

                Scaffold(
                    contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
                    topBar = {
                        Box(
                            modifier = if (topBarBackdrop != null) {
                                Modifier.textureBlur(
                                    backdrop = topBarBackdrop,
                                    shape = RectangleShape,
                                    blurRadius = 20f,
                                    colors = BlurColors(
                                        blendColors = listOf(
                                            BlendColorEntry(
                                                color = MiuixTheme.colorScheme.surface.copy(alpha = 0.8f)
                                            ),
                                        ),
                                    ),
                                )
                            } else {
                                Modifier
                            }
                        ) {
                            TopAppBar(
                                title = "简明天气同步器",
                                color = topBarColor,
                                scrollBehavior = scrollBehavior,
                                actions = {
                                    IconButton(
                                        onClick = {
                                            val intent = Intent(context, BackgroundImagePickerActivity::class.java)
                                            context.startActivity(intent)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Theme,
                                            contentDescription = "主题"
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = {
                                            val intent = Intent(context, SettingsActivity::class.java)
                                            context.startActivity(intent)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Settings,
                                            contentDescription = "设置"
                                        )
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .let { base ->
                                if (topBarBackdrop != null) {
                                    base.layerBackdrop(topBarBackdrop)
                                } else {
                                    base
                                }
                            }
                    ) {
                                var isConnected by remember { mutableStateOf(false) }
                                var deviceName by remember { mutableStateOf("") }
                                var nodeId by remember { mutableStateOf("") }
                                var isReconnecting by remember { mutableStateOf(false) }
                                var reconnectTimeoutJob by remember { mutableStateOf<Job?>(null) }
                                val nodeApi = remember { Wearable.getNodeApi(context.applicationContext) }
                                val messageApi = remember { Wearable.getMessageApi(context.applicationContext) }
                                val authApi = remember { Wearable.getAuthApi(context.applicationContext) }
                                val scope = rememberCoroutineScope()

                                fun checkAndRequestPermissions(targetNodeId: String) {
                                    val permissions = arrayOf(Permission.DEVICE_MANAGER, Permission.NOTIFY)
                                    authApi.checkPermissions(targetNodeId, permissions).addOnSuccessListener { results ->
                                        var allGranted = true
                                        if (results != null && results.size == permissions.size) {
                                            for (result in results) {
                                                if (!result) {
                                                    allGranted = false
                                                    break
                                                }
                                            }
                                        } else {
                                            allGranted = false
                                        }

                                        if (!allGranted) {
                                            authApi.requestPermission(targetNodeId, *permissions)
                                        }
                                    }
                                }
                                
                                fun checkConnection(onComplete: ((Boolean) -> Unit)? = null) {
                                    nodeApi.connectedNodes.addOnSuccessListener { nodes ->
                                        val connectedNode = nodes.firstOrNull()
                                        if (connectedNode != null) {
                                            isConnected = true
                                            deviceName = connectedNode.name
                                            nodeId = connectedNode.id

                                            // Report device name to backend API
                                            scope.launch {
                                                DeviceReportService.reportDeviceName(deviceName)
                                            }

                                            checkAndRequestPermissions(nodeId)
                                            onComplete?.invoke(true)
                                        } else {
                                            isConnected = false
                                            deviceName = ""
                                            nodeId = ""
                                            onComplete?.invoke(false)
                                        }
                                    }.addOnFailureListener {
                                        isConnected = false
                                        deviceName = ""
                                        nodeId = ""
                                        onComplete?.invoke(false)
                                    }
                                }

                                fun retryDeviceConnection() {
                                    if (isReconnecting) return

                                    reconnectTimeoutJob?.cancel()
                                    isReconnecting = true

                                    reconnectTimeoutJob = scope.launch {
                                        delay(1000)
                                        if (isReconnecting) {
                                            isReconnecting = false
                                            isConnected = false
                                            deviceName = ""
                                            nodeId = ""
                                            showDeviceActionDialog.value = false
                                            showDeviceConnectionWizardDialog.value = true
                                        }
                                    }

                                    checkConnection { connected ->
                                        reconnectTimeoutJob?.cancel()
                                        reconnectTimeoutJob = null
                                        isReconnecting = false

                                        if (connected) {
                                            showDeviceConnectionWizardDialog.value = false
                                            showDeviceActionDialog.value = true
                                        } else {
                                            showDeviceActionDialog.value = false
                                            showDeviceConnectionWizardDialog.value = true
                                        }
                                    }
                                }

                                fun checkWatchUpdate() {
                                    showDeviceActionDialog.value = false

                                    if (!isConnected || nodeId.isEmpty()) {
                                        showMessageDialog("提示", "请先连接设备")
                                        return
                                    }

                                    scope.launch {
                                        try {
                                            showLoadingDialog(
                                                title = "正在检查",
                                                summary = "正在检查手表应用安装状态..."
                                            )

                                            val isInstalled = checkWatchAppInstalled(nodeApi, nodeId)
                                            if (!isInstalled) {
                                                showMessageDialog("提示", "手表端未安装应用，请先安装")
                                                return@launch
                                            }

                                            val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                                            val advancedSyncMode = prefs.getBoolean("advanced_sync_mode", true)
                                            if (advancedSyncMode) {
                                                updateLoadingDialog(summary = "正在启动应用并握手...")
                                                performWatchHandshake(nodeApi, messageApi, nodeId)
                                                delay(160)
                                            }

                                            updateLoadingDialog(summary = "正在获取手表端版本信息...")
                                            val watchInfo = requestWatchInfo(messageApi, nodeId)
                                            val quickApp = UpdateService.fetchQuickAppUpdateInfo()
                                            val hasUpdate = isVersionNewer(quickApp.versionName, watchInfo.versionName)

                                            if (hasUpdate) {
                                                watchUpdateDialogTitle = "发现新版本：${quickApp.versionName}"
                                                watchUpdateDialogSummary = quickApp.updateDescription
                                                watchUpdateDownloadUrl = quickApp.downloadUrl.takeIf { it.isNotBlank() }
                                                watchIsForceUpdate = quickApp.forceUpdate
                                                hideLoadingDialog {
                                                    showWatchUpdateDialog.value = true
                                                }
                                            } else {
                                                showMessageDialog(
                                                    title = "手表端已是最新",
                                                    summary = "当前版本：${watchInfo.versionName}"
                                                )
                                            }
                                        } catch (e: Exception) {
                                            val msg = e.message ?: ""
                                            if (msg.contains("permission denied", ignoreCase = true)) {
                                                hideLoadingDialog { showPermissionDeniedDialog.value = true }
                                            } else {
                                                showMessageDialog("检查失败", msg.ifEmpty { "未知错误" })
                                            }
                                        }
                                    }
                                }

                                fun checkWatchStorage() {
                                    showDeviceActionDialog.value = false

                                    if (!isConnected || nodeId.isEmpty()) {
                                        showMessageDialog("提示", "请先连接设备")
                                        return
                                    }

                                    scope.launch {
                                        try {
                                            showLoadingDialog(
                                                title = "正在检查",
                                                summary = "正在检查手表应用安装状态..."
                                            )

                                            val isInstalled = checkWatchAppInstalled(nodeApi, nodeId)
                                            if (!isInstalled) {
                                                showMessageDialog("提示", "手表端未安装应用，请先安装")
                                                return@launch
                                            }

                                            val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                                            val advancedSyncMode = prefs.getBoolean("advanced_sync_mode", true)
                                            if (advancedSyncMode) {
                                                updateLoadingDialog(summary = "正在启动应用并握手...")
                                                performWatchHandshake(nodeApi, messageApi, nodeId)
                                                delay(160)
                                            }

                                            updateLoadingDialog(summary = "正在获取手表存储空间信息...")
                                            val storageInfo = requestWatchStorage(messageApi, nodeId)

                                            hideLoadingDialog {
                                                watchStorageTotal = storageInfo.totalStorage
                                                watchStorageAvailable = storageInfo.availableStorage
                                                watchStorageTimestamp = storageInfo.timestamp
                                                showWatchStorageDialog.value = true
                                            }
                                        } catch (e: Exception) {
                                            val msg = e.message ?: ""
                                            if (msg.contains("permission denied", ignoreCase = true)) {
                                                hideLoadingDialog { showPermissionDeniedDialog.value = true }
                                            } else {
                                                showMessageDialog("检查失败", msg.ifEmpty { "未知错误" })
                                            }
                                        }
                                    }
                                }

                                LaunchedEffect(Unit) {
                                    checkConnection()
                                }

                                val lifecycleOwner = LocalLifecycleOwner.current
                                DisposableEffect(lifecycleOwner) {
                                    val observer = LifecycleEventObserver { _, event ->
                                        if (event == Lifecycle.Event.ON_RESUME) {
                                            checkConnection()
                                        }
                                    }
                                    lifecycleOwner.lifecycle.addObserver(observer)
                                    onDispose {
                                        lifecycleOwner.lifecycle.removeObserver(observer)
                                    }
                                }
                                
                                val syncDaysOptions = listOf("3天", "7天", "10天", "15天", "30天")
                                var selectedSyncDaysIndex by remember { mutableIntStateOf(1) }
                                var syncHourlyWeather by remember { mutableStateOf(true) }
                                var syncAlertData by remember { mutableStateOf(true) }
                                
                                var currentLocation by remember { mutableStateOf("未设置") }
                                var selectedCityLocation by remember { mutableStateOf<CityLocation?>(null) }
                                
                                // Load saved preferences
                                LaunchedEffect(Unit) {
                                    val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                                    selectedSyncDaysIndex = prefs.getInt("sync_days_index", 1)
                                    syncHourlyWeather = prefs.getBoolean("sync_hourly_weather", true)
                                    syncAlertData = prefs.getBoolean("sync_alert_data", true)
                                    currentLocation = prefs.getString("selected_location_name", "未设置") ?: "未设置"
                                    val locationJson = prefs.getString("selected_location_json", null)
                                    if (locationJson != null) {
                                        try {
                                            selectedCityLocation = Gson().fromJson(locationJson, CityLocation::class.java)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }

                                val locationPickerLauncher = rememberLauncherForActivityResult(
                                    contract = ActivityResultContracts.StartActivityForResult()
                                ) { result ->
                                    if (result.resultCode == Activity.RESULT_OK) {
                                        val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                                        val editor = prefs.edit()
                                        
                                        result.data?.getStringExtra("location")?.let {
                                            currentLocation = it
                                            editor.putString("selected_location_name", it)
                                        }
                                        result.data?.getStringExtra("location_data")?.let { json ->
                                            try {
                                                selectedCityLocation = Gson().fromJson(json, CityLocation::class.java)
                                                editor.putString("selected_location_json", json)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                        editor.apply()
                                    }
                                }

                                androidx.compose.foundation.lazy.LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .overScrollVertical()
                                        .scrollEndHaptic()
                                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                                    contentPadding = innerPadding
                                ) {
                                    item {
                                        SponsorPromoCard(
                                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
                                            onClick = {
                                                val intent = Intent(context, SponsorActivity::class.java)
                                                context.startActivity(intent)
                                            }
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Card(
                                            modifier = Modifier
                                                .padding(horizontal = 12.dp)
                                                .padding(bottom = 12.dp)
                                        ) {
                                            ArrowPreference(
                                                title = if (isConnected) "已连接设备" else "未连接设备",
                                                summary = when {
                                                    isConnected && deviceName.isNotBlank() -> deviceName
                                                    isReconnecting -> "正在连接设备..."
                                                    else -> "点击重新连接"
                                                },
                                                onClick = {
                                                    if (isConnected && nodeId.isNotEmpty()) {
                                                        showDeviceActionDialog.value = true
                                                    } else if (!isReconnecting) {
                                                        retryDeviceConnection()
                                                    }
                                                }
                                            )
                                        }

                                    Card(
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp)
                                            .padding(bottom = 12.dp)
                                    ) {
                                        ArrowPreference(
                                            title = "位置设置",
                                            summary = currentLocation,
                                            onClick = {
                                                locationPickerLauncher.launch(Intent(context, LocationPickerActivity::class.java))
                                            }
                                        )
                                        OverlayDropdownPreference(
                                            title = "同步天气天数",
                                            items = syncDaysOptions,
                                            selectedIndex = selectedSyncDaysIndex,
                                            onSelectedIndexChange = { 
                                                selectedSyncDaysIndex = it
                                                val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                                                prefs.edit().putInt("sync_days_index", it).apply()
                                            }
                                        )
                                        SwitchPreference(
                                            title = "同步逐小时天气数据",
                                            summary = "开启后同步最近一周逐小时天气",
                                            checked = syncHourlyWeather,
                                            onCheckedChange = {
                                                syncHourlyWeather = it
                                                val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                                                prefs.edit().putBoolean("sync_hourly_weather", it).apply()
                                            }
                                        )
                                        SwitchPreference(
                                            title = "同步天气预警数据",
                                            summary = "开启后同步天气灾害预警信息",
                                            checked = syncAlertData,
                                            onCheckedChange = {
                                                syncAlertData = it
                                                val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                                                prefs.edit().putBoolean("sync_alert_data", it).apply()
                                            }
                                        )
                                    }

                                    Button(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp),
                                        onClick = {
                                            if (selectedCityLocation == null) {
                                                showMessageDialog("提示", "请先设置位置")
                                                return@Button
                                            }

                                            scope.launch {
                                                try {
                                                    showLoadingDialog(
                                                        title = "正在获取",
                                                        summary = "正在获取天气数据..."
                                                    )

                                                    val days = when(selectedSyncDaysIndex) {
                                                        0 -> "3d"
                                                        1 -> "7d"
                                                        2 -> "10d"
                                                        3 -> "15d"
                                                        4 -> "30d"
                                                        else -> "3d"
                                                    }
                                                    val jsonString = WeatherService.fetchWeatherData(
                                                        context,
                                                        selectedCityLocation!!.id,
                                                        days,
                                                        selectedCityLocation!!.name,
                                                        syncHourlyWeather,
                                                        syncAlertData
                                                    )

                                                    hideLoadingDialog {
                                                        weatherDataPreview = jsonString
                                                        showWeatherDataDialog.value = true
                                                    }
                                                } catch (e: Exception) {
                                                    showMessageDialog("获取失败", e.message ?: "未知错误")
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors()
                                    ) {
                                        Text("复制数据")
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Button(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp),
                                        onClick = {
                                            if (selectedCityLocation == null) {
                                                showMessageDialog("提示", "请先设置位置")
                                                return@Button
                                            }

                                            if (!isConnected || nodeId.isEmpty()) {
                                                showMessageDialog("提示", "请先连接设备")
                                                return@Button
                                            }
                                            
                                            scope.launch {
                                                try {
                                                    showLoadingDialog(
                                                        title = "正在检查",
                                                        summary = "正在检查手表应用安装状态..."
                                                    )
                                                    
                                                    val isInstalled = checkWatchAppInstalled(nodeApi, nodeId)
                                                    if (!isInstalled) {
                                                        showMessageDialog("提示", "手表端未安装应用，请先安装")
                                                        return@launch
                                                    }
                                                } catch (e: Exception) {
                                                    val msg = e.message ?: ""
                                                    if (msg.contains("permission denied", ignoreCase = true)) {
                                                        hideLoadingDialog { showPermissionDeniedDialog.value = true }
                                                    } else {
                                                        showMessageDialog("检查失败", msg.ifEmpty { "未知错误" })
                                                    }
                                                    return@launch
                                                }

                                                val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                                                val advancedSyncMode = prefs.getBoolean("advanced_sync_mode", true)
                                                try {
                                                    showLoadingDialog(
                                                        title = "正在同步",
                                                        summary = "正在获取天气数据..."
                                                    )
                                                    
                                                    val days = when(selectedSyncDaysIndex) {
                                                        0 -> "3d"
                                                        1 -> "7d"
                                                        2 -> "10d"
                                                        3 -> "15d"
                                                        4 -> "30d"
                                                        else -> "3d"
                                                    }
                                                    val jsonString = WeatherService.fetchWeatherData(
                                                        context,
                                                        selectedCityLocation!!.id,
                                                        days,
                                                        selectedCityLocation!!.name,
                                                        syncHourlyWeather,
                                                        syncAlertData
                                                    )

                                                    if (advancedSyncMode) {
                                                        updateLoadingDialog(summary = "正在启动应用并握手...")
                                                        performWatchHandshake(nodeApi, messageApi, nodeId)
                                                    }

                                                    updateLoadingDialog(summary = "正在发送数据到设备...")
                                                    
                                                    messageApi.sendMessage(nodeId, jsonString.toByteArray())
                                                        .addOnSuccessListener {
                                                            showMessageDialog("同步成功", "天气数据已发送到设备")
                                                        }
                                                        .addOnFailureListener { e ->
                                                            showMessageDialog("发送失败", e.message ?: "未知错误")
                                                        }
                                                    
                                                } catch (e: Exception) {
                                                    showMessageDialog("获取失败", e.message ?: "未知错误")
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColorsPrimary()
                                        ) {
                                            Text("同步数据", color = Color.White)
                                        }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    OverlayDialog(
                                        title = messageDialogTitle,
                                        summary = messageDialogSummary,
                                        show = showMessageDialog.value,
                                        onDismissRequest = { dismissMessageDialog() }
                                    ) {
                                        TextButton(
                                            text = "确定",
                                            onClick = { dismissMessageDialog() },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    OverlayDialog(
                                        title = loadingDialogTitle,
                                        summary = loadingDialogSummary,
                                        show = showLoadingDialog.value,
                                        onDismissRequest = { },
                                        onDismissFinished = {
                                            if (!showLoadingDialog.value) {
                                                loadingDialogDismissJob?.cancel()
                                                loadingDialogDismissJob = null
                                            }
                                        }
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

                                    WindowDialog(
                                        title = "已连接设备",
                                        summary = "当前设备：$deviceName",
                                        show = showDeviceActionDialog.value,
                                        onDismissRequest = { showDeviceActionDialog.value = false }
                                    ) {
                                        TextButton(
                                            text = "检查手表端更新",
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = { checkWatchUpdate() },
                                            colors = ButtonDefaults.textButtonColorsPrimary()
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        TextButton(
                                            text = "检查手表存储空间",
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = { checkWatchStorage() },
                                            colors = ButtonDefaults.textButtonColorsPrimary()
                                        )
                                    }

                                    WindowDialog(
                                        title = "未连接设备",
                                        summary = null,
                                        show = showDeviceConnectionWizardDialog.value,
                                        onDismissRequest = { showDeviceConnectionWizardDialog.value = false }
                                    ) {
                                        Text(
                                            text = """
                                                1. 在小米运动健康中连接设备
                                                2. 保证小米运动健康后台运行
                                                3. 返回当前页面重试
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
                                                onClick = { showDeviceConnectionWizardDialog.value = false }
                                            )
                                        }
                                    }
                                    }
                                }
                            }
                    }

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

                WindowDialog(
                    title = "天气数据预览",
                    summary = "可预览后复制到剪贴板",
                    show = showWeatherDataDialog.value,
                    onDismissRequest = { showWeatherDataDialog.value = false }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(weatherDataPreview)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            text = "关闭",
                            modifier = Modifier.weight(1f),
                            onClick = { showWeatherDataDialog.value = false }
                        )
                        TextButton(
                            text = "复制",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Weather Data", weatherDataPreview)
                                clipboard.setPrimaryClip(clip)
                                showWeatherDataDialog.value = false

                                showMessageDialog("复制成功", "天气数据已复制到剪贴板")
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }

                WindowDialog(
                    title = updateDialogTitle,
                    summary = updateDialogSummary,
                    show = showUpdateDialog.value,
                    onDismissRequest = {
                        if (!isForceUpdate) {
                            showUpdateDialog.value = false
                        }
                    }
                ) {
                    if (updateDownloadUrl != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (!isForceUpdate) {
                                TextButton(
                                    text = "取消",
                                    modifier = Modifier.weight(1f),
                                    onClick = { showUpdateDialog.value = false }
                                )
                            }
                            TextButton(
                                text = if (isForceUpdate) "立即更新" else "前往下载",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateDownloadUrl!!))
                                    context.startActivity(intent)
                                    if (!isForceUpdate) {
                                        showUpdateDialog.value = false
                                    }
                                },
                                colors = ButtonDefaults.textButtonColorsPrimary()
                            )
                        }
                    } else {
                        TextButton(
                            text = "确定",
                            onClick = { showUpdateDialog.value = false },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                WindowDialog(
                    title = watchUpdateDialogTitle,
                    summary = watchUpdateDialogSummary,
                    show = showWatchUpdateDialog.value,
                    onDismissRequest = {
                        if (!watchIsForceUpdate) {
                            showWatchUpdateDialog.value = false
                        }
                    }
                ) {
                    if (watchUpdateDownloadUrl != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (!watchIsForceUpdate) {
                                TextButton(
                                    text = "取消",
                                    modifier = Modifier.weight(1f),
                                    onClick = { showWatchUpdateDialog.value = false }
                                )
                            }
                            TextButton(
                                text = if (watchIsForceUpdate) "立即更新" else "前往下载",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(watchUpdateDownloadUrl!!))
                                    context.startActivity(intent)
                                    if (!watchIsForceUpdate) {
                                        showWatchUpdateDialog.value = false
                                    }
                                },
                                colors = ButtonDefaults.textButtonColorsPrimary()
                            )
                        }
                    } else {
                        TextButton(
                            text = "确定",
                            onClick = { showWatchUpdateDialog.value = false },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                WindowDialog(
                    title = "手表存储空间",
                    summary = null,
                    show = showWatchStorageDialog.value,
                    onDismissRequest = { showWatchStorageDialog.value = false }
                ) {
                    val usedStorage = watchStorageTotal - watchStorageAvailable
                    val usedRatio = if (watchStorageTotal > 0) {
                        (usedStorage.toFloat() / watchStorageTotal.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(140.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = usedRatio,
                                size = 140.dp,
                                strokeWidth = 10.dp
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = formatBytes(usedStorage),
                                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                                    fontWeight = FontWeight.Bold,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "已使用",
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "总容量",
                                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = formatBytes(watchStorageTotal),
                                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "可用空间",
                                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = formatBytes(watchStorageAvailable),
                                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "已用空间",
                                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = formatBytes(usedStorage),
                                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        if (watchStorageTimestamp != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "获取时间：${formatTimestamp(watchStorageTimestamp!!)}",
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        text = "确定",
                        onClick = { showWatchStorageDialog.value = false },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    private suspend fun checkWatchAppInstalled(nodeApi: NodeApi, nodeId: String): Boolean = suspendCancellableCoroutine { cont ->
        nodeApi.isWearAppInstalled(nodeId)
            .addOnSuccessListener { isInstalled ->
                cont.resume(isInstalled)
            }
            .addOnFailureListener { e ->
                cont.resumeWithException(e)
            }
    }

    private suspend fun performWatchHandshake(nodeApi: NodeApi, messageApi: MessageApi, nodeId: String) {
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

    private suspend fun requestWatchInfo(messageApi: MessageApi, nodeId: String): WatchInfoPayload {
        val result = CompletableDeferred<String>()
        val listener = OnMessageReceivedListener { _, message ->
            if (!result.isCompleted) {
                val content = String(message, Charsets.UTF_8)
                try {
                    val json = JsonParser.parseString(content).asJsonObject
                    val action = json.get("action")?.asString ?: ""
                    if (action == "info") {
                        result.complete(content)
                    }
                } catch (_: Exception) {
                    // Ignore non-JSON or non-info messages
                }
            }
        }

        try {
            messageApi.addListener(nodeId, listener)

            messageApi.sendMessage(nodeId, "info".toByteArray(Charsets.UTF_8))
                .addOnFailureListener { e ->
                    if (!result.isCompleted) {
                        result.completeExceptionally(Exception("发送失败: ${e.message}"))
                    }
                }

            val raw = withTimeoutOrNull(8000) { result.await() }
                ?: throw Exception("等待手表端信息超时")

            val payload = parseWatchInfoPayload(raw)
            if (payload.action != "info") {
                throw Exception("手表返回了非 info 消息: ${payload.action}")
            }
            return payload

        } catch (e: Exception) {
            throw e
        } finally {
            messageApi.removeListener(nodeId)
        }
    }

    private suspend fun requestWatchStorage(messageApi: MessageApi, nodeId: String): WatchStoragePayload {
        val result = CompletableDeferred<String>()
        val listener = OnMessageReceivedListener { _, message ->
            if (!result.isCompleted) {
                val content = String(message, Charsets.UTF_8)
                try {
                    val json = JsonParser.parseString(content).asJsonObject
                    val action = json.get("action")?.asString ?: ""
                    if (action == "storage") {
                        result.complete(content)
                    }
                } catch (_: Exception) {
                    // Ignore non-JSON or non-storage messages
                }
            }
        }

        try {
            messageApi.addListener(nodeId, listener)

            messageApi.sendMessage(nodeId, "storage".toByteArray(Charsets.UTF_8))
                .addOnFailureListener { e ->
                    if (!result.isCompleted) {
                        result.completeExceptionally(Exception("发送失败: ${e.message}"))
                    }
                }

            val raw = withTimeoutOrNull(8000) { result.await() }
                ?: throw Exception("等待手表端存储信息超时")

            val payload = parseWatchStoragePayload(raw)
            if (payload.action != "storage") {
                throw Exception("手表返回了非 storage 消息: ${payload.action}")
            }
            return payload

        } catch (e: Exception) {
            throw e
        } finally {
            messageApi.removeListener(nodeId)
        }
    }

    private fun parseWatchInfoPayload(raw: String): WatchInfoPayload {
        return try {
            val json = JsonParser.parseString(raw).asJsonObject
            WatchInfoPayload(
                action = json.get("action")?.asString ?: "",
                versionName = json.get("versionName")?.asString ?: "",
                deviceId = json.get("deviceId")?.asString ?: "",
                timestamp = if (json.has("timestamp") && !json.get("timestamp").isJsonNull) json.get("timestamp").asLong else null
            ).also {
                if (it.action.isBlank() || it.versionName.isBlank()) {
                    throw IllegalArgumentException("返回内容缺少 action 或 versionName")
                }
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("手表返回格式无效: $raw")
        }
    }

    private fun parseWatchStoragePayload(raw: String): WatchStoragePayload {
        return try {
            val json = JsonParser.parseString(raw).asJsonObject
            WatchStoragePayload(
                action = json.get("action")?.asString ?: "",
                totalStorage = json.get("totalStorage")?.asLong ?: 0L,
                availableStorage = json.get("availableStorage")?.asLong ?: 0L,
                timestamp = if (json.has("timestamp") && !json.get("timestamp").isJsonNull) json.get("timestamp").asLong else null
            ).also {
                if (it.action.isBlank()) {
                    throw IllegalArgumentException("返回内容缺少 action")
                }
                if (it.totalStorage <= 0L) {
                    throw IllegalArgumentException("返回内容缺少有效的 totalStorage")
                }
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("手表返回格式无效: $raw")
        }
    }

    private fun isVersionNewer(latestVersion: String, currentVersion: String): Boolean {
        val latestParts = latestVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLength = maxOf(latestParts.size, currentParts.size)

        for (i in 0 until maxLength) {
            val latest = latestParts.getOrElse(i) { 0 }
            val current = currentParts.getOrElse(i) { 0 }
            if (latest > current) return true
            if (latest < current) return false
        }
        return false
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824L -> "%.2f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576L -> "%.2f MB".format(bytes / 1_048_576.0)
            bytes >= 1_024L -> "%.2f KB".format(bytes / 1_024.0)
            else -> "$bytes B"
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

}

@Composable
private fun SponsorPromoCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val surface = MiuixTheme.colorScheme.surface
    val isDark = surface.luminance() < 0.5f
    val titleColor = MiuixTheme.colorScheme.onSurface
    val summaryColor = titleColor.copy(alpha = if (isDark) 0.78f else 0.72f)
    val overlayBrush = Brush.verticalGradient(
        colors = listOf(
            surface.copy(alpha = if (isDark) 0.08f else 0.02f),
            surface.copy(alpha = if (isDark) 0.28f else 0.14f),
        ),
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.defaultColors(
            color = Color.Transparent,
            contentColor = titleColor,
        ),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            AboutCardBackground(
                modifier = Modifier.matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(overlayBrush)
            )
            ArrowPreference(
                modifier = Modifier.fillMaxWidth(),
                title = "支持本项目",
                titleColor = BasicComponentDefaults.titleColor(titleColor),
                summary = "赞助可帮助维护天气服务并支持后续功能开发",
                summaryColor = BasicComponentDefaults.summaryColor(summaryColor),
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SimpleweathersyncerngTheme {
        Greeting("Android")
    }
}
