package com.dropboxx.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** App logo as a vector: rounded indigo tile with an upward "drop" arrow. Used for window + tray icons. */
val DropBoxxLogo: ImageVector by lazy {
    ImageVector.Builder(name = "DropBoxxLogo", defaultWidth = 64.dp, defaultHeight = 64.dp, viewportWidth = 64f, viewportHeight = 64f).apply {
        addPath(pathData = addPathNodes("M14 2h36a12 12 0 0 1 12 12v36a12 12 0 0 1-12 12H14A12 12 0 0 1 2 50V14A12 12 0 0 1 14 2z"), fill = SolidColor(Color(0xFF4F5BD5)))
        addPath(pathData = addPathNodes("M32 14l16 16h-10v12H26V30H16z"), fill = SolidColor(Color.White))
        addPath(pathData = addPathNodes("M18 46h28v6H18z"), fill = SolidColor(Color(0xFF9EE7E3)))
    }.build()
}
