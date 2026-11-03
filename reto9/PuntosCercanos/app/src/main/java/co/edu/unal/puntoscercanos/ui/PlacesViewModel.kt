package co.edu.unal.puntoscercanos.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import co.edu.unal.puntoscercanos.data.OverpassPlacesRepository
import co.edu.unal.puntoscercanos.data.PlacesException
import co.edu.unal.puntoscercanos.data.PlacesRepository
import co.edu.unal.puntoscercanos.data.SearchSettings
import co.edu.unal.puntoscercanos.data.SettingsRepository
import co.edu.unal.puntoscercanos.location.UserLocation
import co.edu.unal.puntoscercanos.model.Place
import co.edu.unal.puntoscercanos.model.PlaceCategory
import co.edu.unal.puntoscercanos.model.PlacesError
import co.edu.unal.puntoscercanos.model.distanceMeters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlacesUiState(
    val settings: SearchSettings,
    val places: List<Place> = emptyList(),
    val hiddenCategories: Set<PlaceCategory> = emptySet(),
    val selectedId: String? = null,
    val loading: Boolean = false,
    val error: PlacesError = PlacesError.NONE,
    val savedCopy: Boolean = false,
    val failedCategories: Set<PlaceCategory> = emptySet(),
    val resultVersion: Int = 0
) {
    val visiblePlaces: List<Place> get() = places.filter { it.category !in hiddenCategories }
    val selectedPlace: Place? get() = places.firstOrNull { it.id == selectedId }
}

/** Preferencias + búsqueda de lugares cercanos. Sobrevive a rotaciones. */
class PlacesViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val placesRepository: PlacesRepository = OverpassPlacesRepository(application)

    private val _state = MutableStateFlow(PlacesUiState(settings = settingsRepository.load()))
    val state: StateFlow<PlacesUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var lastLocation: UserLocation? = null
    private var lastSearched: UserLocation? = null

    /** Busca solo si es la primera vez o si el usuario se movió más de 100 m desde la última búsqueda. */
    fun searchIfNeeded(location: UserLocation) {
        lastLocation = location
        val previous = lastSearched
        val moved = previous == null ||
                distanceMeters(previous.latitude, previous.longitude, location.latitude, location.longitude) > REFRESH_DISTANCE_M
        if (moved) search(location)
    }

    /** Fuerza una nueva búsqueda en cuanto llegue la próxima ubicación. */
    fun markStale() { lastSearched = null }

    fun retry() { lastLocation?.let(::search) }

    fun saveSettings(settings: SearchSettings) {
        settingsRepository.save(settings)
        _state.update { it.copy(settings = settings, hiddenCategories = emptySet(), selectedId = null) }
        lastLocation?.let(::search)
    }

    fun selectPlace(id: String?) = _state.update { it.copy(selectedId = id) }

    fun toggleCategory(category: PlaceCategory) = _state.update {
        val hidden = if (category in it.hiddenCategories) it.hiddenCategories - category else it.hiddenCategories + category
        it.copy(hiddenCategories = hidden, selectedId = null)
    }

    private fun search(location: UserLocation) {
        searchJob?.cancel()
        lastSearched = location
        val settings = _state.value.settings
        searchJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = PlacesError.NONE, selectedId = null) }
            try {
                val result = placesRepository.nearby(
                    location.latitude, location.longitude, settings.radiusKm * 1000, settings.categories
                )
                _state.update {
                    it.copy(
                        places = result.places,
                        savedCopy = result.fromSavedCopy,
                        failedCategories = result.failedCategories,
                        loading = false,
                        resultVersion = it.resultVersion + 1
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PlacesException) {
                fail(e.error)
            } catch (e: Exception) {
                Log.e("Overpass", "Error inesperado en la búsqueda", e)
                fail(PlacesError.SERVER)
            }
        }
    }

    private fun fail(error: PlacesError) {
        lastSearched = null // permite reintentar en cuanto cambie la ubicación o se pulse "Reintentar"
        _state.update { it.copy(loading = false, error = error) }
    }

    private companion object {
        const val REFRESH_DISTANCE_M = 100.0
    }
}