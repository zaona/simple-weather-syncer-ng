package com.application.zaona.weather

import android.app.Activity
import android.content.Intent
import android.os.Bundle
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.application.zaona.weather.ui.theme.SimpleweathersyncerngTheme
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
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.icon.extended.Settings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimpleweathersyncerngTheme {
                var selectedIndex by remember { mutableIntStateOf(0) }
                val navItems = listOf(
                    NavigationItem("同步", MiuixIcons.Send),
                    NavigationItem("设置", MiuixIcons.Settings)
                )
                val topBarState = rememberTopAppBarState()
                val scrollBehavior = MiuixScrollBehavior(state = topBarState)
                val context = LocalContext.current

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = "简明天气同步器",
                            scrollBehavior = scrollBehavior
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            items = navItems,
                            selected = selectedIndex,
                            onClick = { selectedIndex = it }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                    ) {
                        when (selectedIndex) {
                            0 -> {
                                var isConnected by remember { mutableStateOf(false) }
                                val deviceName = "Mi Watch"
                                
                                val syncDaysOptions = listOf("3天", "7天", "10天", "15天", "30天")
                                var selectedSyncDaysIndex by remember { mutableIntStateOf(0) }
                                
                                var currentLocation by remember { mutableStateOf("未设置") }
                                val locationPickerLauncher = rememberLauncherForActivityResult(
                                    contract = ActivityResultContracts.StartActivityForResult()
                                ) { result ->
                                    if (result.resultCode == Activity.RESULT_OK) {
                                        result.data?.getStringExtra("location")?.let {
                                            currentLocation = it
                                        }
                                    }
                                }

                                Column(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Card(
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        BasicComponent(
                                            title = if (isConnected) "已连接设备" else "未连接设备",
                                            summary = if (isConnected) deviceName else "点击重试",
                                            onClick = { isConnected = !isConnected }
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
                                            onSelectedIndexChange = { selectedSyncDaysIndex = it }
                                        )
                                    }

                                    Button(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .padding(top = 16.dp),
                                        onClick = { /* Copy Data Logic */ },
                                        colors = ButtonDefaults.buttonColors()
                                    ) {
                                        Text("复制数据", color = if (isSystemInDarkTheme()) Color.White else Color.Black)
                                    }

                                    Button(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .padding(top = 16.dp),
                                        onClick = { /* Sync Data Logic */ },
                                        colors = ButtonDefaults.buttonColorsPrimary()
                                    ) {
                                        Text("同步数据", color = Color.White)
                                    }
                                }
                            }
                            1 -> {
                                var advancedSyncMode by remember { mutableStateOf(false) }
                                val showApiSettings = remember { mutableStateOf(false) }
                                val currentVersion = "v1.0.0"

                                Column(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Card(
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        SuperSwitch(
                                            title = "高级同步模式",
                                            summary = "启用后可同步更多天气数据",
                                            checked = advancedSyncMode,
                                            onCheckedChange = { advancedSyncMode = it }
                                        )
                                        SuperArrow(
                                            title = "API 设置",
                                            onClick = { showApiSettings.value = true }
                                        )
                                        SuperArrow(
                                            title = "检查更新",
                                            summary = currentVersion,
                                            onClick = { /* Check update logic */ }
                                        )
                                    }

                                    SmallTitle(text = "特别感谢")
                                    Card(
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    ) {
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
                                }

                                SuperBottomSheet(
                                    show = showApiSettings,
                                    title = "API 设置",
                                    onDismissRequest = { showApiSettings.value = false }
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("这里是 API 设置面板内容")
                                        // TODO: Add API settings content
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
