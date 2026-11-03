package co.edu.unal.puntoscercanos.ui

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Paleta inspirada en la interfaz de referencia del reto (mapa claro, tarjeta lavanda, pin morado, acciones azules). */
data class AppColors(
    val accent: Color, val accentStrong: Color, val accentSoft: Color,
    val blue: Color, val marker: Color,
    val danger: Color, val dangerSoft: Color, val onDanger: Color,
    val text: Color, val textMuted: Color,
    val surface: Color, val surfaceStrong: Color, val surfaceSoft: Color,
    val outline: Color, val outlineStrong: Color,
    val background: Brush
)

val LocalAppColors = staticCompositionLocalOf<AppColors> { error("AppTheme no está provisto") }

private fun c(hex: Long, alpha: Float = 1f) = Color(hex).copy(alpha = alpha)

fun appColors(dark: Boolean): AppColors = if (dark) AppColors(
    // Modo oscuro: derivado de los mismos tonos (no aparece en la imagen de referencia).
    accent = c(0xFF8A6AD0), accentStrong = c(0xFF6F4FB5), accentSoft = c(0xFF8A6AD0, 0.20f),
    blue = c(0xFF7BB7E1), marker = c(0xFFEF8A8A),
    danger = c(0xFFFF8B95), dangerSoft = c(0xFFFF6B76, 0.13f), onDanger = c(0xFF3A0D14),
    text = c(0xFFECE8F4), textMuted = c(0xFFA9A3B8),
    surface = c(0xFF26212F), surfaceStrong = c(0xFF2E2840), surfaceSoft = Color.White.copy(alpha = 0.06f),
    outline = Color.White.copy(alpha = 0.12f), outlineStrong = Color.White.copy(alpha = 0.20f),
    background = SolidColor(c(0xFF1B1824))
) else AppColors(
    accent = c(0xFF5F3E9D), accentStrong = c(0xFF4A2F80), accentSoft = c(0xFF5F3E9D, 0.12f),
    blue = c(0xFF3F86B8), marker = c(0xFFC85A5A),
    danger = c(0xFFB3261E), dangerSoft = c(0xFFB3261E, 0.09f), onDanger = Color.White,
    text = c(0xFF333333), textMuted = c(0xFF747171),
    surface = Color.White, surfaceStrong = c(0xFFF3ECFB), surfaceSoft = c(0xFFF1F1F4),
    outline = c(0xFFDDDDD7), outlineStrong = c(0xFFC8C6C1),
    background = SolidColor(c(0xFFE9E6DC))
)

@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = remember(dark) { appColors(dark) }
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
        Box(Modifier.fillMaxSize().background(colors.background)) { content() }
    }
}