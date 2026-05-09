package com.yihua.app.ui.screens

import coil.size.Scale

private const val EdgeResistance = 0.28f

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

internal fun photoImageRequestOptions(): PhotoImageRequestOptions = PhotoImageRequestOptions(
    allowHardware = true,
    precisionInexact = true,
    scale = Scale.FIT
)
