package com.application.zaona.weather.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.application.zaona.weather.ui.effect.AboutCardBackground
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun SponsorPromoCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val surface = MiuixTheme.colorScheme.surface
    val isDark = surface.luminance() < 0.5f
    val titleColor = MiuixTheme.colorScheme.onSurface
    val summaryColor = titleColor.copy(alpha = if (isDark) 0.78f else 0.72f)
    val chipBackground = Color.White.copy(alpha = if (isDark) 0.16f else 0.32f)
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
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp)
        ) {
            AboutCardBackground(
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(overlayBrush)
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                            imageVector = MiuixIcons.Favorites,
                            contentDescription = null,
                            tint = titleColor,
                        )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "支持本项目",
                        color = titleColor,
                        style = MiuixTheme.textStyles.title3,
                    )
                    Text(
                        text = "赞助可帮助维护天气服务并支持后续功能开发。",
                        color = summaryColor,
                        style = MiuixTheme.textStyles.footnote1,
                    )
                }
            }
        }
    }
}
