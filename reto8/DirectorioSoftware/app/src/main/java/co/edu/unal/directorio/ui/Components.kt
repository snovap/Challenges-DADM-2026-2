package co.edu.unal.directorio.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- Acciones de contacto (abren apps externas mediante Intents) ----------

private fun Context.startSafely(intent: Intent): Boolean =
    try { startActivity(intent); true } catch (e: ActivityNotFoundException) { false }

fun Context.openDialer(phone: String) =
    startSafely(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone.trim())}")))

fun Context.openEmail(email: String) =
    startSafely(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${email.trim()}")))

fun Context.openWebsite(url: String) =
    startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(url.trim())))

// ---------- Utilidades de estilo ----------

/** Tarjeta translúcida con borde sutil (el "glass" del mockup). */
@Composable
fun Modifier.surfaceCard(radius: Dp = 24.dp): Modifier {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(radius)
    return this.clip(shape).background(c.surface).border(1.dp, c.outline, shape)
}

fun initialsOf(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2)
        .map { it.first().uppercaseChar() }.joinToString("").ifEmpty { "?" }

// ---------- Componentes ----------

@Composable
fun AppIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    radius: Dp = 16.dp,
    iconSize: Dp = 20.dp,
    enabled: Boolean = true
) {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(c.surfaceSoft)
            .border(1.dp, c.outline, shape)
            .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, label,
            tint = c.text.copy(alpha = if (enabled) 1f else 0.3f),
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun AppHeader(
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    onTogglePalette: () -> Unit
) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (onBack != null) AppIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Volver", onBack)
            Column {
                if (subtitle != null) {
                    Text(
                        subtitle.uppercase(), color = c.accent, fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold, letterSpacing = 1.3.sp
                    )
                }
                Text(
                    title, color = c.text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIconButton(Icons.Default.Palette, "Cambiar paleta", onTogglePalette)
            AppIconButton(
                if (dark) Icons.Default.LightMode else Icons.Default.DarkMode,
                if (dark) "Activar modo claro" else "Activar modo oscuro",
                onToggleTheme
            )
        }
    }
}

@Composable
fun CategoryChip(text: String, selected: Boolean = false) {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(50)
    Text(
        text,
        color = if (selected) c.text else c.textMuted,
        fontSize = if (selected) 12.sp else 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) c.accentSoft else c.surfaceSoft)
            .border(1.dp, if (selected) c.accent.copy(alpha = 0.4f) else c.outline, shape)
            .padding(horizontal = if (selected) 10.dp else 8.dp, vertical = if (selected) 6.dp else 4.dp)
    )
}

@Composable
fun InitialsMark(name: String, size: Dp, radius: Dp, fontSize: TextUnit) {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(radius)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(c.accent, c.accentStrong)))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape),
        contentAlignment = Alignment.Center
    ) {
        Text(initialsOf(name), color = Color.White, fontSize = fontSize, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun AccentIconBox(icon: ImageVector, size: Dp = 42.dp, radius: Dp = 14.dp, iconSize: Dp = 20.dp) {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(radius)
    Box(
        Modifier.size(size).clip(shape).background(c.accentSoft)
            .border(1.dp, c.accent.copy(alpha = 0.28f), shape),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = c.accent, modifier = Modifier.size(iconSize)) }
}

enum class ButtonKind { PRIMARY, SECONDARY, DANGER, DANGER_FILLED }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    kind: ButtonKind = ButtonKind.PRIMARY
) {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(15.dp)
    val (background, foreground, border) = when (kind) {
        ButtonKind.PRIMARY -> Triple(c.accent, Color.White, Color.Transparent)
        ButtonKind.SECONDARY -> Triple(c.surfaceSoft, c.text, c.outline)
        ButtonKind.DANGER -> Triple(c.dangerSoft, c.danger, c.danger.copy(alpha = 0.35f))
        ButtonKind.DANGER_FILLED -> Triple(c.danger, c.onDanger, Color.Transparent)
    }
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 19.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = foreground, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(if (focused) 1.5.dp else 1.dp, if (focused) c.accent else c.outline, shape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, tint = c.textMuted)
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = c.text, fontSize = 15.sp),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(Modifier.height(56.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text("Buscar por nombre", color = c.textMuted.copy(alpha = 0.8f), fontSize = 15.sp)
                    }
                    inner()
                }
            }
        )
        if (value.isNotEmpty()) {
            Icon(
                Icons.Default.Close, "Borrar búsqueda", tint = c.textMuted,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onValueChange("") }.padding(6.dp)
            )
        }
    }
}

@Composable
fun AppTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    val c = LocalAppColors.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    val borderColor = when {
        error != null -> c.danger
        focused -> c.accent
        else -> c.outline
    }
    Column(modifier) {
        Column(
            Modifier.fillMaxWidth().clip(shape).background(c.surface)
                .border(1.dp, borderColor, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                color = if (focused) c.accent else c.textMuted
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                minLines = minLines,
                textStyle = TextStyle(color = c.text, fontSize = 15.sp),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box(
                        Modifier.defaultMinSize(minHeight = 28.dp),
                        contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
                    ) {
                        if (value.isEmpty()) {
                            Text(placeholder, color = c.textMuted.copy(alpha = 0.8f), fontSize = 15.sp)
                        }
                        inner()
                    }
                }
            )
        }
        if (error != null) {
            Text(error, color = c.danger, fontSize = 11.sp, modifier = Modifier.padding(start = 14.dp, top = 4.dp))
        }
    }
}