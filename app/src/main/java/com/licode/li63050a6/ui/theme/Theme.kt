package com.licode.li63050a6.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 品牌主色，与 Web 端 DeepSeek 蓝紫 #4D6BFE 一致。 */
val BrandBlue = Color(0xFF4D6BFE)
val BrandBlueDark = Color(0xFF7C8CFE)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7EAFF),
    onPrimaryContainer = Color(0xFF1A2A9C),
    secondary = Color(0xFF5B628F),
    background = Color(0xFFFAFBFF),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F3FA),
    onSurface = Color(0xFF1B1C22),
    outline = Color(0xFFC6CADD),
)

private val DarkColors = darkColorScheme(
    primary = BrandBlueDark,
    onPrimary = Color(0xFF0D1230),
    primaryContainer = Color(0xFF2F3BA8),
    onPrimaryContainer = Color(0xFFE0E3FF),
    secondary = Color(0xFFBAC0DF),
    background = Color(0xFF12131A),
    surface = Color(0xFF191B24),
    surfaceVariant = Color(0xFF23263A),
    onSurface = Color(0xFFE7E8F0),
    outline = Color(0xFF454A63),
)

@Composable
fun LicodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}