package com.yihua.app.ui.screens

import coil.size.Scale

private const val EdgeResistance = 0.28f
private const val DeletePreviewDistancePx = 220f
private const val DeletePreviewScaleReduction = 0.15f

internal const val PeekOffsetFraction = 0.06f
internal const val PeekScale = 0.94f

internal enum class CardMotion {
    FlyToLeft,
    FlyToTop,
    CoverFromLeft,
    CoverFromTop
}

internal data class SwipeTransform(
    val translationX: Float,
    val translationY: Float,
    val scale: Float
)

internal data class PhotoImageRequestOptions(
    val allowHardware: Boolean = true,
    val precisionInexact: Boolean = true,
    val scale: Scale = Scale.FIT,
    val memoryCacheKeyPrefix: String = "pictureclean-photo"
)

internal fun edgeResistedDrag(
    totalDrag: Float,
    resistance: Float = EdgeResistance
): Float = totalDrag * resistance

internal fun deletePreviewScale(dragY: Float): Float {
    val progress = (-dragY.coerceAtMost(0f) / DeletePreviewDistancePx).coerceIn(0f, 1f)
    return 1f - progress * DeletePreviewScaleReduction
}

internal fun flyOutTransform(
    motion: CardMotion,
    progress: Float,
    width: Float,
    height: Float,
    startX: Float,
    startY: Float,
    startScale: Float
): SwipeTransform {
    val clampedProgress = progress.coerceIn(0f, 1f)
    return when (motion) {
        CardMotion.FlyToLeft -> {
            val endX = -width * 1.1f
            SwipeTransform(
                translationX = startX + (endX - startX) * clampedProgress,
                translationY = startY,
                scale = startScale + (1f - startScale) * clampedProgress
            )
        }
        CardMotion.FlyToTop -> {
            val endY = -height * 1.1f
            SwipeTransform(
                translationX = startX,
                translationY = startY + (endY - startY) * clampedProgress,
                scale = startScale + (1f - startScale) * clampedProgress
            )
        }
        CardMotion.CoverFromLeft -> SwipeTransform(
            translationX = startX * (1f - clampedProgress),
            translationY = 0f,
            scale = 1f
        )
        CardMotion.CoverFromTop -> SwipeTransform(
            translationX = 0f,
            translationY = startY * (1f - clampedProgress),
            scale = 1f
        )
    }
}

internal fun photoImageRequestOptions(): PhotoImageRequestOptions = PhotoImageRequestOptions(
    allowHardware = true,
    precisionInexact = true,
    scale = Scale.FIT
)
