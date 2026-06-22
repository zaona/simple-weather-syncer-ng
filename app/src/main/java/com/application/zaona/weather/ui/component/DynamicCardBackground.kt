package com.application.zaona.weather.ui.component

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
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin
import top.yukonga.miuix.kmp.shader.RuntimeShader
import top.yukonga.miuix.kmp.shader.asBrush
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DynamicCardBackground(
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    content: @Composable (BoxScope.() -> Unit) = {},
) {
    val shaderSupported = remember { isRuntimeShaderSupported() }
    val surface = MiuixTheme.colorScheme.surfaceContainer
    val isDark = surface.luminance() < 0.5f
    val preset = remember(isDark) { DynamicCardBackgroundConfig.get(isDark) }
    val painter = if (shaderSupported) remember { DynamicCardBackgroundPainter() } else null
    val frameTimeSeconds = rememberFrameTimeSeconds(animate)
    val colorStage = remember { Animatable(0f) }

    LaunchedEffect(animate, preset) {
        if (!animate) return@LaunchedEffect
        var targetStage = colorStage.value.toInt() + 1f
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

            val animTime = frameTimeSeconds()
            if (painter == null) return@Canvas

            painter.updateResolution(size.width, size.height)
            painter.updatePresetIfNeeded(
                contentHeight = size.height,
                totalHeight = size.height,
                totalWidth = size.width,
                isDark = isDark,
            )
            painter.updateColors(preset, colorStage.value)
            painter.updatePointsAnim(animTime, preset)
            painter.updateAnimTime(animTime)
            drawRect(painter.brush)
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

private fun colorsForStage(
    preset: DynamicCardBackgroundConfig.Config,
    stage: Float,
): FloatArray {
    val base = stage.toInt()
    val fraction = stage - base
    val start = colorsForCycleIndex(preset, base)
    val end = colorsForCycleIndex(preset, base + 1)
    return FloatArray(16) { index ->
        start[index] + (end[index] - start[index]) * fraction
    }
}

private fun colorsForCycleIndex(
    preset: DynamicCardBackgroundConfig.Config,
    index: Int,
): FloatArray = when (index.mod(4)) {
    1 -> preset.colors1
    3 -> preset.colors3
    else -> preset.colors2
}

private class DynamicCardBackgroundPainter {
    val runtimeShader by lazy {
        RuntimeShader(OS3_CARD_BG_FRAG).also { shader ->
            shader.setFloatUniform("uTranslateY", 0f)
            shader.setFloatUniform("uNoiseScale", 1.5f)
            shader.setFloatUniform("uPointRadiusMulti", 1f)
            shader.setFloatUniform("uAlphaMulti", 1f)
        }
    }

    val brush by lazy { runtimeShader.asBrush() }

    private val resolution = FloatArray(2)
    private val bound = FloatArray(4)
    private val colorsBuffer = FloatArray(16)
    private val pointsAnimBuffer = FloatArray(8)

    private var animTime = Float.NaN
    private var isDarkCached: Boolean? = null
    private var presetApplied = false
    private var cachedContentHeight = Float.NaN
    private var cachedTotalHeight = Float.NaN
    private var cachedTotalWidth = Float.NaN
    private var cachedColorStage = Float.NaN
    private var cachedColorsPreset: DynamicCardBackgroundConfig.Config? = null
    private var cachedPointsAnimTime = Float.NaN
    private var cachedPointsAnimPreset: DynamicCardBackgroundConfig.Config? = null

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

    fun updatePointsAnim(
        time: Float,
        preset: DynamicCardBackgroundConfig.Config,
    ) {
        if (cachedPointsAnimTime == time && cachedPointsAnimPreset === preset) return

        repeat(4) { index ->
            val sourceX = preset.points[index * 3]
            val sourceY = preset.points[index * 3 + 1]
            val animatedX = sourceX + sin(time + sourceY) * preset.pointOffset
            val animatedY = sourceY + cos(time + animatedX) * preset.pointOffset
            pointsAnimBuffer[index * 2] = animatedX
            pointsAnimBuffer[index * 2 + 1] = animatedY
        }
        runtimeShader.setFloatUniform("uPointsAnim", pointsAnimBuffer)

        cachedPointsAnimTime = time
        cachedPointsAnimPreset = preset
    }

    fun updateColors(
        preset: DynamicCardBackgroundConfig.Config,
        stage: Float,
    ) {
        if (cachedColorsPreset === preset && cachedColorStage == stage) return

        val colors = colorsForStage(preset, stage)
        for (index in colors.indices) {
            colorsBuffer[index] = colors[index]
        }
        runtimeShader.setFloatUniform("uColors", colorsBuffer)

        cachedColorsPreset = preset
        cachedColorStage = stage
    }

    fun updatePresetIfNeeded(
        contentHeight: Float,
        totalHeight: Float,
        totalWidth: Float,
        isDark: Boolean,
    ) {
        if (cachedContentHeight != contentHeight ||
            cachedTotalHeight != totalHeight ||
            cachedTotalWidth != totalWidth
        ) {
            updateBound(contentHeight, totalHeight, totalWidth)
            runtimeShader.setFloatUniform("uBound", bound)
            cachedContentHeight = contentHeight
            cachedTotalHeight = totalHeight
            cachedTotalWidth = totalWidth
        }

        if (presetApplied && isDarkCached == isDark) return
        applyPreset(isDark)
        isDarkCached = isDark
        presetApplied = true
    }

    private fun applyPreset(isDark: Boolean) {
        val preset = DynamicCardBackgroundConfig.get(isDark)
        runtimeShader.setFloatUniform("uPoints", preset.points)
        runtimeShader.setFloatUniform("uLightOffset", preset.lightOffset)
        runtimeShader.setFloatUniform("uSaturateOffset", preset.saturateOffset)
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

private object DynamicCardBackgroundConfig {
    class Config(
        val points: FloatArray,
        val colors1: FloatArray,
        val colors2: FloatArray,
        val colors3: FloatArray,
        val colorInterpPeriod: Float,
        val lightOffset: Float,
        val saturateOffset: Float,
        val pointOffset: Float,
    )

    private val os3PhoneLight = Config(
        points = floatArrayOf(0.8f, 0.2f, 1.0f, 0.8f, 0.9f, 1.0f, 0.2f, 0.9f, 1.0f, 0.2f, 0.2f, 1.0f),
        colors1 = floatArrayOf(1.0f, 0.9f, 0.94f, 1.0f, 1.0f, 0.84f, 0.89f, 1.0f, 0.97f, 0.73f, 0.82f, 1.0f, 0.64f, 0.65f, 0.98f, 1.0f),
        colors2 = floatArrayOf(0.58f, 0.74f, 1.0f, 1.0f, 1.0f, 0.9f, 0.93f, 1.0f, 0.74f, 0.76f, 1.0f, 1.0f, 0.97f, 0.77f, 0.84f, 1.0f),
        colors3 = floatArrayOf(0.98f, 0.86f, 0.9f, 1.0f, 0.6f, 0.73f, 0.98f, 1.0f, 0.92f, 0.93f, 1.0f, 1.0f, 0.56f, 0.69f, 1.0f, 1.0f),
        colorInterpPeriod = 5.0f,
        lightOffset = 0.1f,
        saturateOffset = 0.2f,
        pointOffset = 0.2f,
    )

    private val os3PhoneDark = Config(
        points = floatArrayOf(0.8f, 0.2f, 1.0f, 0.8f, 0.9f, 1.0f, 0.2f, 0.9f, 1.0f, 0.2f, 0.2f, 1.0f),
        colors1 = floatArrayOf(0.2f, 0.06f, 0.88f, 0.4f, 0.3f, 0.14f, 0.55f, 0.5f, 0.0f, 0.64f, 0.96f, 0.5f, 0.11f, 0.16f, 0.83f, 0.4f),
        colors2 = floatArrayOf(0.07f, 0.15f, 0.79f, 0.5f, 0.62f, 0.21f, 0.67f, 0.5f, 0.06f, 0.25f, 0.84f, 0.5f, 0.0f, 0.2f, 0.78f, 0.5f),
        colors3 = floatArrayOf(0.58f, 0.3f, 0.74f, 0.4f, 0.27f, 0.18f, 0.6f, 0.5f, 0.66f, 0.26f, 0.62f, 0.5f, 0.12f, 0.16f, 0.7f, 0.6f),
        colorInterpPeriod = 8.0f,
        lightOffset = 0.0f,
        saturateOffset = 0.17f,
        pointOffset = 0.4f,
    )

    fun get(isDark: Boolean): Config = if (isDark) os3PhoneDark else os3PhoneLight
}

private const val OS3_CARD_BG_FRAG = """
    uniform vec2 uResolution;
    uniform float uAnimTime;
    uniform vec4 uBound;
    uniform float uTranslateY;
    uniform vec3 uPoints[4];
    uniform vec2 uPointsAnim[4];
    uniform vec4 uColors[4];
    uniform float uAlphaMulti;
    uniform float uNoiseScale;
    uniform float uPointRadiusMulti;
    uniform float uSaturateOffset;
    uniform float uLightOffset;

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
        vec2 i = floor(x); vec2 f = fract(x);

        float a = hash(i); float b = hash(i + vec2(1.0, 0.0));
        float c = hash(i + vec2(0.0, 1.0)); float d = hash(i + vec2(1.0, 1.0));

        vec2 u = f * f * (3.0 - 2.0 * f);
        return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
    }

    float gradientNoise(in vec2 uv) {
        return fract(52.9829189 * fract(dot(uv, vec2(0.06711056, 0.00583715))));
    }

    vec4 main(vec2 fragCoord){
        vec2 vUv = fragCoord/uResolution;
        vUv.y = 1.0-vUv.y;
        vec2 uv = vUv;
        uv -= vec2(0., uTranslateY);
        uv.xy -= uBound.xy;
        uv.xy /= uBound.zw;

        vec4 color = vec4(0.0);
        float noiseValue = perlin(vUv * uNoiseScale + vec2(-uAnimTime, -uAnimTime));

        for (int i = 0; i < 4; i++){
            vec4 pointColor = uColors[i];
            pointColor.rgb *= pointColor.a;
            vec2 point = uPointsAnim[i];
            float rad = uPoints[i].z * uPointRadiusMulti;

            float d = distance(uv, point);
            float pct = smoothstep(rad, 0., d);
            color.rgb = mix(color.rgb, pointColor.rgb, pct);
            color.a = mix(color.a, pointColor.a, pct);
        }

        float oppositeNoise = smoothstep(0., 1., noiseValue);
        color.rgb /= max(color.a, 0.0001);
        vec3 hsv = rgb2hsv(color.rgb);
        hsv.y = mix(hsv.y, 0.0, oppositeNoise * uSaturateOffset);
        color.rgb = hsv2rgb(hsv);
        color.rgb += oppositeNoise * uLightOffset;

        color.a = clamp(color.a, 0., 1.);
        color.a *= uAlphaMulti;

        color += (10.0 / 255.0) * gradientNoise(fragCoord.xy) - (5.0 / 255.0);
        return vec4(color.rgb * color.a, color.a);
    }
"""
