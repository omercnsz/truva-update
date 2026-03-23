package com.truva

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Truva marka renk paleti — Dark Navy / Cyan Premium Design
private val TruvaDarkColorScheme =
    darkColorScheme(
        primary = Color(0xFF00E5FF), // Parlak Turkuaz (Cyan) - Ana Aksan
        onPrimary = Color(0xFF0B111F), // Koyu Lacivert Arka Planda Siyah Metin
        primaryContainer = Color(0xFF004D54),
        onPrimaryContainer = Color(0xFFAFEFFF),
        secondary = Color(0xFF1E88E5), // Canlı Mavi
        onSecondary = Color.White,
        secondaryContainer = Color(0xFF003271),
        onSecondaryContainer = Color(0xFFD9E2FF),
        tertiary = Color(0xFF82B1FF), // Hafif Mavi
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        background = Color(0xFF0B111F), // Derin Lacivert Arka Plan (Ekran görüntüsündeki gibi)
        onBackground = Color(0xFFE2E2E6),
        surface = Color(0xFF161D2B), // Bir tık daha açık lacivert kartlar/yüzeyler
        onSurface = Color(0xFFE2E2E6),
        surfaceVariant = Color(0xFF1C2533),
        onSurfaceVariant = Color(0xFFC4C6D0),
        outline = Color(0xFF74777F),
        outlineVariant = Color(0xFF44474E)
    )

private val TruvaLightColorScheme =
    lightColorScheme(
        primary = Color(0xFF006064), // Derin Cyan
        onPrimary = Color.White,
        primaryContainer = Color(0xFFB2EBF2),
        onPrimaryContainer = Color(0xFF002021),
        secondary = Color(0xFF1565C0), // Derin Safir Mavi
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD9E2FF),
        onSecondaryContainer = Color(0xFF001945),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        background = Color(0xFFF0F4FA), // Hafif Mavimsi Beyaz
        onBackground = Color(0xFF1B1B1F),
        surface = Color(0xFFF0F4FA),
        onSurface = Color(0xFF1B1B1F),
        surfaceVariant = Color(0xFFE1E2EC),
        onSurfaceVariant = Color(0xFF44474E),
        outline = Color(0xFF74777F),
        outlineVariant = Color(0xFFC4C6D0)
    )

@Composable
fun TruvaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) TruvaDarkColorScheme else TruvaLightColorScheme
    MaterialTheme(colorScheme = colorScheme, content = content)
}
