package com.kashyap.wizlyai.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Brand icons for third-party authentication providers.
 * Monochrome versions that inherit tint from the Icon composable.
 */
object BrandIcons {
    /**
     * Google "G" logo icon (monochrome).
     * Standard Google logo simplified to a single-color path.
     */
    val Google: ImageVector by lazy {
        ImageVector.Builder(
            name = "Google",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                // Google "G" path
                moveTo(22.56f, 12.25f)
                curveTo(22.56f, 11.47f, 22.49f, 10.72f, 22.36f, 10f)
                horizontalLineTo(12f)
                verticalLineTo(14.26f)
                horizontalLineTo(17.92f)
                curveTo(17.66f, 15.63f, 16.88f, 16.79f, 15.71f, 17.57f)
                verticalLineTo(20.34f)
                horizontalLineTo(19.28f)
                curveTo(21.36f, 18.42f, 22.56f, 15.6f, 22.56f, 12.25f)
                close()
                moveTo(12f, 23f)
                curveTo(14.97f, 23f, 17.46f, 22.02f, 19.28f, 20.34f)
                lineTo(15.71f, 17.57f)
                curveTo(14.73f, 18.23f, 13.48f, 18.63f, 12f, 18.63f)
                curveTo(9.13f, 18.63f, 6.71f, 16.69f, 5.84f, 14.04f)
                horizontalLineTo(2.17f)
                verticalLineTo(16.89f)
                curveTo(3.98f, 20.48f, 7.7f, 23f, 12f, 23f)
                close()
                moveTo(5.84f, 14.04f)
                curveTo(5.62f, 13.38f, 5.49f, 12.68f, 5.49f, 12f)
                curveTo(5.49f, 11.32f, 5.62f, 10.62f, 5.84f, 9.96f)
                verticalLineTo(7.11f)
                horizontalLineTo(2.17f)
                curveTo(1.4f, 8.63f, 1f, 10.29f, 1f, 12f)
                curveTo(1f, 13.71f, 1.4f, 15.37f, 2.17f, 16.89f)
                lineTo(5.84f, 14.04f)
                close()
                moveTo(12f, 5.37f)
                curveTo(13.62f, 5.37f, 15.06f, 5.91f, 16.21f, 6.97f)
                lineTo(19.36f, 3.82f)
                curveTo(17.45f, 2.09f, 14.97f, 1f, 12f, 1f)
                curveTo(7.7f, 1f, 3.98f, 3.52f, 2.17f, 7.11f)
                lineTo(5.84f, 9.96f)
                curveTo(6.71f, 7.31f, 9.13f, 5.37f, 12f, 5.37f)
                close()
            }
        }.build()
    }

    /**
     * Apple logo icon (monochrome).
     * Standard Apple logo simplified to a single-color path.
     */
    val Apple: ImageVector by lazy {
        ImageVector.Builder(
            name = "Apple",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
            ) {
                // Apple logo path
                moveTo(17.05f, 20.28f)
                curveTo(16.07f, 21.23f, 15f, 21.08f, 13.97f, 20.63f)
                curveTo(12.88f, 20.17f, 11.88f, 20.15f, 10.73f, 20.63f)
                curveTo(9.29f, 21.25f, 8.53f, 21.07f, 7.67f, 20.28f)
                curveTo(2.79f, 15.25f, 3.51f, 7.59f, 9.05f, 7.31f)
                curveTo(10.4f, 7.38f, 11.34f, 8.03f, 12.13f, 8.09f)
                curveTo(13.31f, 7.85f, 14.44f, 7.18f, 15.7f, 7.27f)
                curveTo(17.22f, 7.39f, 18.36f, 7.99f, 19.1f, 9.07f)
                curveTo(15.83f, 11f, 16.56f, 15.32f, 19.56f, 16.55f)
                curveTo(18.99f, 18.01f, 18.24f, 19.46f, 17.05f, 20.29f)
                verticalLineTo(20.28f)
                close()
                moveTo(12.03f, 7.25f)
                curveTo(11.88f, 5.02f, 13.69f, 3.18f, 15.77f, 3f)
                curveTo(16.06f, 5.58f, 13.43f, 7.5f, 12.03f, 7.25f)
                close()
            }
        }.build()
    }
}
