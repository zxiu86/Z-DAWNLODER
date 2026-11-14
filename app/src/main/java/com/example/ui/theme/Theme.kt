package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ZDownloaderColorScheme =
    darkColorScheme(
        primary = PurplePrimary,
        onPrimary = TextPrimary,
        primaryContainer = SurfaceCardElevated,
        onPrimaryContainer = PurpleFocusRing,
        secondary = PurplePrimaryBright,
        onSecondary = TextPrimary,
        background = BgMidnight,
        onBackground = TextPrimary,
        surface = SurfaceCard,
        onSurface = TextPrimary,
        surfaceVariant = SurfaceCardElevated,
        onSurfaceVariant = TextSecondary,
        outline = PurpleSubtleBorder,
        error = StatusError,
        onError = TextPrimary,
    )

private val ZDownloaderShapes =
    Shapes(
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(22.dp),
    )

@Composable
fun ZDownloaderTheme(content: @Composable () -> Unit) {
  MaterialTheme(
      colorScheme = ZDownloaderColorScheme,
      typography = Typography,
      shapes = ZDownloaderShapes,
      content = content,
  )
}
