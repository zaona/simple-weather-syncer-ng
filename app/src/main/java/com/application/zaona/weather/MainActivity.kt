package com.application.zaona.weather

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import com.application.zaona.weather.model.CityLocation
import com.application.zaona.weather.service.UpdateService
import com.application.zaona.weather.service.WeatherService
import com.google.gson.Gson
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.application.zaona.weather.ui.theme.SimpleweathersyncerngTheme
import com.xiaomi.xms.wearable.Wearable
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperBottomSheet
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.WindowDialog
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.utils.overScrollVertical

import com.xiaomi.xms.wearable.message.OnMessageReceivedListener
import com.xiaomi.xms.wearable.node.NodeApi
import com.xiaomi.xms.wearable.auth.AuthApi
import com.xiaomi.xms.wearable.auth.Permission
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.microsoft.clarity.Clarity
import com.microsoft.clarity.ClarityConfig
import com.microsoft.clarity.models.LogLevel
import androidx.lifecycle.lifecycleScope

import com.application.zaona.weather.service.SupabaseService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Supabase Service
        SupabaseService.init(applicationContext)
        
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
                val showDialog = remember { mutableStateOf(false) }
                var dialogTitle by remember { mutableStateOf("") }
                var dialogSummary by remember { mutableStateOf("") }
                val showWeatherDataDialog = remember { mutableStateOf(false) }
                var weatherDataPreview by remember { mutableStateOf("") }
                
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

                Scaffold(
                    contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
                    topBar = {
                        TopAppBar(
                            title = "简明天气同步器",
                            scrollBehavior = scrollBehavior,
                            actions = {
                                IconButton(
                                    modifier = Modifier.padding(end = 8.dp),
                                    onClick = {
                                        val intent = Intent(context, SponsorActivity::class.java)
                                        context.startActivity(intent)
                                    }
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Favorites,
                                        contentDescription = "赞助"
                                    )
                                }
                                IconButton(
                                    modifier = Modifier.padding(end = 16.dp),
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
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                                var isConnected by remember { mutableStateOf(false) }
                                var deviceName by remember { mutableStateOf("") }
                                var nodeId by remember { mutableStateOf("") }
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
                                
                                fun checkConnection() {
                                    nodeApi.connectedNodes.addOnSuccessListener { nodes ->
                                        if (nodes.isNotEmpty()) {
                                            isConnected = true
                                            deviceName = nodes[0].name
                                            nodeId = nodes[0].id
                                            
                                            // Report device name to Supabase
                                            scope.launch {
                                                SupabaseService.reportDeviceName(deviceName)
                                            }

                                            checkAndRequestPermissions(nodeId)
                                        } else {
                                            isConnected = false
                                            deviceName = ""
                                            nodeId = ""
                                        }
                                    }.addOnFailureListener {
                                        isConnected = false
                                    }
                                }
                                
                                LaunchedEffect(Unit) {
                                    checkConnection()
                                }
                                
                                val syncDaysOptions = listOf("3天", "7天", "10天", "15天", "30天")
                                var selectedSyncDaysIndex by remember { mutableIntStateOf(0) }
                                var syncHourlyWeather by remember { mutableStateOf(false) }
                                
                                var currentLocation by remember { mutableStateOf("未设置") }
                                var selectedCityLocation by remember { mutableStateOf<CityLocation?>(null) }
                                
                                // Load saved preferences
                                LaunchedEffect(Unit) {
                                    val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                                    selectedSyncDaysIndex = prefs.getInt("sync_days_index", 0)
                                    syncHourlyWeather = prefs.getBoolean("sync_hourly_weather", false)
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
                                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                                ) {
                                    item {
                                    Card(
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        BasicComponent(
                                            title = if (isConnected) "已连接设备" else "未连接设备",
                                            summary = if (isConnected) deviceName else "点击重试",
                                            onClick = { checkConnection() }
                                        )
                                    }
                                    
                                    Card(
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    ) {
                                        SuperArrow(
                                            title = "位置设置",
                                            summary = currentLocation,
                                            onClick = {
                                                locationPickerLauncher.launch(Intent(context, LocationPickerActivity::class.java))
                                            }
                                        )
                                        SuperDropdown(
                                            title = "同步天气天数",
                                            items = syncDaysOptions,
                                            selectedIndex = selectedSyncDaysIndex,
                                            onSelectedIndexChange = { 
                                                selectedSyncDaysIndex = it
                                                val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                                                prefs.edit().putInt("sync_days_index", it).apply()
                                            }
                                        )
                                        SuperSwitch(
                                            title = "同步逐小时天气数据",
                                            summary = "开启后同步最近一周逐小时天气",
                                            checked = syncHourlyWeather,
                                            onCheckedChange = {
                                                syncHourlyWeather = it
                                                val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                                                prefs.edit().putBoolean("sync_hourly_weather", it).apply()
                                            }
                                        )
                                    }

                                    Button(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .padding(top = 16.dp),
                                        onClick = {
                                            if (selectedCityLocation == null) {
                                                dialogTitle = "提示"
                                                dialogSummary = "请先设置位置"
                                                showDialog.value = true
                                                return@Button
                                            }
                                            
                                            scope.launch {
                                                try {
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
                                                        syncHourlyWeather
                                                    )

                                                    weatherDataPreview = jsonString
                                                    showWeatherDataDialog.value = true
                                                } catch (e: Exception) {
                                                    dialogTitle = "获取失败"
                                                    dialogSummary = e.message ?: "未知错误"
                                                    showDialog.value = true
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors()
                                    ) {
                                        Text("复制数据")
                                    }

                                    Button(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .padding(top = 16.dp),
                                        onClick = {
                                            if (selectedCityLocation == null) {
                                                dialogTitle = "提示"
                                                dialogSummary = "请先设置位置"
                                                showDialog.value = true
                                                return@Button
                                            }

                                            if (!isConnected || nodeId.isEmpty()) {
                                                dialogTitle = "提示"
                                                dialogSummary = "请先连接设备"
                                                showDialog.value = true
                                                return@Button
                                            }
                                            
                                            scope.launch {
                                                try {
                                                    dialogTitle = "正在检查"
                                                    dialogSummary = "正在检查手表应用安装状态..."
                                                    showDialog.value = true
                                                    
                                                    val isInstalled = checkWatchAppInstalled(nodeApi, nodeId)
                                                    if (!isInstalled) {
                                                        dialogTitle = "提示"
                                                        dialogSummary = "手表端未安装应用，请先安装"
                                                        showDialog.value = true
                                                        return@launch
                                                    }
                                                } catch (e: Exception) {
                                                     dialogTitle = "检查失败"
                                                     dialogSummary = "检查应用安装状态失败: ${e.message}"
                                                     showDialog.value = true
                                                     return@launch
                                                }

                                                val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                                                val advancedSyncMode = prefs.getBoolean("advanced_sync_mode", true)
                                                try {
                                                    dialogTitle = "正在同步"
                                                    dialogSummary = "正在获取天气数据..."
                                                    showDialog.value = true
                                                    
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
                                                        syncHourlyWeather
                                                    )

                                                    if (advancedSyncMode) {
                                                        dialogSummary = "正在启动应用并握手..."
                                                        
                                                        var isReady = false
                                                        val listener = OnMessageReceivedListener { _, message ->
                                                            if (String(message).contains("ready")) {
                                                                isReady = true
                                                            }
                                                        }
                                                        
                                                        try {
                                                            // Register listener
                                                            messageApi.addListener(nodeId, listener)
                                                            
                                                            // Launch app
                                                            nodeApi.launchWearApp(nodeId, "/home")
                                                            
                                                            // Handshake loop
                                                            var attempts = 0
                                                            while (attempts < 15 && !isReady) {
                                                                attempts++
                                                                messageApi.sendMessage(nodeId, "start".toByteArray())
                                                                
                                                                // Check frequently within the 600ms interval (50ms * 12 = 600ms)
                                                                for (i in 0 until 12) {
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

                                                    dialogSummary = "正在发送数据到设备..."
                                                    
                                                    messageApi.sendMessage(nodeId, jsonString.toByteArray())
                                                        .addOnSuccessListener {
                                                            dialogTitle = "同步成功"
                                                            dialogSummary = "天气数据已发送到设备"
                                                            showDialog.value = true
                                                        }
                                                        .addOnFailureListener { e ->
                                                            dialogTitle = "发送失败"
                                                            dialogSummary = e.message ?: "未知错误"
                                                            showDialog.value = true
                                                        }
                                                    
                                                } catch (e: Exception) {
                                                    dialogTitle = "获取失败"
                                                    dialogSummary = e.message ?: "未知错误"
                                                    showDialog.value = true
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColorsPrimary()
                                    ) {
                                        Text("同步数据", color = Color.White)
                                    }
                                    
                                    SuperDialog(
                                        title = dialogTitle,
                                        summary = dialogSummary,
                                        show = showDialog,
                                        onDismissRequest = { showDialog.value = false }
                                    ) {
                                        TextButton(
                                            text = "确定",
                                            onClick = { showDialog.value = false },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    }
                                }
                            }
                    }

                WindowDialog(
                    title = "天气数据预览",
                    summary = "可预览后复制到剪贴板",
                    show = showWeatherDataDialog,
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
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { showWeatherDataDialog.value = false },
                            colors = ButtonDefaults.buttonColors()
                        ) {
                            Text("关闭")
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Weather Data", weatherDataPreview)
                                clipboard.setPrimaryClip(clip)
                                showWeatherDataDialog.value = false

                                dialogTitle = "复制成功"
                                dialogSummary = "天气数据已复制到剪贴板"
                                showDialog.value = true
                            },
                            colors = ButtonDefaults.buttonColorsPrimary()
                        ) {
                            Text("复制", color = Color.White)
                        }
                    }
                }

                WindowDialog(
                    title = updateDialogTitle,
                    summary = updateDialogSummary,
                    show = showUpdateDialog,
                    onDismissRequest = {
                        if (!isForceUpdate) {
                            showUpdateDialog.value = false
                        }
                    }
                ) {
                    if (updateDownloadUrl != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (!isForceUpdate) {
                                Button(
                                    modifier = Modifier.weight(1f),
                                    onClick = { showUpdateDialog.value = false },
                                    colors = ButtonDefaults.buttonColors()
                                ) {
                                    Text("取消")
                                }
                            }
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateDownloadUrl!!))
                                    context.startActivity(intent)
                                    if (!isForceUpdate) {
                                        showUpdateDialog.value = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColorsPrimary()
                            ) {
                                Text(if (isForceUpdate) "立即更新" else "前往下载", color = Color.White)
                            }
                        }
                    } else {
                        TextButton(
                            text = "确定",
                            onClick = { showUpdateDialog.value = false },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
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
