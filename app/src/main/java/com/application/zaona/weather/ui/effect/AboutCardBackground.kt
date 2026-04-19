package com.application.zaona.weather.ui.effect

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import top.yukonga.miuix.kmp.blur.RuntimeShader
import top.yukonga.miuix.kmp.blur.asBrush
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AboutCardBackground(
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    content: @Composable (BoxScope.() -> Unit) = {},
) {
    val shaderSupported = remember { isRuntimeShaderSupported() }
    val surface = MiuixTheme.colorScheme.surface
    val isDark = surface.luminance() < 0.5f
    val preset = remember(isDark) { AboutCardBackgroundConfig.get(isDark) }
    val painter = if (shaderSupported) remember { AboutCardBackgroundPainter() } else null
    val frameTimeSeconds = rememberFrameTimeSeconds(animate)
    val colorStage = remember { Animatable(0f) }

    LaunchedEffect(animate, preset) {
        if (!animate) return@LaunchedEffect
        var targetStage = 1f
        while (isActive) {
            delay((preset.colorInterpPeriod * 500).toLong())
            colorStage.animateTo(
                targetValue = targetStage,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 35f),
            )
            targetStage += 1f
        }
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawRect(surface)

            val stage = colorStage.value
            val base = stage.toInt()
            val fraction = stage - base

            val getColors = { index: Int ->
                when (index % 4) {
                    0 -> preset.colors2
                    1 -> preset.colors1
                    2 -> preset.colors2
                    else -> preset.colors3
                }
            }

            val start = getColors(base)
            val end = getColors(base + 1)
            val currentColors = FloatArray(16) { index ->
                start[index] + (end[index] - start[index]) * fraction
            }

            if (painter != null) {
                painter.updateResolution(size.width, size.height)
                painter.updatePresetIfNeeded(
                    contentHeight = size.height,
                    totalHeight = size.height,
                    totalWidth = size.width,
                    isDark = isDark,
                )
                painter.updateColors(currentColors)
                painter.updateAnimTime(frameTimeSeconds())
                drawRect(painter.brush)
            } else {
                drawFallbackBackground(
                    preset = preset,
                    currentColors = currentColors,
                    animTime = frameTimeSeconds(),
                )
            }
        }
        content()
    }
}

@Composable
private fun rememberFrameTimeSeconds(
    playing: Boolean,
): () -> Float {
    var time by remember { mutableFloatStateOf(0f) }
    var startOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playing) {
        if (!playing) {
            startOffset = time
            return@LaunchedEffect
        }

        val start = withFrameNanos { it }
        while (playing) {
            val now = withFrameNanos { it }
            time = startOffset + (now - start) / 1_000_000_000f
        }
    }

    return { time }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFallbackBackground(
    preset: AboutCardBackgroundConfig.Config,
    currentColors: FloatArray,
    animTime: Float,
) {
    val maxDimension = max(size.width, size.height)
    repeat(4) { index ->
        val colorIndex = index * 4
        val pointIndex = index * 3

        val color = Color(
            red = currentColors[colorIndex],
            green = currentColors[colorIndex + 1],
            blue = currentColors[colorIndex + 2],
            alpha = currentColors[colorIndex + 3],
        )

        val baseX = preset.points[pointIndex]
        val baseY = preset.points[pointIndex + 1]
        val radiusFactor = preset.points[pointIndex + 2]

        val animatedX = baseX + sin(animTime + baseY) * preset.pointOffset
        val animatedY = baseY + cos(animTime + baseX) * preset.pointOffset

        drawCircle(
            color = color,
            radius = maxDimension * radiusFactor * 0.68f,
            center = Offset(
                x = animatedX * size.width,
                y = animatedY * size.height,
            ),
        )
    }
}

private class AboutCardBackgroundPainter {
    val runtimeShader by lazy {
        RuntimeShader(ABOUT_CARD_BG_FRAG).also { shader ->
            shader.setFloatUniform("uTranslateY", 0f)
            shader.setFloatUniform("uNoiseScale", 1.5f)
            shader.setFloatUniform("uPointRadiusMulti", 1f)
            shader.setFloatUniform("uAlphaMulti", 1f)
            shader.setFloatUniform("uAlphaOffset", 0.1f)
            shader.setFloatUniform("uShadowOffset", 0.01f)
        }
    }

    val brush by lazy { runtimeShader.asBrush() }

    private val resolution = FloatArray(2)
    private val bound = FloatArray(4)

    private var animTime = Float.NaN
    private var isDarkCached: Boolean? = null
    private var presetApplied = false

    fun updateResolution(width: Float, height: Float) {
        if (resolution[0] == width && resolution[1] == height) return
        resolution[0] = width
        resolution[1] = height
        runtimeShader.setFloatUniform("uResolution", resolution)
    }

    fun updateAnimTime(time: Float) {
        if (animTime == time) return
        animTime = time
        runtimeShader.setFloatUniform("uAnimTime", animTime)
    }

    fun updateColors(colors: FloatArray) {
        runtimeShader.setFloatUniform("uColors", colors)
    }

    fun updatePresetIfNeeded(
        contentHeight: Float,
        totalHeight: Float,
        totalWidth: Float,
        isDark: Boolean,
    ) {
        if (presetApplied && isDarkCached == isDark) return
        updateBound(contentHeight, totalHeight, totalWidth)
        applyPreset(isDark)
        isDarkCached = isDark
        presetApplied = true
    }

    private fun applyPreset(isDark: Boolean) {
        val preset = AboutCardBackgroundConfig.get(isDark)

        runtimeShader.setFloatUniform("uPoints", preset.points)
        runtimeShader.setFloatUniform("uPointOffset", preset.pointOffset)
        runtimeShader.setFloatUniform("uLightOffset", preset.lightOffset)
        runtimeShader.setFloatUniform("uSaturateOffset", preset.saturateOffset)
        runtimeShader.setFloatUniform("uBound", bound)
        runtimeShader.setFloatUniform("uShadowColorMulti", preset.shadowColorMulti)
        runtimeShader.setFloatUniform("uShadowColorOffset", preset.shadowColorOffset)
        runtimeShader.setFloatUniform("uShadowNoiseScale", preset.shadowNoiseScale)
    }

    private fun updateBound(
        contentHeight: Float,
        totalHeight: Float,
        totalWidth: Float,
    ) {
        val heightRatio = contentHeight / totalHeight
        if (totalWidth <= totalHeight) {
            bound[0] = 0f
            bound[1] = 1f - heightRatio
            bound[2] = 1f
            bound[3] = heightRatio
        } else {
            val aspectRatio = totalWidth / totalHeight
            val contentCenterY = 1f - heightRatio / 2f
            bound[0] = 0f
            bound[1] = contentCenterY - aspectRatio / 2f
            bound[2] = 1f
            bound[3] = aspectRatio
        }
    }
}

private object AboutCardBackgroundConfig {
    class Config(
        val points: FloatArray,
        val colors1: FloatArray,
        val colors2: FloatArray,
        val colors3: FloatArray,
        val colorInterpPeriod: Float,
        val lightOffset: Float,
        val saturateOffset: Float,
        val pointOffset: Float,
        val shadowColorMulti: Float = 0.3f,
        val shadowColorOffset: Float = 0.3f,
        val shadowNoiseScale: Float = 5.0f,
    )

    private val phoneLight = Config(
        points = floatArrayOf(0.8f, 0.2f, 1.0f, 0.8f, 0.9f, 1.0f, 0.2f, 0.9f, 1.0f, 0.2f, 0.2f, 1.0f),
        colors1 = floatArrayOf(1.0f, 0.9f, 0.94f, 1.0f, 1.0f, 0.84f, 0.89f, 1.0f, 0.97f, 0.73f, 0.82f, 1.0f, 0.64f, 0.65f, 0.98f, 1.0f),
        colors2 = floatArrayOf(0.58f, 0.74f, 1.0f, 1.0f, 1.0f, 0.9f, 0.93f, 1.0f, 0.74f, 0.76f, 1.0f, 1.0f, 0.97f, 0.77f, 0.84f, 1.0f),
        colors3 = floatArrayOf(0.98f, 0.86f, 0.9f, 1.0f, 0.6f, 0.73f, 0.98f, 1.0f, 0.92f, 0.93f, 1.0f, 1.0f, 0.56f, 0.69f, 1.0f, 1.0f),
        colorInterpPeriod = 5.0f,
        lightOffset = 0.1f,
        saturateOffset = 0.2f,
        pointOffset = 0.2f,
    )

    private val phoneDark = Config(
        points = floatArrayOf(0.8f, 0.2f, 1.0f, 0.8f, 0.9f, 1.0f, 0.2f, 0.9f, 1.0f, 0.2f, 0.2f, 1.0f),
        colors1 = floatArrayOf(0.2f, 0.06f, 0.88f, 0.4f, 0.3f, 0.14f, 0.55f, 0.5f, 0.0f, 0.64f, 0.96f, 0.5f, 0.11f, 0.16f, 0.83f, 0.4f),
        colors2 = floatArrayOf(0.07f, 0.15f, 0.79f, 0.5f, 0.62f, 0.21f, 0.67f, 0.5f, 0.06f, 0.25f, 0.84f, 0.5f, 0.0f, 0.2f, 0.78f, 0.5f),
        colors3 = floatArrayOf(0.58f, 0.3f, 0.74f, 0.4f, 0.27f, 0.18f, 0.6f, 0.5f, 0.66f, 0.26f, 0.62f, 0.5f, 0.12f, 0.16f, 0.7f, 0.6f),
        colorInterpPeriod = 8.0f,
        lightOffset = 0.0f,
        saturateOffset = 0.17f,
        pointOffset = 0.4f,
    )

    fun get(isDark: Boolean): Config = if (isDark) phoneDark else phoneLight
}

private const val ABOUT_CARD_BG_FRAG = """
    uniform vec2 uResolution;
    uniform shader uTex;
    uniform shader uTexBitmap;
    uniform vec2 uTexWH;

    uniform float uAnimTime;
    uniform vec4 uBound;
    uniform float uTranslateY;
    uniform vec3 uPoints[4];
    uniform vec4 uColors[4];
    uniform float uAlphaMulti;
    uniform float uNoiseScale;
    uniform float uPointOffset;
    uniform float uPointRadiusMulti;
    uniform float uSaturateOffset;
    uniform float uLightOffset;
    uniform float uAlphaOffset;
    uniform float uShadowColorMulti;
    uniform float uShadowColorOffset;
    uniform float uShadowNoiseScale;
    uniform float uShadowOffset;

    vec3 rgb2hsv(vec3 c) {
        vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
        vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
        vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
        float d = q.x - min(q.w, q.y);
        float e = 1.0e-10;
        return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
    }

    vec3 hsv2rgb(vec3 c) {
        vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
        vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
        return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
    }

    float hash(vec2 p) {
        vec3 p3 = fract(vec3(p.xyx) * 0.13);
        p3 += dot(p3, p3.yzx + 3.333);
        return fract((p3.x + p3.y) * p3.z);
    }

    float perlin(vec2 x) {
        vec2 i = floor(x);
        vec2 f = fract(x);
        float a = hash(i);
        float b = hash(i + vec2(1.0, 0.0));
        float c = hash(i + vec2(0.0, 1.0));
        float d = hash(i + vec2(1.0, 1.0));
        vec2 u = f * f * (3.0 - 2.0 * f);
        return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
    }

    float gradientNoise(in vec2 uv) {
        return fract(52.9829189 * fract(dot(uv, vec2(0.06711056, 0.00583715))));
    }

    vec4 main(vec2 fragCoord){
        vec2 vUv = fragCoord / uResolution;
        vUv.y = 1.0 - vUv.y;
        vec2 uv = vUv;
        uv -= vec2(0., uTranslateY);
        uv.xy -= uBound.xy;
        uv.xy /= uBound.zw;

        vec3 hsv;
        vec4 color = vec4(0.0);
        float noiseValue = perlin(vUv * uNoiseScale + vec2(-uAnimTime, -uAnimTime));

        for (int i = 0; i < 4; i++){
            vec4 pointColor = uColors[i];
            pointColor.rgb *= pointColor.a;
            vec2 point = uPoints[i].xy;
            float rad = uPoints[i].z * uPointRadiusMulti;

            point.x += sin(uAnimTime + point.y) * uPointOffset;
            point.y += cos(uAnimTime + point.x) * uPointOffset;

            float d = distance(uv, point);
            float pct = smoothstep(rad, 0., d);

            color.rgb = mix(color.rgb, pointColor.rgb, pct);
            color.a = mix(color.a, pointColor.a, pct);
        }

        float oppositeNoise = smoothstep(0., 1., noiseValue);
        color.rgb /= color.a;
        hsv = rgb2hsv(color.rgb);
        hsv.y = mix(hsv.y, 0.0, oppositeNoise * uSaturateOffset);
        color.rgb = hsv2rgb(hsv);
        color.rgb += oppositeNoise * uLightOffset;

        color.a = clamp(color.a, 0., 1.);
        color.a *= uAlphaMulti;
        color += (1.0 / 255.0) * gradientNoise(fragCoord.xy) - (0.5 / 255.0);

        return vec4(color.rgb * color.a, color.a);
    }
"""
