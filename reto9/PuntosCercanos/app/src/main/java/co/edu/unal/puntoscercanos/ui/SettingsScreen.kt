package co.edu.unal.puntoscercanos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.unal.puntoscercanos.data.SearchSettings
import co.edu.unal.puntoscercanos.model.PlaceCategory
import kotlin.math.roundToInt

/** Preferencias: radio de búsqueda en kilómetros y categorías de lugares. */
@Composable
fun SettingsScreen(
    initial: SearchSettings,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
    onSave: (SearchSettings) -> Unit
) {
    val c = LocalAppColors.current
    // Borrador local: no se guarda hasta pulsar "Guardar". rememberSaveable lo conserva al rotar.
    var radius by rememberSaveable { mutableStateOf(initial.radiusKm.toFloat()) }
    var mask by rememberSaveable {
        mutableStateOf(initial.categories.fold(0) { acc, category -> acc or (1 shl category.ordinal) })
    }
    val selected = PlaceCategory.entries.filter { (mask and (1 shl it.ordinal)) != 0 }.toSet()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Volver", onBack)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("PREFERENCIAS", color = c.accent, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.3.sp)
                Text("Ajustes de búsqueda", color = c.text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            }
            AppIconButton(
                if (dark) Icons.Default.LightMode else Icons.Default.DarkMode,
                if (dark) "Activar modo claro" else "Activar modo oscuro",
                onToggleTheme
            )
        }

        Column(Modifier.fillMaxWidth().surfaceCard().padding(20.dp)) {
            Text("Radio de búsqueda", color = c.textMuted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${radius.roundToInt()}", color = c.accent, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    " km", color = c.textMuted, fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }
            Slider(
                value = radius,
                onValueChange = { radius = it },
                valueRange = SearchSettings.MIN_RADIUS_KM.toFloat()..SearchSettings.MAX_RADIUS_KM.toFloat(),
                steps = SearchSettings.MAX_RADIUS_KM - SearchSettings.MIN_RADIUS_KM - 1, // un paso por kilómetro
                colors = SliderDefaults.colors(
                    thumbColor = c.accent,
                    activeTrackColor = c.accent,
                    inactiveTrackColor = c.accentSoft,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${SearchSettings.MIN_RADIUS_KM} km", color = c.textMuted, fontSize = 12.sp)
                Text("${SearchSettings.MAX_RADIUS_KM} km", color = c.textMuted, fontSize = 12.sp)
            }
        }

        Column(Modifier.fillMaxWidth().surfaceCard().padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text("Categorías", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Qué lugares mostrar en el mapa", color = c.textMuted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            PlaceCategory.entries.forEach { category ->
                CategoryRow(category, checked = category in selected) { mask = mask xor (1 shl category.ordinal) }
            }
            if (selected.isEmpty()) {
                Text("Elige al menos una categoría.", color = c.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }

        Text(
            "Los lugares provienen de OpenStreetMap. Con radios grandes en zonas muy densas se muestran " +
                    "los 100 más cercanos.",
            color = c.textMuted, fontSize = 12.sp, lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppButton("Cancelar", onBack, Modifier.weight(1f), kind = ButtonKind.SECONDARY)
            AppButton(
                "Guardar",
                { if (selected.isNotEmpty()) onSave(SearchSettings(radius.roundToInt(), selected)) },
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CategoryRow(category: PlaceCategory, checked: Boolean, onToggle: () -> Unit) {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(22.dp).clip(shape)
                .background(if (checked) c.accent else Color.Transparent)
                .border(1.dp, if (checked) c.accent else c.outlineStrong, shape),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Icon(categoryIcon(category), null, tint = c.marker, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(category.label, color = c.text, fontSize = 15.sp)
    }
}