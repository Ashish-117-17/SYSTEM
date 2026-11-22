package com.example.ui.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Apple Fluid Interfaces / iOS 26 spring physics presets.
 * Replaces linear/rigid duration curves with interruptible, velocity-preserving springs.
 */
object AppleMotion {
    fun <T> tactileSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.62f,
        stiffness = 580f,
    )

    fun <T> morphSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.78f,
        stiffness = 420f,
    )

    fun <T> sheetSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.86f,
        stiffness = 340f,
    )

    fun <T> appSwitchSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.84f,
        stiffness = 380f,
    )

    fun <T> pillSlideSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.74f,
        stiffness = 460f,
    )

    fun floatTactile(): SpringSpec<Float> = spring(
        dampingRatio = 0.58f,
        stiffness = 620f,
    )

    fun dpMorph(): SpringSpec<Dp> = spring(
        dampingRatio = 0.80f,
        stiffness = 420f,
    )

    fun sizeMorph(): SpringSpec<IntSize> = spring(
        dampingRatio = 0.82f,
        stiffness = 400f,
    )

    fun offsetSheet(): SpringSpec<IntOffset> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 380f,
    )
}

/**
 * Elastic iOS-style press response: compresses smoothly on touch-down and rebounds with
 * spring physics on release without blocking click/scroll gestures.
 */
fun Modifier.appleTactilePress(
    pressedScale: Float = 0.94f,
    enabled: Boolean = true,
): Modifier = composed {
    if (!enabled) return@composed this
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = AppleMotion.floatTactile(),
        label = "apple_tactile_scale",
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(enabled) {
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    waitForUpOrCancellation()
                    isPressed = false
                }
            }
        }
}
