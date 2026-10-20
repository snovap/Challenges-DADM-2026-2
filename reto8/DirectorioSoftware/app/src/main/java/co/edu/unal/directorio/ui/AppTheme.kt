package co.edu.unal.directorio.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class PaletteStyle { VIOLET, FORMAL }

/** Paleta completa, traducida de las variables CSS del mockup de Figma. */
data class AppColors(
    val accent: Color, val accentStrong: Color, val accentSoft: Color,
    val danger: Color, val dangerSoft: Color, val onDanger: Color,
    val text: Color, val textMuted: Color,
    val surface: Color, val surfaceStrong: Color, val surfaceSoft: Color,
    val outline: Color, val outlineStrong: Color,
    val ambient: Color, val background: Brush
)

val LocalAppColors = staticCompositionLocalOf<AppColors> { error("AppTheme no está provisto") }

private fun c(hex: Long, alpha: Float = 1f) = Color(hex).copy(alpha = alpha)

fun appColors(dark: Boolean, style: PaletteStyle): AppColors {
    val formal = style == PaletteStyle.FORMAL
    val accent = Color(if (!formal) 0xFF8B5CF6 else if (dark) 0xFF3387A3 else 0xFF176B87)
    val accentStrong = Color(if (!formal) 0xFF7C3AED else if (dark) 0xFF14647F else 0xFF0F566F)
    val accentSoft = accent.copy(alpha = if (formal && !dark) 0.13f else 0.17f)
    val gradient = when {
        dark && !formal -> listOf(c(0xFF1A1A2E), c(0xFF16213E), c(0xFF0F3460))
        dark -> listOf(c(0xFF111A24), c(0xFF172838), c(0xFF1E3A47))
        !formal -> listOf(c(0xFFE8E0FF), c(0xFFE5E8FF), c(0xFFD9F1F4))
        else -> listOf(c(0xFFDFE9E9), c(0xFFDCE8ED), c(0xFFD4E4E8))
    }
    return if (dark) AppColors(
        accent = accent, accentStrong = accentStrong, accentSoft = accentSoft,
        danger = c(0xFFFF8B95), dangerSoft = c(0xFFFF6B76, 0.13f), onDanger = c(0xFF3A0D14),
        text = c(0xFFF7F5FF), textMuted = c(0xFFAAA8BD),
        surface = c(0xFF171830, 0.72f), surfaceStrong = c(0xFF1E1F3A, 0.94f),
        surfaceSoft = Color.White.copy(alpha = 0.055f),
        outline = Color.White.copy(alpha = 0.11f), outlineStrong = Color.White.copy(alpha = 0.19f),
        ambient = if (formal) c(0xFF2F86A3, 0.22f) else c(0xFF8B5CF6, 0.24f),
        background = Brush.linearGradient(gradient)
    ) else AppColors(
        accent = accent, accentStrong = accentStrong, accentSoft = accentSoft,
        danger = c(0xFFB3263B), dangerSoft = c(0xFFB3263B, 0.09f), onDanger = Color.White,
        text = c(if (formal) 0xFF18252C else 0xFF242039),
        textMuted = c(if (formal) 0xFF60727A else 0xFF6E6982),
        surface = if (formal) c(0xFFEFF6F7, 0.76f) else c(0xFFF7F5FF, 0.71f),
        surfaceStrong = if (formal) c(0xFFEEF6F7, 0.96f) else c(0xFFF6F4FF, 0.94f),
        surfaceSoft = Color.White.copy(alpha = 0.39f),
        outline = if (formal) c(0xFF19424C, 0.13f) else c(0xFF41306B, 0.12f),
        outlineStrong = if (formal) c(0xFF19424C, 0.21f) else c(0xFF41306B, 0.20f),
        ambient = if (formal) c(0xFF176B87, 0.13f) else c(0xFF7C3AED, 0.13f),
        background = Brush.linearGradient(gradient)
    )
}

/** Dos resplandores difuminados (arriba a la derecha y abajo a la izquierda), como en el mockup. */
private fun Modifier.ambientGlow(color: Color) = drawBehind {
    fun glow(center: Offset, radius: Float) = drawCircle(
        brush = Brush.radialGradient(0f to color, 0.68f to Color.Transparent, center = center, radius = radius),
        radius = radius,
        center = center
    )
    glow(Offset(size.width - 30.dp.toPx(), 40.dp.toPx()), 210.dp.toPx())
    glow(Offset(0f, size.height - 40.dp.toPx()), 260.dp.toPx())
}

@Composable
fun AppTheme(dark: Boolean, style: PaletteStyle, content: @Composable () -> Unit) {
    val colors = remember(dark, style) { appColors(dark, style) }
    val view = LocalView.current

    // Íconos de la barra de estado y de navegación legibles según el tema elegido en la app.
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(LocalAppColors provides colors) {
        Box(Modifier.fillMaxSize().background(colors.background).ambientGlow(colors.ambient)) {
            content()
        }
    }
}