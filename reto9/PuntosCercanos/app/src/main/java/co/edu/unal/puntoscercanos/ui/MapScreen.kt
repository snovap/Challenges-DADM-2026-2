package co.edu.unal.puntoscercanos.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.unal.puntoscercanos.location.UserLocation
import co.edu.unal.puntoscercanos.map.OsmMap
import co.edu.unal.puntoscercanos.model.Place
import co.edu.unal.puntoscercanos.model.PlaceCategory
import co.edu.unal.puntoscercanos.model.PlacesError
import co.edu.unal.puntoscercanos.model.formatDistance
import org.osmdroid.util.GeoPoint
import java.util.Locale
import kotlin.math.roundToInt

/** Contenedor flotante con sombra, como las tarjetas de la interfaz de referencia. */
private fun Modifier.floating(radius: Dp, color: Color): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.shadow(6.dp, shape).clip(shape).background(color)
}

internal fun categoryIcon(category: PlaceCategory): ImageVector = when (category) {
    PlaceCategory.HOSPITAL -> Icons.Default.LocalHospital
    PlaceCategory.TOURISM -> Icons.Default.Attractions
    PlaceCategory.PHARMACY -> Icons.Default.LocalPharmacy
    PlaceCategory.RESTAURANT -> Icons.Default.Restaurant
}

private fun Context.launchSafely(intent: Intent): Boolean =
    try { startActivity(intent); true } catch (e: ActivityNotFoundException) { false }

private fun fmt(value: Double) = String.format(Locale.US, "%.6f", value)

/** Abre la app de mapas del teléfono con el lugar seleccionado. */
private fun Context.openDirections(place: Place): Boolean {
    val label = Uri.encode(place.name)
    val coords = "${fmt(place.latitude)},${fmt(place.longitude)}"
    return launchSafely(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$coords?q=$coords($label)")))
}

private fun Context.sharePlace(place: Place): Boolean {
    val link = "https://www.openstreetmap.org/?mlat=${fmt(place.latitude)}&mlon=${fmt(place.longitude)}" +
            "#map=18/${fmt(place.latitude)}/${fmt(place.longitude)}"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "${place.name}\n$link")
    }
    return launchSafely(Intent.createChooser(send, "Compartir lugar"))
}

@Composable
fun MapScreen(
    location: LocationUiState,
    places: PlacesUiState,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onRefresh: () -> Unit,
    onRetrySearch: () -> Unit,
    onSelectPlace: (String?) -> Unit,
    onToggleCategory: (PlaceCategory) -> Unit
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    var recenter by rememberSaveable { mutableStateOf(0) }
    val loc = location.location
    val userPoint = remember(loc) { loc?.let { GeoPoint(it.latitude, it.longitude) } }
    val visiblePlaces = remember(places.places, places.hiddenCategories) { places.visiblePlaces }
    val selected = places.selectedPlace
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }

    Box(Modifier.fillMaxSize()) {
        OsmMap(
            modifier = Modifier.fillMaxSize(),
            dark = dark,
            userDotColor = c.blue.toArgb(),
            markerColor = c.marker.toArgb(),
            selectedColor = c.accent.toArgb(),
            labelColor = c.text.toArgb(),
            labelHaloColor = c.surface.toArgb(),
            circleColor = c.accent.toArgb(),
            userLocation = userPoint,
            recenterSignal = recenter,
            autoCenter = location.searching,
            places = visiblePlaces,
            selectedId = places.selectedId,
            radiusMeters = places.settings.radiusKm * 1000,
            fitSignal = places.resultVersion,
            onPlaceClick = { onSelectPlace(it) }
        )

        // Barra superior y chips
        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth().floating(14.dp, c.surface)
                    .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.LocationOn, null, tint = c.accent, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Puntos cercanos", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Explora tu entorno", color = c.textMuted, fontSize = 12.sp)
                }
                AppIconButton(Icons.Default.Tune, "Ajustes de búsqueda", onOpenSettings, size = 40.dp, radius = 20.dp)
                Spacer(Modifier.width(8.dp))
                AppIconButton(
                    if (dark) Icons.Default.LightMode else Icons.Default.DarkMode,
                    if (dark) "Activar modo claro" else "Activar modo oscuro",
                    onToggleTheme, size = 40.dp, radius = 20.dp
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Chip de radio: abre los ajustes
                Row(
                    Modifier.height(36.dp).floating(18.dp, c.accent)
                        .clickable(role = Role.Button, onClickLabel = "Cambiar radio", onClick = onOpenSettings)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Tune, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Radio ${places.settings.radiusKm} km", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                // Chips de categoría: muestran u ocultan esa categoría en el mapa
                places.settings.categories.sortedBy { it.ordinal }.forEach { category ->
                    val active = category !in places.hiddenCategories
                    Row(
                        Modifier.height(36.dp).floating(18.dp, if (active) c.surface else c.surfaceSoft)
                            .clickable(role = Role.Button) { onToggleCategory(category) }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            categoryIcon(category), null,
                            tint = if (active) c.marker else c.textMuted, modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            category.chipLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            color = if (active) c.text else c.textMuted
                        )
                    }
                }
            }
        }

        // Zona inferior: botón de centrar + tarjeta según el estado
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
//            if (userPoint != null) {
//                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
//                    Box(
//                        Modifier.size(48.dp).floating(24.dp, c.surface)
//                            .clickable(role = Role.Button, onClickLabel = "Centrar en mi ubicación") { recenter++ },
//                        contentAlignment = Alignment.Center
//                    ) { Icon(Icons.Default.MyLocation, "Centrar en mi ubicación", tint = c.text) }
//                }
//            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Atribución obligatoria de OpenStreetMap (licencia ODbL)
                Text(
                    "© OpenStreetMap contributors", color = c.textMuted, fontSize = 10.sp,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .background(c.surface.copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                if (userPoint != null) {
                    Box(
                        Modifier.size(48.dp).floating(24.dp, c.surface)
                            .clickable(role = Role.Button, onClickLabel = "Centrar en mi ubicación") { recenter++ },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.MyLocation, "Centrar en mi ubicación", tint = c.text) }
                }
            }
            when {
                loc == null || location.permission != PermissionState.GRANTED ||
                        location.error == LocationError.LOCATION_OFF ->
                    LocationPromptCard(location, onRequestPermission, onOpenAppSettings, onOpenLocationSettings, onRefresh)
                selected != null -> PlaceCard(selected, onClose = { onSelectPlace(null) }, onMessage = toast)
                else -> ResultsCard(loc, places, visiblePlaces.size, onOpenSettings, onRefresh, onRetrySearch)
            }
        }
    }
}

@Composable
private fun CardContainer(content: @Composable ColumnScope.() -> Unit) {
    val c = LocalAppColors.current
    Column(
        Modifier.fillMaxWidth().floating(20.dp, c.surfaceStrong).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Composable
private fun LocationPromptCard(
    state: LocationUiState,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    val c = LocalAppColors.current
    CardContainer {
        when {
            state.permission == PermissionState.DENIED_PERMANENTLY -> Prompt(
                Icons.Default.LocationOff, "Ubicación bloqueada",
                "Habilítala en los ajustes de la app para ver los puntos cercanos.",
                "Abrir ajustes", onOpenAppSettings
            )
            state.permission != PermissionState.GRANTED -> Prompt(
                Icons.Default.LocationOn, "Activa tu ubicación",
                "Usamos tu posición para mostrar los lugares de interés cercanos.",
                "Permitir ubicación", onRequestPermission
            )
            state.error == LocationError.LOCATION_OFF -> Prompt(
                Icons.Default.LocationDisabled, "El GPS está apagado",
                "Enciende la ubicación del teléfono para ubicarte en el mapa.",
                "Activar ubicación", onOpenLocationSettings
            )
            state.searching -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(Modifier.size(24.dp), color = c.accent, strokeWidth = 2.5.dp)
                Column {
                    Text("Buscando tu ubicación…", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Puede tardar unos segundos.", color = c.textMuted, fontSize = 13.sp)
                }
            }
            else -> Prompt(
                Icons.Default.LocationDisabled, "No pudimos ubicarte",
                "Sal a un lugar abierto e inténtalo de nuevo.",
                "Reintentar", onRefresh
            )
        }
    }
}

@Composable
private fun ResultsCard(
    location: UserLocation,
    places: PlacesUiState,
    visibleCount: Int,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit
) {
    val c = LocalAppColors.current
    val radius = places.settings.radiusKm
    CardContainer {
        when {
            places.loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(Modifier.size(24.dp), color = c.accent, strokeWidth = 2.5.dp)
                Column {
                    Text("Buscando lugares…", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Radio de $radius km", color = c.textMuted, fontSize = 13.sp)
                }
            }
            places.error == PlacesError.NETWORK -> Prompt(
                Icons.Default.CloudOff, "Sin conexión",
                "Revisa tu internet e inténtalo de nuevo.", "Reintentar", onRetry
            )
            places.error == PlacesError.SERVER -> Prompt(
                Icons.Default.CloudOff, "Servicio ocupado",
                "El servidor de mapas está saturado. Intenta de nuevo en unos segundos.", "Reintentar", onRetry
            )
            visibleCount == 0 -> {
                Prompt(
                    Icons.Default.SearchOff, "Sin lugares en este radio",
                    "Amplía el radio o activa más categorías.", "Abrir ajustes", onOpenSettings
                )
                AppButton("Actualizar", onRefresh, Modifier.fillMaxWidth(), Icons.Default.Refresh, ButtonKind.SECONDARY)
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "$visibleCount ${if (visibleCount == 1) "lugar cerca" else "lugares cerca"}",
                            color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold
                        )
                        Text(
                            when {
                                places.savedCopy -> "Resultados guardados · sin conexión con el servidor"
                                places.failedCategories.isNotEmpty() ->
                                    "No cargó: ${places.failedCategories.joinToString { it.chipLabel }} · pulsa Actualizar"
                                else -> "Radio de $radius km · toca un punto para ver detalles"
                            },
                            color = c.textMuted, fontSize = 13.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.GpsFixed, null, tint = c.blue, modifier = Modifier.size(30.dp))
                        if (location.accuracyMeters < Float.MAX_VALUE) {
                            Text("±${location.accuracyMeters.roundToInt()} m", color = c.blue, fontSize = 12.sp)
                        }
                    }
                }
                AppButton("Actualizar", onRefresh, Modifier.fillMaxWidth(), Icons.Default.Refresh, ButtonKind.SECONDARY)
            }
        }
    }
}

@Composable
private fun PlaceCard(place: Place, onClose: () -> Unit, onMessage: (String) -> Unit) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    CardContainer {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    place.name, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(categoryIcon(place.category), null, tint = c.marker, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(place.category.label, color = c.textMuted, fontSize = 13.sp)
                }
                place.address?.let { Text(it, color = c.textMuted, fontSize = 12.sp) }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 10.dp)) {
                Icon(Icons.Default.NearMe, null, tint = c.blue, modifier = Modifier.size(28.dp))
                Text(formatDistance(place.distanceMeters), color = c.blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Icon(
                Icons.Default.Close, "Cerrar", tint = c.textMuted,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClose).padding(4.dp)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            PlaceAction(Icons.Default.Directions, "Cómo llegar") {
                if (!context.openDirections(place)) onMessage("No hay una app de mapas.")
            }
            place.phone?.let { phone ->
                PlaceAction(Icons.Default.Call, "Llamar") {
                    if (!context.openDialer(phone)) onMessage("No hay una app para llamar.")
                }
            }
            PlaceAction(Icons.Default.Share, "Compartir") {
                if (!context.sharePlace(place)) onMessage("No se pudo compartir.")
            }
        }
    }
}

@Composable
private fun PlaceAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = c.blue, modifier = Modifier.size(26.dp))
        Text(label, color = c.blue, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Prompt(icon: ImageVector, title: String, body: String, action: String, onAction: () -> Unit) {
    val c = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        AccentIconBox(icon)
        Column {
            Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(body, color = c.textMuted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
    AppButton(action, onAction, Modifier.fillMaxWidth())
}