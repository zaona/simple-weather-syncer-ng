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
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.window.WindowDialog

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

                var advancedSyncMode by remember { mutableStateOf(true) }
                var themeModeIndex by remember { mutableStateOf(0) }

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
                    advancedSyncMode = prefs.getBoolean("advanced_sync_mode", true)
                    themeModeIndex = when (prefs.getString("theme_mode", "system")) {
                        "light" -> 1
                        "dark" -> 2
                        else -> 0
                    }
                    prefs.edit()
                        .remove("use_custom_api")
                        .remove("custom_api_host")
                        .remove("custom_api_key")
                        .apply()
                }

                Scaffold(
                    contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
                    topBar = {
                        TopAppBar(
                            title = "设置",
                            navigationIcon = {
                                IconButton(
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
                            Spacer(modifier = Modifier.height(16.dp))

                            Card(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 12.dp)
                            ) {
                                val themeModeOptions = listOf("跟随系统", "浅色", "深色")
                                SwitchPreference(
                                    title = "高级同步模式",
                                    summary = "启用后先启动应用并握手",
                                    checked = advancedSyncMode,
                                    onCheckedChange = { 
                                        advancedSyncMode = it
                                        val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                                        prefs.edit().putBoolean("advanced_sync_mode", it).apply()
                                    }
                                )
                                OverlayDropdownPreference(
                                    title = "主题模式",
                                    items = themeModeOptions,
                                    selectedIndex = themeModeIndex,
                                    onSelectedIndexChange = {
                                        themeModeIndex = it
                                        val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                                        val modeValue = when (it) {
                                            1 -> "light"
                                            2 -> "dark"
                                            else -> "system"
                                        }
                                        prefs.edit().putString("theme_mode", modeValue).apply()
                                    }
                                )
                                ArrowPreference(
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
                            }

                            item {
                            SmallTitle(text = "更多内容")
                            Card(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 12.dp)
                            ) {
                                ArrowPreference(
                                    title = "帮助文档",
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.yuque.com/zaona/weather"))
                                        context.startActivity(intent)
                                    }
                                )
                                ArrowPreference(
                                    title = "QQ交流群",
                                    summary = "947038648",
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://qm.qq.com/q/afSsUcRWjS"))
                                        context.startActivity(intent)
                                    }
                                )
                            }
                            }

                            item {
                            SmallTitle(text = "特别鸣谢")
                            Card(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 12.dp)
                            ) {
                                ArrowPreference(
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
                    }
                }

                WindowDialog(
                    title = "正在检查更新",
                    show = showCheckingDialog.value,
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
