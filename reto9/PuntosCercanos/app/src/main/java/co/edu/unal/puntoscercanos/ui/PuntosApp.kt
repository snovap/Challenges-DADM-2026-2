package co.edu.unal.puntoscercanos.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

private enum class Route { MAP, SETTINGS }

/** Preferencia visual (modo claro u oscuro) que se recuerda entre sesiones. Arranca en claro, como la referencia. */
private class ThemeSettings(context: Context) {
    private val prefs = context.getSharedPreferences("puntos_prefs", Context.MODE_PRIVATE)
    var dark: Boolean
        get() = prefs.getBoolean("dark", false)
        set(value) = prefs.edit().putBoolean("dark", value).apply()
}

private fun Context.openAppSettings() = startActivity(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
)

private fun Context.openLocationSettings() =
    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

/** Raíz de la app: tema, permisos, búsqueda automática y navegación mapa ↔ ajustes. */
@Composable
fun PuntosApp(
    locationViewModel: LocationViewModel = viewModel(),
    placesViewModel: PlacesViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val settings = remember { ThemeSettings(context) }
    var dark by rememberSaveable { mutableStateOf(settings.dark) }
    var route by rememberSaveable { mutableStateOf(Route.MAP) }
    val locationState by locationViewModel.state.collectAsState()
    val placesState by placesViewModel.state.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.any { it }
        // Si se negó y Android ya no ofrece explicación, el permiso quedó bloqueado: solo se activa en Ajustes.
        val blocked = !granted && LOCATION_PERMISSIONS.none {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }
        locationViewModel.onPermissionResult(granted, blocked)
    }

    // El usuario puede volver de Ajustes con el permiso concedido o el GPS encendido.
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) locationViewModel.onAppResumed()
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    // Búsqueda automática: cuando la ubicación termina de afinarse (no antes) y solo si hace falta.
    LaunchedEffect(locationState.location, locationState.searching) {
        val current = locationState.location
        if (current != null && !locationState.searching) placesViewModel.searchIfNeeded(current)
    }

    BackHandler(enabled = route == Route.SETTINGS) { route = Route.MAP }

    AppTheme(dark = dark) {
        when (route) {
            Route.MAP -> MapScreen(
                location = locationState,
                places = placesState,
                dark = dark,
                onToggleTheme = { dark = !dark; settings.dark = dark },
                onOpenSettings = { route = Route.SETTINGS },
                onRequestPermission = { permissionLauncher.launch(LOCATION_PERMISSIONS) },
                onOpenAppSettings = { context.openAppSettings() },
                onOpenLocationSettings = { context.openLocationSettings() },
                onRefresh = {
                    placesViewModel.markStale()
                    locationViewModel.refreshLocation()
                },
                onRetrySearch = placesViewModel::retry,
                onSelectPlace = placesViewModel::selectPlace,
                onToggleCategory = placesViewModel::toggleCategory
            )

            Route.SETTINGS -> SettingsScreen(
                initial = placesState.settings,
                dark = dark,
                onToggleTheme = { dark = !dark; settings.dark = dark },
                onBack = { route = Route.MAP },
                onSave = { newSettings ->
                    placesViewModel.saveSettings(newSettings)
                    route = Route.MAP
                }
            )
        }
    }
}