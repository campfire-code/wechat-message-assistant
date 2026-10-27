package com.campfire.wechatassistant.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 品牌绿（#239F6A）。白字放在它上面只有 3.4:1，文字和按钮用更深的 [Deep]。 */
val Brand = Color(0xFF239F6A)
val Deep = Color(0xFF17744D)
val DeepDark = Color(0xFF0F4A33)

private val light = lightColorScheme(
    primary = Deep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF3E8),
    onPrimaryContainer = Color(0xFF0D3B28),
    secondary = Color(0xFF4A5A52),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3ECE7),
    onSecondaryContainer = Color(0xFF1B2A23),
    tertiary = Color(0xFF9A5B00),
    background = Color(0xFFF4F8F6),
    onBackground = Color(0xFF16201B),
    surface = Color(0xFFF4F8F6),
    onSurface = Color(0xFF16201B),
    surfaceVariant = Color(0xFFE3ECE7),
    onSurfaceVariant = Color(0xFF4A5A52),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEAF1ED),
    surfaceContainerHighest = Color(0xFFE3ECE7),
    outline = Color(0xFF6F7C75),
    outlineVariant = Color(0xFFD3DED8),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFBE4E1),
    onErrorContainer = Color(0xFF5C0F0A),
)

private val dark = darkColorScheme(
    primary = Color(0xFF6FDDAA),
    onPrimary = Color(0xFF0B2A1D),
    primaryContainer = Color(0xFF16513A),
    onPrimaryContainer = Color(0xFFC7F3DD),
    secondary = Color(0xFFB3C2BA),
    onSecondary = Color(0xFF1B2A23),
    secondaryContainer = Color(0xFF2B3A33),
    onSecondaryContainer = Color(0xFFDCE8E1),
    tertiary = Color(0xFFF2B35A),
    background = Color(0xFF0F1613),
    onBackground = Color(0xFFE3ECE7),
    surface = Color(0xFF0F1613),
    onSurface = Color(0xFFE3ECE7),
    surfaceVariant = Color(0xFF26332D),
    onSurfaceVariant = Color(0xFFB3C2BA),
    surfaceContainerLowest = Color(0xFF0A100D),
    surfaceContainerLow = Color(0xFF16201B),
    surfaceContainer = Color(0xFF1A2520),
    surfaceContainerHigh = Color(0xFF222E28),
    surfaceContainerHighest = Color(0xFF2B3A33),
    outline = Color(0xFF8A9890),
    outlineVariant = Color(0xFF34443C),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF410B07),
    errorContainer = Color(0xFF5C1A15),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** 成功色：浅色 5.8:1，深色 10.9:1。 */
@Composable
fun okColor(): Color = if (isSystemInDarkTheme()) Color(0xFF6FDDAA) else Deep

/** 警示（重试中、排队中）。 */
@Composable
fun warnColor(): Color = if (isSystemInDarkTheme()) Color(0xFFF2B35A) else Color(0xFF9A5B00)

@Composable
fun AppTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) dark else light, content = content)
