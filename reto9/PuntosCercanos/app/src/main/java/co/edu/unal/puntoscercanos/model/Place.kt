package co.edu.unal.puntoscercanos.model

import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class PlaceCategory(val label: String, val chipLabel: String) {
    HOSPITAL("Hospitales", "Hospitales"),
    TOURISM("Lugares turísticos", "Turismo"),
    PHARMACY("Farmacias", "Farmacias"),
    RESTAURANT("Restaurantes", "Restaurantes")
}

enum class PlacesError { NONE, NETWORK, SERVER }

data class Place(
    val id: String,
    val name: String,
    val category: PlaceCategory,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Int,
    val address: String? = null,
    val phone: String? = null
)

/** Distancia en metros entre dos coordenadas (fórmula de Haversine). */
fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * 6_371_000.0 * asin(sqrt(a))
}

fun formatDistance(meters: Int): String =
    if (meters < 1000) "$meters m" else String.format(Locale.US, "%.1f km", meters / 1000.0)