package com.application.zaona.weather

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.application.zaona.weather.model.Announcement
import com.application.zaona.weather.service.AnnouncementService
import com.application.zaona.weather.ui.component.MarkdownText
import com.application.zaona.weather.ui.theme.SimpleweathersyncerngTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

class AnnouncementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimpleweathersyncerngTheme {
                val topBarState = rememberTopAppBarState()
                val scrollBehavior = MiuixScrollBehavior(state = topBarState)
                val context = LocalContext.current

                var announcements by remember { mutableStateOf<List<Announcement>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }
                var errorMessage by remember { mutableStateOf<String?>(null) }

                // Detail dialog state
                val showDetailDialog = remember { mutableStateOf(false) }
                var selectedAnnouncement by remember { mutableStateOf<Announcement?>(null) }

                // Read announcement IDs (observable so UI updates on markAsRead)
                var readIds by remember {
                    val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                    mutableStateOf(prefs.getStringSet("read_announcement_ids", emptySet()) ?: emptySet())
                }

                fun markAsRead(id: String) {
                    val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                    val current = prefs.getStringSet("read_announcement_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
                    current.add(id)
                    prefs.edit().putStringSet("read_announcement_ids", current).apply()
                    readIds = current
                }

                fun loadData() {
                    isLoading = true
                    errorMessage = null
                    lifecycleScope.launch {
                        try {
                            val result = AnnouncementService.fetchAnnouncements()
                            announcements = result.announcements.filter { it.enabled }
                            if (announcements.isEmpty()) {
                                errorMessage = "暂无公告"
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            errorMessage = "网络错误"
                        } finally {
                            isLoading = false
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    loadData()
                }

                Scaffold(
                    contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
                    topBar = {
                        TopAppBar(
                            title = "公告",
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
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .overScrollVertical()
                            .scrollEndHaptic()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = innerPadding
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(12.dp))

                            Card(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 12.dp)
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
                                            InfiniteProgressIndicator()
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
                                                color = MiuixTheme.colorScheme.error
                                            )
                                        }
                                    }
                                } else {
                                    announcements.forEach { announcement ->
                                        BasicComponent(
                                            title = announcement.title,
                                            summary = announcement.publishDate,
                                            onClick = {
                                                selectedAnnouncement = announcement
                                                showDetailDialog.value = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Announcement detail dialog
                WindowDialog(
                    title = selectedAnnouncement?.title ?: "",
                    show = showDetailDialog.value,
                    onDismissRequest = { showDetailDialog.value = false }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        selectedAnnouncement?.let { announcement ->
                            MarkdownText(
                                markdown = announcement.content,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            TextButton(
                                text = "已读",
                                onClick = {
                                    selectedAnnouncement?.let { markAsRead(it.id) }
                                    showDetailDialog.value = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
