package com.fantaysea.clan.ui.icons

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 项目实际用到的 Material 图标。
 *
 * `material-icons-extended` 会把约 2000 个图标全部打进 dex（debug 包约 42 MB），
 * 而 `material-icons-core` 只包含最常用的 49 个图标。这里只内置 core 未覆盖的图标，
 * 路径数据来自 androidx.compose.material:material-icons-extended 1.7.8（Apache-2.0）。
 */
object ClanIcons {


    @Suppress("CheckReturnValue")
    val search: ImageVector
        get() {
            if (_search != null) {
                return _search!!
            }
            _search =
                ImageVector.Builder(
                    name = "search",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 24f,
                    viewportHeight = 24f,
                )
                    .apply {
                        path(
                            fill = SolidColor(Color.Black),
                            fillAlpha = 1f,
                            stroke = null,
                            strokeAlpha = 1f,
                            strokeLineWidth = 1f,
                            strokeLineCap = StrokeCap.Butt,
                            strokeLineJoin = StrokeJoin.Bevel,
                            strokeLineMiter = 1f,
                            pathFillType = PathFillType.Companion.NonZero,
                        ) {
                            moveTo(19.6f, 21f)
                            lineTo(13.3f, 14.7f)
                            quadToRelative(-0.75f, 0.6f, -1.72f, 0.95f)
                            reflectiveQuadTo(9.5f, 16f)
                            quadTo(6.78f, 16f, 4.89f, 14.11f)
                            quadTo(3f, 12.23f, 3f, 9.5f)
                            quadTo(3f, 6.77f, 4.89f, 4.89f)
                            reflectiveQuadTo(9.5f, 3f)
                            reflectiveQuadToRelative(4.61f, 1.89f)
                            reflectiveQuadTo(16f, 9.5f)
                            quadToRelative(0f, 1.1f, -0.35f, 2.07f)
                            reflectiveQuadTo(14.7f, 13.3f)
                            lineTo(21f, 19.6f)
                            lineTo(19.6f, 21f)
                            close()
                            moveTo(9.5f, 14f)
                            quadToRelative(1.88f, 0f, 3.19f, -1.31f)
                            reflectiveQuadTo(14f, 9.5f)
                            reflectiveQuadTo(12.69f, 6.31f)
                            reflectiveQuadTo(9.5f, 5f)
                            reflectiveQuadTo(6.31f, 6.31f)
                            reflectiveQuadTo(5f, 9.5f)
                            reflectiveQuadToRelative(1.31f, 3.19f)
                            reflectiveQuadTo(9.5f, 14f)
                            close()
                        }
                    }
                    .build()
            return _search!!
        }

    private var _search: ImageVector? = null

    /** 最近任务（九宫格）。 */
    val Apps: ImageVector by lazy {
        materialIcon(name = "Filled.Apps") {
            materialPath {
                moveTo(4.0f, 8.0f)
                horizontalLineToRelative(4.0f)
                lineTo(8.0f, 4.0f)
                lineTo(4.0f, 4.0f)
                verticalLineToRelative(4.0f)
                close()
                moveTo(10.0f, 20.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(4.0f)
                close()
                moveTo(4.0f, 20.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-4.0f)
                lineTo(4.0f, 16.0f)
                verticalLineToRelative(4.0f)
                close()
                moveTo(4.0f, 14.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-4.0f)
                lineTo(4.0f, 10.0f)
                verticalLineToRelative(4.0f)
                close()
                moveTo(10.0f, 14.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(4.0f)
                close()
                moveTo(16.0f, 4.0f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(4.0f)
                lineTo(20.0f, 4.0f)
                horizontalLineToRelative(-4.0f)
                close()
                moveTo(10.0f, 8.0f)
                horizontalLineToRelative(4.0f)
                lineTo(14.0f, 4.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(4.0f)
                close()
                moveTo(16.0f, 14.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(4.0f)
                close()
                moveTo(16.0f, 20.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(4.0f)
                close()
            }
        }
    }

    /** 屏幕共享（RTL 下自动镜像）。 */
    val ScreenShare: ImageVector by lazy {
        materialIcon(name = "AutoMirrored.Outlined.ScreenShare", autoMirror = true) {
            materialPath {
                moveTo(20.0f, 18.0f)
                curveToRelative(1.1f, 0.0f, 1.99f, -0.9f, 1.99f, -2.0f)
                lineTo(22.0f, 6.0f)
                curveToRelative(0.0f, -1.11f, -0.9f, -2.0f, -2.0f, -2.0f)
                lineTo(4.0f, 4.0f)
                curveToRelative(-1.11f, 0.0f, -2.0f, 0.89f, -2.0f, 2.0f)
                verticalLineToRelative(10.0f)
                curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
                lineTo(0.0f, 18.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(24.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-4.0f)
                close()
                moveTo(4.0f, 16.0f)
                lineTo(4.0f, 6.0f)
                horizontalLineToRelative(16.0f)
                verticalLineToRelative(10.01f)
                lineTo(4.0f, 16.0f)
                close()
                moveTo(13.0f, 9.13f)
                curveToRelative(-3.89f, 0.54f, -5.44f, 3.2f, -6.0f, 5.87f)
                curveToRelative(1.39f, -1.87f, 3.22f, -2.72f, 6.0f, -2.72f)
                verticalLineToRelative(2.19f)
                lineToRelative(4.0f, -3.74f)
                lineTo(13.0f, 7.0f)
                verticalLineToRelative(2.13f)
                close()
            }
        }
    }

    /** 触摸/点击操作提示。 */
    val TouchApp: ImageVector by lazy {
        materialIcon(name = "Outlined.TouchApp") {
            materialPath {
                moveTo(18.19f, 12.44f)
                lineToRelative(-3.24f, -1.62f)
                curveToRelative(1.29f, -1.0f, 2.12f, -2.56f, 2.12f, -4.32f)
                curveToRelative(0.0f, -3.03f, -2.47f, -5.5f, -5.5f, -5.5f)
                reflectiveCurveToRelative(-5.5f, 2.47f, -5.5f, 5.5f)
                curveToRelative(0.0f, 2.13f, 1.22f, 3.98f, 3.0f, 4.89f)
                verticalLineToRelative(3.26f)
                curveToRelative(-2.15f, -0.46f, -2.02f, -0.44f, -2.26f, -0.44f)
                curveToRelative(-0.53f, 0.0f, -1.03f, 0.21f, -1.41f, 0.59f)
                lineTo(4.0f, 16.22f)
                lineToRelative(5.09f, 5.09f)
                curveTo(9.52f, 21.75f, 10.12f, 22.0f, 10.74f, 22.0f)
                horizontalLineToRelative(6.3f)
                curveToRelative(0.98f, 0.0f, 1.81f, -0.7f, 1.97f, -1.67f)
                lineToRelative(0.8f, -4.71f)
                curveTo(20.03f, 14.32f, 19.38f, 13.04f, 18.19f, 12.44f)
                close()
                moveTo(17.84f, 15.29f)
                lineTo(17.04f, 20.0f)
                horizontalLineToRelative(-6.3f)
                curveToRelative(-0.09f, 0.0f, -0.17f, -0.04f, -0.24f, -0.1f)
                lineToRelative(-3.68f, -3.68f)
                lineToRelative(4.25f, 0.89f)
                verticalLineTo(6.5f)
                curveToRelative(0.0f, -0.28f, 0.22f, -0.5f, 0.5f, -0.5f)
                curveToRelative(0.28f, 0.0f, 0.5f, 0.22f, 0.5f, 0.5f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(1.76f)
                lineToRelative(3.46f, 1.73f)
                curveTo(17.69f, 14.43f, 17.91f, 14.86f, 17.84f, 15.29f)
                close()
                moveTo(8.07f, 6.5f)
                curveToRelative(0.0f, -1.93f, 1.57f, -3.5f, 3.5f, -3.5f)
                reflectiveCurveToRelative(3.5f, 1.57f, 3.5f, 3.5f)
                curveToRelative(0.0f, 0.95f, -0.38f, 1.81f, -1.0f, 2.44f)
                verticalLineTo(6.5f)
                curveToRelative(0.0f, -1.38f, -1.12f, -2.5f, -2.5f, -2.5f)
                curveToRelative(-1.38f, 0.0f, -2.5f, 1.12f, -2.5f, 2.5f)
                verticalLineToRelative(2.44f)
                curveTo(8.45f, 8.31f, 8.07f, 7.45f, 8.07f, 6.5f)
                close()
            }
        }
    }

    /** 被控端"投屏中"提示。 */
    val PresentToAll: ImageVector by lazy {
        materialIcon(name = "Outlined.PresentToAll") {
            materialPath {
                moveTo(21.0f, 3.0f)
                lineTo(3.0f, 3.0f)
                curveToRelative(-1.11f, 0.0f, -2.0f, 0.89f, -2.0f, 2.0f)
                verticalLineToRelative(14.0f)
                curveToRelative(0.0f, 1.11f, 0.89f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(18.0f)
                curveToRelative(1.11f, 0.0f, 2.0f, -0.89f, 2.0f, -2.0f)
                lineTo(23.0f, 5.0f)
                curveToRelative(0.0f, -1.11f, -0.89f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(21.0f, 19.02f)
                lineTo(3.0f, 19.02f)
                lineTo(3.0f, 4.98f)
                horizontalLineToRelative(18.0f)
                verticalLineToRelative(14.04f)
                close()
                moveTo(10.0f, 12.0f)
                lineTo(8.0f, 12.0f)
                lineToRelative(4.0f, -4.0f)
                lineToRelative(4.0f, 4.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(-4.0f)
                close()
            }
        }
    }

    /** 无障碍权限。 */
    val Accessibility: ImageVector by lazy {
        materialIcon(name = "Outlined.Accessibility") {
            materialPath {
                moveTo(12.0f, 2.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, 0.9f, 2.0f, 2.0f)
                reflectiveCurveToRelative(-0.9f, 2.0f, -2.0f, 2.0f)
                reflectiveCurveToRelative(-2.0f, -0.9f, -2.0f, -2.0f)
                reflectiveCurveToRelative(0.9f, -2.0f, 2.0f, -2.0f)
                close()
                moveTo(21.0f, 9.0f)
                horizontalLineToRelative(-6.0f)
                verticalLineToRelative(13.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(-6.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(6.0f)
                lineTo(9.0f, 22.0f)
                lineTo(9.0f, 9.0f)
                lineTo(3.0f, 9.0f)
                lineTo(3.0f, 7.0f)
                horizontalLineToRelative(18.0f)
                verticalLineToRelative(2.0f)
                close()
            }
        }
    }

    /** 设备（手机）图标。 */
    val PhoneAndroid: ImageVector by lazy {
        materialIcon(name = "Outlined.PhoneAndroid") {
            materialPath {
                moveTo(16.0f, 1.0f)
                lineTo(8.0f, 1.0f)
                curveTo(6.34f, 1.0f, 5.0f, 2.34f, 5.0f, 4.0f)
                verticalLineToRelative(16.0f)
                curveToRelative(0.0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
                horizontalLineToRelative(8.0f)
                curveToRelative(1.66f, 0.0f, 3.0f, -1.34f, 3.0f, -3.0f)
                lineTo(19.0f, 4.0f)
                curveToRelative(0.0f, -1.66f, -1.34f, -3.0f, -3.0f, -3.0f)
                close()
                moveTo(17.0f, 18.0f)
                lineTo(7.0f, 18.0f)
                lineTo(7.0f, 4.0f)
                horizontalLineToRelative(10.0f)
                verticalLineToRelative(14.0f)
                close()
                moveTo(14.0f, 21.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(-1.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(1.0f)
                close()
            }
        }
    }

    /** 未选中状态。 */
    val RadioButtonUnchecked: ImageVector by lazy {
        materialIcon(name = "Outlined.RadioButtonUnchecked") {
            materialPath {
                moveTo(12.0f, 2.0f)
                curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                close()
                moveTo(12.0f, 20.0f)
                curveToRelative(-4.42f, 0.0f, -8.0f, -3.58f, -8.0f, -8.0f)
                reflectiveCurveToRelative(3.58f, -8.0f, 8.0f, -8.0f)
                reflectiveCurveToRelative(8.0f, 3.58f, 8.0f, 8.0f)
                reflectiveCurveToRelative(-3.58f, 8.0f, -8.0f, 8.0f)
                close()
            }
        }
    }
}
