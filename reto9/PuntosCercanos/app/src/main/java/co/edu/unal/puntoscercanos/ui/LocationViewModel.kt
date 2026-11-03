package co.edu.unal.puntoscercanos.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.edu.unal.puntoscercanos.location.AndroidLocationRepository
import co.edu.unal.puntoscercanos.location.LocationRepository
import co.edu.unal.puntoscercanos.location.UserLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class PermissionState { UNKNOWN, GRANTED, DENIED, DENIED_PERMANENTLY }
enum class LocationError { NONE, LOCATION_OFF, NO_FIX }

data class LocationUiState(
    val permission: PermissionState = PermissionState.UNKNOWN,
    val location: UserLocation? = null,
    val searching: Boolean = false,
    val error: LocationError = LocationError.NONE
)

/** Estado de permiso y ubicación del usuario. Sobrevive a rotaciones. */
class LocationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: LocationRepository = AndroidLocationRepository(application)

    private val _state = MutableStateFlow(LocationUiState())
    val state: StateFlow<LocationUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init { onAppResumed() }

    /** Al volver al primer plano el usuario pudo cambiar permisos o encender el GPS. */
    fun onAppResumed() {
        val granted = repository.hasPermission()
        _state.update { s ->
            when {
                granted && s.permission != PermissionState.GRANTED -> s.copy(permission = PermissionState.GRANTED)
                !granted && s.permission == PermissionState.GRANTED ->
                    s.copy(permission = PermissionState.UNKNOWN, location = null)
                else -> s
            }
        }
        val s = _state.value
        if (s.permission == PermissionState.GRANTED && s.location == null && !s.searching) refreshLocation()
    }

    fun onPermissionResult(granted: Boolean, permanentlyDenied: Boolean) {
        _state.update {
            it.copy(
                permission = when {
                    granted -> PermissionState.GRANTED
                    permanentlyDenied -> PermissionState.DENIED_PERMANENTLY
                    else -> PermissionState.DENIED
                }
            )
        }
        if (granted) refreshLocation()
    }

    /** Busca la posición: muestra la primera que llegue y la va afinando hasta ±30 m o 20 s. */
    fun refreshLocation() {
        if (!repository.hasPermission()) {
            _state.update { if (it.permission == PermissionState.GRANTED) it.copy(permission = PermissionState.UNKNOWN) else it }
            return
        }
        searchJob?.cancel()
        if (!repository.isLocationEnabled()) {
            _state.update { it.copy(error = LocationError.LOCATION_OFF, searching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            _state.update { it.copy(searching = true, error = LocationError.NONE) }
            var best: UserLocation? = null
            try {
                withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                    repository.locationUpdates()
                        .onEach { fix ->
                            val current = best
                            if (current == null || fix.accuracyMeters <= current.accuracyMeters) {
                                best = fix
                                _state.update { it.copy(location = fix) }
                            }
                        }
                        .firstOrNull { it.accuracyMeters <= GOOD_ACCURACY_M }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) { // el usuario revocó el permiso mientras se buscaba
                _state.update { it.copy(permission = PermissionState.UNKNOWN, location = null, searching = false) }
                return@launch
            }
            _state.update {
                it.copy(
                    searching = false,
                    error = if (best == null && it.location == null) LocationError.NO_FIX else LocationError.NONE
                )
            }
        }
    }

    private companion object {
        const val SEARCH_TIMEOUT_MS = 20_000L
        const val GOOD_ACCURACY_M = 30f
    }
}
