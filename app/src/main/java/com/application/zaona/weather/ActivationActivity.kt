package com.application.zaona.weather

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.application.zaona.weather.ui.effect.AboutCardBackground
import com.application.zaona.weather.ui.theme.SimpleweathersyncerngTheme
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Create
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.MindMap
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.shapes.SmoothRoundedCornerShape
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode

private const val AFDIAN_URL = "https://afdian.com/a/zaona"

private data class BenefitItemUi(
    val icon: ImageVector,
    val title: String,
    val summary: String,
)

class ActivationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimpleweathersyncerngTheme {
                val topBarState = rememberTopAppBarState()
                val scrollBehavior = MiuixScrollBehavior(state = topBarState)
                val context = LocalContext.current
                val surface = MiuixTheme.colorScheme.surface
                val onSurface = MiuixTheme.colorScheme.onSurface
                val isDark = surface.luminance() < 0.5f
                val backdrop = rememberLayerBackdrop()
                val aboutLogoBlend = remember(isDark) {
                    if (isDark) {
                        listOf(
                            BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
                            BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
                            BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
                        )
                    } else {
                        listOf(
                            BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
                            BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
                            BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
                        )
                    }
                }
                val secondaryTextColor = onSurface.copy(alpha = if (isDark) 0.78f else 0.7f)
                val overlayBrush = remember(surface, isDark) {
                    Brush.verticalGradient(
                        colors = listOf(
                            surface.copy(alpha = if (isDark) 0.16f else 0.05f),
                            surface.copy(alpha = if (isDark) 0.56f else 0.34f),
                            surface.copy(alpha = if (isDark) 0.9f else 0.76f),
                        ),
                    )
                }
                val bottomActionBrush = remember(surface) {
                    Brush.verticalGradient(
                        colors = listOf(
                            surface.copy(alpha = 0f),
                            surface.copy(alpha = 0.30f),
                            surface.copy(alpha = 0.60f),
                            surface.copy(alpha = 0.80f),
                            surface.copy(alpha = 0.90f),
                        ),
                    )
                }
                val cardColor = surface.copy(alpha = if (isDark) 0.58f else 0.76f)
                val benefits = remember {
                    listOf(
                        BenefitItemUi(
                            icon = MiuixIcons.Backup,
                            title = "天气数据离线存储",
                            summary = "自由选择存储天数，无惧数据过期"
                        ),
                        BenefitItemUi(
                            icon = MiuixIcons.Create,
                            title = "界面美观，数据简明易懂",
                            summary = "可查看十余种详细天气数据"
                        ),
                        BenefitItemUi(
                            icon = MiuixIcons.Link,
                            title = "数据传输方便",
                            summary = "配置完成后只需每次点击同步按钮"
                        ),
                        BenefitItemUi(
                            icon = MiuixIcons.MindMap,
                            title = "一次购买，多设备可用",
                            summary = "无需为每个手环手表单独购买"
                        ),
                        BenefitItemUi(
                            icon = MiuixIcons.Report,
                            title = "持续更新，服务稳定",
                            summary = "你的支持将使得本项目越来越好"
                        ),
                    )
                }

                fun openAfdianPage() {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AFDIAN_URL)))
                }

                Scaffold(
                    contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
                    topBar = {
                        TopAppBar(
                            title = "激活完整功能",
                            color = Color.Transparent,
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
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .layerBackdrop(backdrop)
                        ) {
                            AboutCardBackground(
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(overlayBrush)
                            )
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .overScrollVertical()
                                .scrollEndHaptic()
                                .nestedScroll(scrollBehavior.nestedScrollConnection),
                            contentPadding = PaddingValues(bottom = 176.dp)
                        ) {
                            item {
                                Column(
                                    modifier = Modifier.padding(vertical = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    PriceHero(
                                        backdrop = backdrop,
                                        aboutLogoBlend = aboutLogoBlend
                                    )

                                    Card(
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp)
                                            .padding(bottom = 12.dp),
                                        colors = CardDefaults.defaultColors(
                                            color = cardColor,
                                            contentColor = onSurface,
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(20.dp),
                                            verticalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            benefits.forEach { benefit ->
                                                BenefitItem(
                                                    icon = benefit.icon,
                                                    title = benefit.title,
                                                    summary = benefit.summary,
                                                    summaryColor = secondaryTextColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(bottomActionBrush)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(start = 12.dp, end = 12.dp, top = 32.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { openAfdianPage() },
                                    colors = ButtonDefaults.buttonColorsPrimary()
                                ) {
                                    Text("立即激活", color = Color.White)
                                }
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { },
                                    colors = ButtonDefaults.buttonColors()
                                ) {
                                    Text("复制识别码")
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
private fun PriceHero(
    backdrop: LayerBackdrop,
    aboutLogoBlend: List<BlendColorEntry>,
) {
    Text(
        text = "¥1.99",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .textureBlur(
                backdrop = backdrop,
                shape = SmoothRoundedCornerShape(24.dp),
                blurRadius = 150f,
                colors = BlurColors(
                    blendColors = aboutLogoBlend,
                ),
                contentBlendMode = ComposeBlendMode.DstIn,
                enabled = isRenderEffectSupported(),
            ),
        color = MiuixTheme.colorScheme.onBackground,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 68.sp,
    )
}

@Composable
private fun BenefitItem(
    icon: ImageVector,
    title: String,
    summary: String,
    summaryColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = summary,
                color = summaryColor,
                style = MiuixTheme.textStyles.footnote1
            )
        }
    }
}
