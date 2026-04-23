package com.application.zaona.weather

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.application.zaona.weather.ui.theme.SimpleweathersyncerngTheme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

private const val USER_AFDIAN_URL = "https://afdian.com/a/zaona"
private const val MAX_BOUND_DEVICE_COUNT = 3
private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L

private data class BoundDeviceUi(
    val name: String,
    val summary: String,
    val isCurrentDevice: Boolean = false,
)

class UserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimpleweathersyncerngTheme {
                val context = LocalContext.current
                val topBarState = rememberTopAppBarState()
                val scrollBehavior = MiuixScrollBehavior(state = topBarState)
                val onSurface = MiuixTheme.colorScheme.onSurface
                val secondaryTextColor = onSurface.copy(alpha = 0.7f)
                val activationTimestamp = remember {
                    System.currentTimeMillis() - 23L * MILLIS_PER_DAY
                }
                val deviceCandidates = remember {
                    listOf(
                        BoundDeviceUi(
                            name = "Xiaomi Smart Band 9 Pro",
                            summary = "最近同步：今天 09:42",
                            isCurrentDevice = true,
                        ),
                        BoundDeviceUi(
                            name = "Redmi Watch 5",
                            summary = "最近同步：昨天 21:16",
                        ),
                        BoundDeviceUi(
                            name = "Xiaomi Smart Band 8",
                            summary = "等待首次同步",
                        ),
                    )
                }
                val boundDevices = remember {
                    mutableStateListOf<BoundDeviceUi>().apply {
                        addAll(deviceCandidates.take(2))
                    }
                }
                var isLoggedIn by remember { mutableStateOf(true) }
                val daysSinceActivation = remember(activationTimestamp) {
                    ((System.currentTimeMillis() - activationTimestamp) / MILLIS_PER_DAY).coerceAtLeast(0)
                }

                fun openAfdianPage() {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(USER_AFDIAN_URL)))
                }

                fun logout() {
                    isLoggedIn = false
                    boundDevices.clear()
                }

                Scaffold(
                    contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
                    topBar = {
                        TopAppBar(
                            title = "账号与设备",
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
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = innerPadding
                    ) {
                        item {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AccountSummaryCard(
                                    primaryMetric = if (isLoggedIn) "${daysSinceActivation} 天" else "--",
                                    primaryLabel = "已激活",
                                    secondaryMetric = "${boundDevices.size}/$MAX_BOUND_DEVICE_COUNT",
                                    secondaryLabel = "绑定设备",
                                    tertiaryMetric = if (isLoggedIn) "正常" else "未登录",
                                    tertiaryLabel = "账号状态",
                                    secondaryTextColor = secondaryTextColor,
                                )
                            }
                        }

                        item {
                            SmallTitle(text = "账号信息")
                            Card(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 12.dp)
                            ) {
                                ArrowPreference(
                                    title = "爱发电账号",
                                    summary = if (isLoggedIn) "zaona_supporter" else "当前未绑定爱发电账号",
                                    onClick = { openAfdianPage() },
                                )
                            }
                        }

                        item {
                            SmallTitle(text = "设备管理")
                            Card(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 12.dp)
                            ) {
                                if (boundDevices.isEmpty()) {
                                    BasicComponent(
                                        title = "暂无绑定设备",
                                        summary = "登录后可绑定最多 3 台可穿戴设备"
                                    )
                                } else {
                                    boundDevices.forEach { device ->
                                        BasicComponent(
                                            title = device.name,
                                            summary = device.summary,
                                            endActions = if (device.isCurrentDevice) {
                                                {
                                                    Text(
                                                        text = "当前设备",
                                                        color = secondaryTextColor
                                                    )
                                                }
                                            } else {
                                                null
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Button(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 4.dp, bottom = 16.dp),
                                onClick = { logout() },
                                colors = ButtonDefaults.buttonColors()
                            ) {
                                Text(
                                    text = "退出登录",
                                    color = MiuixTheme.colorScheme.error
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountSummaryCard(
    primaryMetric: String,
    primaryLabel: String,
    secondaryMetric: String,
    secondaryLabel: String,
    tertiaryMetric: String,
    tertiaryLabel: String,
    secondaryTextColor: Color,
) {
    Card(
        colors = CardDefaults.defaultColors()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SummaryMetric(
                    modifier = Modifier.weight(1f),
                    value = primaryMetric,
                    label = primaryLabel,
                    secondaryTextColor = secondaryTextColor,
                )
                SummaryMetric(
                    modifier = Modifier.weight(1f),
                    value = secondaryMetric,
                    label = secondaryLabel,
                    secondaryTextColor = secondaryTextColor,
                )
                SummaryMetric(
                    modifier = Modifier.weight(1f),
                    value = tertiaryMetric,
                    label = tertiaryLabel,
                    secondaryTextColor = secondaryTextColor,
                )
            }
        }
    }
}

@Composable
private fun SummaryMetric(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    secondaryTextColor: Color,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
        )
        Text(
            text = label,
            color = secondaryTextColor,
            fontSize = 13.sp,
        )
    }
}
