package com.application.zaona.weather

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.application.zaona.weather.service.UpdateService
import com.application.zaona.weather.service.WeatherService
import com.application.zaona.weather.ui.theme.SimpleweathersyncerngTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperBottomSheet
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.extra.WindowDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.utils.overScrollVertical

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimpleweathersyncerngTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val topBarState = rememberTopAppBarState()
                val scrollBehavior = MiuixScrollBehavior(state = topBarState)

                var advancedSyncMode by remember { mutableStateOf(false) }
                var useCustomApi by remember { mutableStateOf(false) }

                // Hoisted update state
                val showUpdateDialog = remember { mutableStateOf(false) }
                var updateDialogTitle by remember { mutableStateOf("") }
                var updateDialogSummary by remember { mutableStateOf("") }
                var updateDownloadUrl by remember { mutableStateOf<String?>(null) }
                var isForceUpdate by remember { mutableStateOf(false) }
                
                // Manual check loading state
                val showCheckingDialog = remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                    advancedSyncMode = prefs.getBoolean("advanced_sync_mode", false)
                    useCustomApi = prefs.getBoolean("use_custom_api", false)
                }

                Scaffold(
                    contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
                    topBar = {
                        TopAppBar(
                            title = "设置",
                            navigationIcon = {
                                IconButton(
                                    modifier = Modifier.padding(start = 16.dp),
                                    onClick = { finish() }
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Back,
                                        contentDescription = "返回"
                                    )
                                }
                            },
                            scrollBehavior = scrollBehavior
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        val showApiSettings = remember { mutableStateOf(false) }
                        val currentVersion = remember {
                            try {
                                context.packageManager.getPackageInfo(context.packageName, 0).versionName
                            } catch (e: Exception) {
                                "1.0"
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
                                SuperSwitch(
                                    title = "高级同步模式",
                                    summary = "启用后先启动应用并握手",
                                    checked = advancedSyncMode,
                                    onCheckedChange = { 
                                        advancedSyncMode = it
                                        val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                                        prefs.edit().putBoolean("advanced_sync_mode", it).apply()
                                    }
                                )
                                SuperArrow(
                                    title = "API 设置",
                                    summary = if (useCustomApi) "自定义" else "默认",
                                    onClick = { showApiSettings.value = true }
                                )
                                SuperArrow(
                                    title = "检查更新",
                                    summary = currentVersion,
                                    onClick = { 
                                        scope.launch {
                                            showCheckingDialog.value = true
                                            // Add a minimum delay to ensure the dialog is visible and user perceives the check
                                            val checkJob = launch {
                                                delay(1000)
                                            }
                                            val result = UpdateService.checkForUpdateManually(context)
                                            checkJob.join() // Wait for at least 1 second
                                            showCheckingDialog.value = false

                                            if (result.checkFailed) {
                                                updateDialogTitle = "检查更新失败"
                                                updateDialogSummary = result.errorMessage ?: "网络连接失败，请稍后重试"
                                                updateDownloadUrl = null
                                                showUpdateDialog.value = true
                                            } else if (result.hasUpdate && result.updateInfo != null) {
                                                val info = result.updateInfo
                                                updateDialogTitle = "发现新版本：${info.versionName}"
                                                updateDialogSummary = info.updateDescription
                                                updateDownloadUrl = info.downloadUrl
                                                isForceUpdate = info.forceUpdate
                                                showUpdateDialog.value = true
                                            } else {
                                                updateDialogTitle = "已是最新版本"
                                                updateDialogSummary = "当前已是最新版本，无需更新"
                                                updateDownloadUrl = null
                                                showUpdateDialog.value = true
                                            }
                                        }
                                    }
                                )
                            }

                            SmallTitle(text = "特别感谢")
                            Card(
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                SuperArrow(
                                    title = "赞助者",
                                    onClick = {
                                        val intent = Intent(context, SponsorActivity::class.java)
                                        context.startActivity(intent)
                                    }
                                )
                                BasicComponent(
                                    title = "Waijade",
                                    summary = "为快应用与同步器插件贡献代码",
                                    onClick = { /* Optional: Link to profile */ }
                                )
                                BasicComponent(
                                    title = "xinghengCN",
                                    summary = "为作者提供米环9和9pro供测试",
                                    onClick = { /* Optional: Link to profile */ }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        SuperBottomSheet(
                            show = showApiSettings,
                            title = "API 设置",
                            onDismissRequest = { showApiSettings.value = false },
                            defaultWindowInsetsPadding = false
                        ) {
                            // State for API settings
                            var customApiHost by remember { mutableStateOf("") }
                            var customApiKey by remember { mutableStateOf("") }
                            var isTestingConnection by remember { mutableStateOf(false) }
                            
                            // Dialog for test result
                            val showTestDialog = remember { mutableStateOf(false) }
                            var testDialogTitle by remember { mutableStateOf("") }
                            var testDialogSummary by remember { mutableStateOf("") }

                            // Load settings
                            LaunchedEffect(showApiSettings.value) {
                                if (showApiSettings.value) {
                                    val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                                    customApiHost = prefs.getString("custom_api_host", "") ?: ""
                                    customApiKey = prefs.getString("custom_api_key", "") ?: ""
                                }
                            }

                            Column(modifier = Modifier.padding(16.dp)) {
                                TextField(
                                    value = customApiHost,
                                    onValueChange = { customApiHost = it },
                                    label = "API Host",
                                    modifier = Modifier.fillMaxWidth()
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                TextField(
                                    value = customApiKey,
                                    onValueChange = { customApiKey = it },
                                    label = "API Key",
                                    modifier = Modifier.fillMaxWidth()
                                )
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isTestingConnection,
                                    onClick = {
                                        scope.launch {
                                            isTestingConnection = true
                                            try {
                                                // Test connection
                                                val host = customApiHost
                                                val key = customApiKey
                                                
                                                val (success, message) = WeatherService.testApiConnection(host, key)
                                                
                                                if (success) {
                                                    // Save settings
                                                    val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                                                    prefs.edit()
                                                        .putBoolean("use_custom_api", true)
                                                        .putString("custom_api_host", customApiHost)
                                                        .putString("custom_api_key", customApiKey)
                                                        .apply()
                                                    
                                                    useCustomApi = true
                                                    testDialogTitle = "保存成功"
                                                    testDialogSummary = "配置已保存并验证通过"
                                                } else {
                                                    testDialogTitle = "验证失败"
                                                    testDialogSummary = message
                                                }
                                            } catch (e: Exception) {
                                                testDialogTitle = "错误"
                                                testDialogSummary = e.message ?: "未知错误"
                                            } finally {
                                                isTestingConnection = false
                                                showTestDialog.value = true
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColorsPrimary()
                                ) {
                                    if (isTestingConnection) {
                                        InfiniteProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color.White
                                        )
                                    } else {
                                        Text("保存并验证", color = Color.White)
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Button(
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            // Reset logic
                                            customApiHost = ""
                                            customApiKey = ""
                                            
                                            val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                                            prefs.edit()
                                                .remove("use_custom_api")
                                                .remove("custom_api_host")
                                                .remove("custom_api_key")
                                                .apply()
                                            
                                            useCustomApi = false
                                            testDialogTitle = "重置成功"
                                            testDialogSummary = "已恢复默认配置"
                                            showTestDialog.value = true
                                        },
                                        colors = ButtonDefaults.buttonColors()
                                    ) {
                                        Text("重置")
                                    }
                                    
                                    Button(
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.yuque.com/zaona/weather/api"))
                                            context.startActivity(intent)
                                        },
                                        colors = ButtonDefaults.buttonColors()
                                    ) {
                                        Text("帮助")
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                            
                            SuperDialog(
                                title = testDialogTitle,
                                summary = testDialogSummary,
                                show = showTestDialog,
                                onDismissRequest = { showTestDialog.value = false }
                            ) {
                                TextButton(
                                    text = "确定",
                                    onClick = { showTestDialog.value = false },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                WindowDialog(
                    title = "正在检查更新",
                    show = showCheckingDialog,
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
}
