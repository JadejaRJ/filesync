package com.syncbridge.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = BridgeBlue40,
    onPrimary = Color.White,
    primaryContainer = BridgeBlue90,
    onPrimaryContainer = BridgeBlue10,
    secondary = Amber40,
    secondaryContainer = Amber80,
)

private val DarkColors = darkColorScheme(
    primary = BridgeBlue80,
    onPrimary = BridgeBlue20,
    primaryContainer = BridgeBlue20,
    onPrimaryContainer = BridgeBlue90,
    secondary = Amber80,
)

/** True/false status semantics (upload/download/error/success/conflict) live outside the Material scheme;
 * see [StatusSuccess], [StatusError], [StatusWarning], [StatusUploading], [StatusDownloading]. */
@Composable
fun SyncBridgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SyncBridgeTypography,
        content = content,
    )
}
