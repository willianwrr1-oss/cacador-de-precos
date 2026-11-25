package com.willian.cacadorprecos.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Pal {
    val Navy = Color(0xFF0E2233)
    val Teal = Color(0xFF0E7C6B)
    val Bg = Color(0xFFF3F5F7)
    val Line = Color(0xFFE1E6EB)
    val Border = Color(0xFFCBD3DB)
    val Muted = Color(0xFF5B6773)
    val Soft = Color(0xFFB4C3D1)
    val GoodBg = Color(0xFFDDF3EC)
    val GoodFg = Color(0xFF0A6455)
    val UpBg = Color(0xFFFDEBD8)
    val UpFg = Color(0xFF9A4A07)
    val UpLine = Color(0xFFB45309)
    val FlatBg = Color(0xFFE8ECF0)
    val FlatFg = Color(0xFF44505C)
    val GoldBg = Color(0xFFFFF6D6)
    val GoldPill = Color(0xFFFFF1C2)
    val GoldFg = Color(0xFF6B4A00)
    val GoldLine = Color(0xFFA67C00)
    val BlueBg = Color(0xFFE3EEF9)
    val BlueFg = Color(0xFF1F5A94)
    val Danger = Color(0xFFB42318)
    val OffTrack = Color(0xFF6F7C89)
}

@Composable
fun PrecosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Pal.Teal,
            onPrimary = Color.White,
            secondary = Pal.Navy,
            background = Pal.Bg,
            onBackground = Pal.Navy,
            surface = Color.White,
            onSurface = Pal.Navy,
            surfaceVariant = Pal.FlatBg,
            outline = Pal.Border
        ),
        content = content
    )
}
