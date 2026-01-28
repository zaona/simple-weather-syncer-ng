package com.application.zaona.weather

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.application.zaona.weather.model.Sponsor
import com.application.zaona.weather.service.AfdianService
import com.application.zaona.weather.ui.theme.SimpleweathersyncerngTheme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height

import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenStarted
import kotlinx.coroutines.launch

class SponsorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimpleweathersyncerngTheme {
                val topBarState = rememberTopAppBarState()
                val scrollBehavior = MiuixScrollBehavior(state = topBarState)
                val context = LocalContext.current
                
                var sponsors by remember { mutableStateOf<List<Sponsor>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }
                var loadProgress by remember { mutableStateOf(0f) }
                var errorMessage by remember { mutableStateOf<String?>(null) }
                
                fun loadData() {
                    isLoading = true
                    errorMessage = null
                    loadProgress = 0f
                    // Launch in a coroutine
                    lifecycleScope.launch {
                        whenStarted {
                            try {
                                sponsors = AfdianService.fetchSponsors { progress ->
                                    loadProgress = progress
                                }
                                if (sponsors.isEmpty()) {
                                    errorMessage = "暂无赞助者数据"
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                errorMessage = "网络错误"
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    loadData()
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = "赞助者鸣谢",
                            navigationIcon = {
                                IconButton(
                                    modifier = Modifier.padding(start = 12.dp),
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
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = innerPadding
                    ) {
                        item {
                            Button(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 16.dp, bottom = 16.dp),
                                onClick = {
                                    // Open sponsor link
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://afdian.com/a/zaona"))
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColorsPrimary()
                            ) {
                                Text("前往赞助", color = Color.White)
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                if (isLoading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            CircularProgressIndicator(
                                                progress = loadProgress
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "正在加载 ${(loadProgress * 100).toInt()}%",
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                } else if (errorMessage != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = errorMessage ?: "未知错误",
                                                color = Color.Red,
                                            )
                                        }
                                    }
                                } else {
                                    sponsors.forEach { sponsor ->
                                        BasicComponent(
                                            title = sponsor.name,
                                            onClick = { /* Optional: Link to sponsor profile */ }
                                        )
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
