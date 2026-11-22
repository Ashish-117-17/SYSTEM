package com.example.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalPrismColors

/**
 * Runtime Liquid Glass optics configuration controlled live by the user's Clarity Slider.
 * clarity = 0.0f -> Deep Frosted (high Gaussian blur, soft diffuse milky/smoky veil)
 * clarity = 1.0f -> Crystal Clear (low blur, strong Snell refraction & prism rim dispersion)
 */
@Immutable
data class LiquidGlassStyle(
    val clarity: Float = 0.45f,
)

class BackdropCaptureState(
    val layer: GraphicsLayer,
) {
    var rootOffset by mutableStateOf(Offset.Zero)
}

val LocalLiquidGlassStyle = staticCompositionLocalOf { LiquidGlassStyle(clarity = 0.45f) }
val LocalBackdropCapture = staticCompositionLocalOf<BackdropCaptureState?> { null }

/**
 * AGSL PBR Liquid Glass Fragment Shader inspired by PrismalAGSL & Kyant0/AndroidLiquidGlass.
 * Computes rounded-rectangle SDF, spherical meniscus bezel normals, Snell's law refraction
 * with subtle chromatic RGB edge dispersion, Blinn-Phong specular arc, and Schlick Fresnel rim.
 */
private const val LIQUID_GLASS_AGSL = """
    uniform shader backdrop;
    uniform float2 resolution;
    uniform float cornerRadius;
    uniform float clarity;
    uniform float isDark;
    uniform float tintR;
    uniform float tintG;
    uniform float tintB;
    uniform float tintAlpha;

    float sdRoundedBox(float2 p, float2 b, float r) {
        float2 q = abs(p) - b + float2(r);
        return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
    }

    half4 main(float2 fragCoord) {
        float2 safeRes = max(resolution, float2(2.0, 2.0));
        float2 halfSize = safeRes * 0.5;
        float2 p = fragCoord - halfSize;
        float r = min(cornerRadius, min(halfSize.x, halfSize.y));

        float dist = sdRoundedBox(p, halfSize, r);
        float edgeDepth = clamp(-dist, 0.0, 36.0);
        float rimWidth = mix(18.0, 26.0, clarity);
        float rimFactor = clamp(1.0 - (edgeDepth / rimWidth), 0.0, 1.0);

        // Compute inward normal from SDF gradient approximation
        float eps = 1.5;
        float gx = sdRoundedBox(p + float2(eps, 0.0), halfSize, r) - sdRoundedBox(p - float2(eps, 0.0), halfSize, r);
        float gy = sdRoundedBox(p + float2(0.0, eps), halfSize, r) - sdRoundedBox(p - float2(0.0, eps), halfSize, r);
        float2 grad = normalize(float2(gx, gy) + float2(0.0001, 0.0001));

        // Spherical meniscus curve along rim -> Snell refraction offset
        float meniscus = rimFactor * rimFactor;
        float refractStrength = mix(7.0, 22.0, clarity) * meniscus;
        float2 refractedUv = fragCoord - grad * refractStrength;

        // Subtle chromatic dispersion along crystal-clear rims
        float dispersion = mix(0.35, 2.6, clarity) * meniscus;
        float2 uvR = clamp(refractedUv - grad * dispersion, float2(0.0), safeRes - float2(1.0));
        float2 uvG = clamp(refractedUv, float2(0.0), safeRes - float2(1.0));
        float2 uvB = clamp(refractedUv + grad * dispersion, float2(0.0), safeRes - float2(1.0));

        half rSample = backdrop.eval(uvR).r;
        half gSample = backdrop.eval(uvG).g;
        half bSample = backdrop.eval(uvB).b;
        half3 refractedColor = half3(rSample, gSample, bSample);

        // Subtle optical vibrancy lift (Apple vibrancy)
        half luma = dot(refractedColor, half3(0.299, 0.587, 0.114));
        half satBoost = mix(1.08, 1.22, clarity);
        refractedColor = mix(half3(luma), refractedColor, satBoost);

        // Adaptive frosted-to-clear surface tint
        half3 tintColor = half3(tintR, tintG, tintB);
        half baseAlpha = half(tintAlpha);
        half3 glassColor = mix(refractedColor, tintColor, baseAlpha);

        // Dual Blinn-Phong specular arc (top-left primary + bottom-right caustic)
        float2 lightDir1 = normalize(float2(-0.62, -0.78));
        float2 lightDir2 = normalize(float2(0.55, 0.83));
        float spec1 = pow(max(dot(grad, lightDir1), 0.0), 3.0) * smoothstep(0.35, 1.0, rimFactor);
        float spec2 = pow(max(dot(grad, lightDir2), 0.0), 4.0) * smoothstep(0.45, 1.0, rimFactor);

        // Schlick Fresnel grazing highlight
        float fresnel = pow(rimFactor, 3.2) * mix(0.26, 0.52, clarity);

        half highlightStrength = (isDark > 0.5) ? half(0.44) : half(0.75);
        half3 specular = half3(1.0) * half(spec1 * 0.55 + spec2 * 0.22 + fresnel) * highlightStrength;

        // Subtle inner meniscus depth shadow for legibility
        float innerShadow = smoothstep(0.0, 1.0, rimFactor) * (1.0 - smoothstep(0.72, 1.0, rimFactor));
        half shadowAmt = (isDark > 0.5) ? half(0.10) : half(0.05);
        glassColor = glassColor * (half(1.0) - half(innerShadow) * shadowAmt) + specular;

        return half4(clamp(glassColor, half3(0.0), half3(1.0)), half(1.0));
    }
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun buildLiquidGlassRenderEffect(
    widthPx: Float,
    heightPx: Float,
    cornerRadiusPx: Float,
    clarity: Float,
    isDark: Boolean,
    tintColor: Color,
    tintAlpha: Float,
): RenderEffect {
    val shader = RuntimeShader(LIQUID_GLASS_AGSL).apply {
        setFloatUniform("resolution", widthPx.coerceAtLeast(2f), heightPx.coerceAtLeast(2f))
        setFloatUniform("cornerRadius", cornerRadiusPx.coerceAtLeast(1f))
        setFloatUniform("clarity", clarity.coerceIn(0f, 1f))
        setFloatUniform("isDark", if (isDark) 1f else 0f)
        setFloatUniform("tintR", tintColor.red)
        setFloatUniform("tintG", tintColor.green)
        setFloatUniform("tintB", tintColor.blue)
        setFloatUniform("tintAlpha", tintAlpha.coerceIn(0.04f, 0.88f))
    }

    // Blur radius smoothly scales from deep frosted (24px) to crystal clear (2.5px)
    val blurRadius = (24f * (1f - clarity * 0.88f)).coerceAtLeast(2.2f)
    val blurEffect = RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
    val refractionEffect = RenderEffect.createRuntimeShaderEffect(shader, "backdrop")
    return RenderEffect.createChainEffect(refractionEffect, blurEffect)
}

/**
 * Wraps screen content so any floating [LiquidGlassSurface] rendered in [overlay] samples
 * real live pixels from [content] behind it through our AGSL refraction + blur pipeline.
 */
@Composable
fun LiquidBackdropHost(
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val graphicsLayer = rememberGraphicsLayer()
    val captureState = remember(graphicsLayer) { BackdropCaptureState(graphicsLayer) }

    CompositionLocalProvider(LocalBackdropCapture provides captureState) {
        Box(modifier = modifier) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coords ->
                        captureState.rootOffset = coords.positionInRoot()
                    }
                    .drawWithContent {
                        graphicsLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(graphicsLayer)
                    },
                content = content,
            )
            overlay()
        }
    }
}

/**
 * Primary iOS 26 Liquid Glass surface.
 * Uses real backdrop layer capture + AGSL PBR refraction/dispersion shader on Android 13+ (API 33+)
 * and gracefully falls back to a multi-stop specular frosted surface on API 24-32.
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    forceDarkGlass: Boolean = false,
    accentTint: Color? = null,
    elevation: Dp = 10.dp,
    clarityOverride: Float? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalPrismColors.current
    val glassStyle = LocalLiquidGlassStyle.current
    val backdropState = LocalBackdropCapture.current
    val density = LocalDensity.current

    val isDarkGlass = forceDarkGlass || colors.isDark
    val clarity = (clarityOverride ?: glassStyle.clarity).coerceIn(0f, 1f)
    val shape: Shape = RoundedCornerShape(cornerRadius)

    var surfaceCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var surfaceSize by remember { mutableStateOf(Size.Zero) }

    // Frosted (clarity=0): richer milky/smoky surface; Clear (clarity=1): ultra-transparent crystal
    val baseTintColor = when {
        accentTint != null -> accentTint
        isDarkGlass -> Color(0xFF1A1B20)
        else -> Color(0xFFFDFDFF)
    }
    val baseTintAlpha = when {
        accentTint != null -> 0.24f + (1f - clarity) * 0.28f
        isDarkGlass -> 0.18f + (1f - clarity) * 0.54f
        else -> 0.16f + (1f - clarity) * 0.58f
    }

    val topRimColor = if (isDarkGlass) {
        Color.White.copy(alpha = 0.22f + clarity * 0.24f)
    } else {
        Color.White.copy(alpha = 0.72f + clarity * 0.24f)
    }
    val bottomRimColor = if (isDarkGlass) {
        Color.White.copy(alpha = 0.06f + clarity * 0.08f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }

    val borderBrush = Brush.linearGradient(
        colors = listOf(topRimColor, bottomRimColor, topRimColor.copy(alpha = topRimColor.alpha * 0.55f)),
    )

    val cornerPx = with(density) { cornerRadius.toPx() }
    val shadowAmbient = if (isDarkGlass) Color.Black.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.18f)
    val shadowSpot = if (isDarkGlass) Color.Black.copy(alpha = 0.75f) else Color.Black.copy(alpha = 0.22f)

    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = shadowAmbient,
                spotColor = shadowSpot,
                clip = false,
            )
            .clip(shape)
            .onGloballyPositioned { coords ->
                surfaceCoords = coords
                surfaceSize = Size(coords.size.width.toFloat(), coords.size.height.toFloat())
            },
        contentAlignment = contentAlignment,
    ) {
        // Real-time refracted backdrop layer (API 33+ AGSL shader; graceful frosted veil on API < 33)
        if (backdropState != null && surfaceSize.width > 1f && surfaceSize.height > 1f) {
            val renderEffectModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val effect = remember(
                    surfaceSize.width.toInt(),
                    surfaceSize.height.toInt(),
                    cornerPx,
                    clarity,
                    isDarkGlass,
                    baseTintColor,
                    baseTintAlpha,
                ) {
                    buildLiquidGlassRenderEffect(
                        widthPx = surfaceSize.width,
                        heightPx = surfaceSize.height,
                        cornerRadiusPx = cornerPx,
                        clarity = clarity,
                        isDark = isDarkGlass,
                        tintColor = baseTintColor,
                        tintAlpha = baseTintAlpha,
                    ).asComposeRenderEffect()
                }
                Modifier.graphicsLayer {
                    renderEffect = effect
                    clip = true
                }
            } else {
                Modifier
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(renderEffectModifier)
                    .drawWithContent {
                        val coords = surfaceCoords
                        if (coords != null && coords.isAttached) {
                            val myPos = coords.positionInRoot()
                            val dx = backdropState.rootOffset.x - myPos.x
                            val dy = backdropState.rootOffset.y - myPos.y
                            withTransform({
                                translate(left = dx, top = dy)
                            }) {
                                drawLayer(backdropState.layer)
                            }
                        }
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            drawRect(color = baseTintColor.copy(alpha = (baseTintAlpha + 0.16f).coerceAtMost(0.88f)))
                        }
                    },
            )
        } else {
            // Fallback when used inside dialogs/sheets outside a BackdropHost
            val fallbackBrush = Brush.verticalGradient(
                colors = if (isDarkGlass) {
                    listOf(
                        baseTintColor.copy(alpha = (baseTintAlpha + 0.18f).coerceAtMost(0.90f)),
                        baseTintColor.copy(alpha = (baseTintAlpha + 0.08f).coerceAtMost(0.84f)),
                    )
                } else {
                    listOf(
                        baseTintColor.copy(alpha = (baseTintAlpha + 0.20f).coerceAtMost(0.94f)),
                        baseTintColor.copy(alpha = (baseTintAlpha + 0.10f).coerceAtMost(0.88f)),
                    )
                },
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(fallbackBrush),
            )
        }

        // Specular top sheen overlay & crisp glass rim stroke
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isDarkGlass) 0.10f + clarity * 0.08f else 0.32f + clarity * 0.14f),
                            Color.Transparent,
                            Color.White.copy(alpha = if (isDarkGlass) 0.04f else 0.12f),
                        ),
                    ),
                )
                .border(width = 1.dp, brush = borderBrush, shape = shape),
        )

        content()
    }
}

/**
 * Circular Liquid Glass icon button with Apple spring press physics and 48dp accessibility target.
 */
@Composable
fun LiquidGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    forceDarkGlass: Boolean = false,
    accentTint: Color? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .appleTactilePress(pressedScale = 0.90f, enabled = enabled)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlassSurface(
            modifier = Modifier.size(size),
            cornerRadius = size / 2,
            forceDarkGlass = forceDarkGlass,
            accentTint = accentTint,
            elevation = 8.dp,
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/**
 * Grouped Liquid Glass pill container (used in YouTube player top/bottom clusters and Notes bars).
 */
@Composable
fun LiquidGlassPillGroup(
    modifier: Modifier = Modifier,
    forceDarkGlass: Boolean = false,
    horizontalPadding: Dp = 6.dp,
    verticalPadding: Dp = 4.dp,
    content: @Composable RowScope.() -> Unit,
) {
    LiquidGlassSurface(
        modifier = modifier,
        cornerRadius = 999.dp,
        forceDarkGlass = forceDarkGlass,
        elevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

/**
 * Custom iOS 26 Liquid Glass Slider with magnified elastic thumb for Clarity (Frosted <-> Clear)
 * and audio/video scrubbing.
 */
@Composable
fun LiquidGlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: () -> Unit = {},
    activeTrackColor: Color = LocalPrismColors.current.systemBlue,
    inactiveTrackColor: Color = if (LocalPrismColors.current.isDark) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.12f),
) {
    var isDragging by remember { mutableStateOf(false) }
    var trackWidthPx by remember { mutableFloatStateOf(1f) }
    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.24f else 1.0f,
        animationSpec = AppleMotion.floatTactile(),
        label = "glass_slider_thumb",
    )
    val clamped = value.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .onGloballyPositioned { trackWidthPx = it.size.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val next = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                    onValueChange(next)
                    onValueChangeFinished()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        onValueChange((offset.x / trackWidthPx).coerceIn(0f, 1f))
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onValueChange((change.position.x / trackWidthPx).coerceIn(0f, 1f))
                    },
                    onDragEnd = {
                        isDragging = false
                        onValueChangeFinished()
                    },
                    onDragCancel = {
                        isDragging = false
                        onValueChangeFinished()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackH = if (isDragging) 8.dp.toPx() else 6.dp.toPx()
            val cy = size.height / 2f
            val thumbR = 11.dp.toPx() * thumbScale
            val usableW = (size.width - thumbR * 2f).coerceAtLeast(1f)
            val thumbCx = thumbR + clamped * usableW

            // Inactive capsule track
            drawRoundRect(
                color = inactiveTrackColor,
                topLeft = Offset(0f, cy - trackH / 2f),
                size = Size(size.width, trackH),
                cornerRadius = CornerRadius(trackH / 2f, trackH / 2f),
            )

            // Active filled track
            drawRoundRect(
                color = activeTrackColor,
                topLeft = Offset(0f, cy - trackH / 2f),
                size = Size(thumbCx.coerceAtLeast(trackH), trackH),
                cornerRadius = CornerRadius(trackH / 2f, trackH / 2f),
            )

            // Outer soft shadow halo on glass thumb
            drawCircle(
                color = Color.Black.copy(alpha = 0.22f),
                radius = thumbR + 2.dp.toPx(),
                center = Offset(thumbCx, cy + 1.dp.toPx()),
            )

            // Liquid glass pill thumb (becomes clearer when dragged like iOS 26 sliders)
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDragging) 0.55f else 0.96f),
                        Color(0xFFE8EEF8).copy(alpha = if (isDragging) 0.35f else 0.88f),
                    ),
                ),
                radius = thumbR,
                center = Offset(thumbCx, cy),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.92f),
                radius = thumbR,
                center = Offset(thumbCx, cy),
                style = Stroke(width = 1.25.dp.toPx()),
            )
        }
    }
}
