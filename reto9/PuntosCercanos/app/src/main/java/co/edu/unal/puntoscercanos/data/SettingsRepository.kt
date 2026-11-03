package co.edu.unal.puntoscercanos.data

import android.content.Context
import co.edu.unal.puntoscercanos.model.PlaceCategory

data class SearchSettings(
    val radiusKm: Int = 3,
    val categories: Set<PlaceCategory> = setOf(PlaceCategory.HOSPITAL, PlaceCategory.TOURISM)
) {
    companion object {
        const val MIN_RADIUS_KM = 1
        const val MAX_RADIUS_KM = 20
    }
}

/** Preferencias del usuario (radio y categorías) guardadas con SharedPreferences. */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("puntos_settings", Context.MODE_PRIVATE)

    fun load(): SearchSettings {
        val defaults = SearchSettings()
        val radius = prefs.getInt(KEY_RADIUS, defaults.radiusKm)
            .coerceIn(SearchSettings.MIN_RADIUS_KM, SearchSettings.MAX_RADIUS_KM)
        val categories = prefs.getStringSet(KEY_CATEGORIES, null)
            ?.mapNotNull { saved -> PlaceCategory.entries.firstOrNull { it.name == saved } }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: defaults.categories
        return SearchSettings(radius, categories)
    }

    fun save(settings: SearchSettings) {
        prefs.edit()
            .putInt(KEY_RADIUS, settings.radiusKm)
            .putStringSet(KEY_CATEGORIES, settings.categories.map { it.name }.toSet())
            .apply()
    }

    private companion object {
        const val KEY_RADIUS = "radius_km"
        const val KEY_CATEGORIES = "categories"
    }
}