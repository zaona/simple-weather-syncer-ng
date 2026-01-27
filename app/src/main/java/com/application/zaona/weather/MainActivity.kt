package com.application.zaona.weather

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.application.zaona.weather.ui.theme.SimpleweathersyncerngTheme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDropdown
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
                                }
                            }
                            1 -> Greeting("Settings")
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
