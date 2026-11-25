package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ClipCashDarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = DarkElevated,
    onPrimaryContainer = DarkTextPrimary,
    secondary = CashEmerald,
    onSecondary = InkNavy,
    secondaryContainer = MintSurface,
    onSecondaryContainer = BrightMint,
    tertiary = CoinGold,
    onTertiary = InkNavy,
    tertiaryContainer = GoldSurface,
    onTertiaryContainer = BrightGold,
    background = DarkMidnight,
    onBackground = DarkTextPrimary,
    surface = DarkCard,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkElevated,
    onSurfaceVariant = DarkTextMuted,
    outline = DarkBorder,
    outlineVariant = Color(0xFF1A294D),
    error = CoralRed,
    onError = Color.White
)

val ClipCashShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun ClipCashTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ClipCashDarkColorScheme,
        typography = ClipCashTypography,
        shapes = ClipCashShapes,
        content = content
    )
}
