package com.application.zaona.weather.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 轻量 Markdown 解析器，将常用 Markdown 语法渲染为 Miuix Text 组件。
 *
 * 支持的块级语法：## 标题、- 无序列表、空行分隔段落。
 * 支持的行内语法：**粗体**、`行内代码`、[链接](url)（可点击）。
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        val lines = markdown.split("\n")
        for (i in lines.indices) {
            val line = lines[i].trimEnd()
            when {
                // 空行 → 段落间距
                line.isBlank() -> {
                    if (i > 0 && i < lines.size - 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                // ## 标题
                line.startsWith("## ") -> {
                    val heading = line.removePrefix("## ").trim()
                    Text(
                        text = heading,
                        style = MiuixTheme.textStyles.headline1,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = if (i > 0) 12.dp else 0.dp, bottom = 4.dp)
                    )
                }
                // - 无序列表
                line.startsWith("- ") || line.startsWith("* ") -> {
                    val content = line.substring(2).trim()
                    Row(modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)) {
                        Text(
                            text = "• ",
                            style = MiuixTheme.textStyles.main,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        InlineMarkdownText(
                            text = content,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                // 普通段落
                else -> {
                    InlineMarkdownText(text = line)
                }
            }
        }
    }
}

/**
 * 解析行内 Markdown 格式：**粗体**、`行内代码`、[链接](url)（LinkAnnotation 可点击）。
 */
@Composable
fun InlineMarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    val linkColor = MiuixTheme.colorScheme.primary
    val bodyColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val codeBgColor = MiuixTheme.colorScheme.disabledSecondary.copy(alpha = 0.3f)

    val annotated = remember(text, linkColor, codeBgColor) {
        buildAnnotatedString {
            var i = 0
            while (i < text.length) {
                when {
                    // **粗体**
                    text.startsWith("**", startIndex = i) -> {
                        val end = text.indexOf("**", startIndex = i + 2)
                        if (end != -1) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(text.substring(i + 2, end))
                            }
                            i = end + 2
                        } else { append(text[i]); i++ }
                    }
                    // `行内代码`
                    text[i] == '`' -> {
                        val end = text.indexOf('`', startIndex = i + 1)
                        if (end != -1) {
                            withStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    background = codeBgColor
                                )
                            ) {
                                append(text.substring(i + 1, end))
                            }
                            i = end + 1
                        } else { append(text[i]); i++ }
                    }
                    // [链接](url)
                    text.startsWith("[", startIndex = i) -> {
                        val cb = text.indexOf("](", startIndex = i + 1)
                        val cp = if (cb != -1) text.indexOf(")", startIndex = cb + 2) else -1
                        if (cb != -1 && cp != -1) {
                            val linkText = text.substring(i + 1, cb)
                            val url = text.substring(cb + 2, cp)
                            val start = length
                            append(linkText)
                            addLink(
                                LinkAnnotation.Url(
                                    url = url,
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                            color = linkColor,
                                            fontWeight = FontWeight.Medium,
                                            textDecoration = TextDecoration.Underline
                                        )
                                    )
                                ),
                                start = start,
                                end = length
                            )
                            i = cp + 1
                        } else { append(text[i]); i++ }
                    }
                    else -> { append(text[i]); i++ }
                }
            }
        }
    }

    Text(
        text = annotated,
        style = MiuixTheme.textStyles.body1.copy(color = bodyColor),
        modifier = modifier
    )
}
