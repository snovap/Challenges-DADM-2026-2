package co.edu.unal.puntoscercanos.data

import android.content.Context
import android.util.Log
import co.edu.unal.puntoscercanos.model.Place
import co.edu.unal.puntoscercanos.model.PlaceCategory
import co.edu.unal.puntoscercanos.model.PlacesError
import co.edu.unal.puntoscercanos.model.distanceMeters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.roundToInt

/** Resultado de una búsqueda. */
data class PlacesResult(
    val places: List<Place>,
    /** true si viene de la última búsqueda guardada y no del servidor. */
    val fromSavedCopy: Boolean = false,
    /** Categorías que no se pudieron cargar (resultado parcial). */
    val failedCategories: Set<PlaceCategory> = emptySet()
)

interface PlacesRepository {
    /** Lugares con nombre dentro de [radiusMeters], ordenados del más cercano al más lejano. */
    suspend fun nearby(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        categories: Set<PlaceCategory>
    ): PlacesResult
}

class PlacesException(
    val error: PlacesError,
    cause: Throwable? = null,
    val detail: String? = null,
    /** Tiempo que no se vuelve a molestar a ese servidor (la política de OSM pide ≥ 30 s tras un 429). */
    val cooldownMs: Long = 0L,
    /** true si el servidor aplicó un límite de uso (429/406): se pausa el servidor completo, no solo la categoría. */
    val limited: Boolean = false
) : Exception(detail ?: error.name, cause)

/** Consulta los datos de OpenStreetMap mediante Overpass API, una consulta por categoría. */
class OverpassPlacesRepository(context: Context) : PlacesRepository {

    private class CacheEntry(val savedAtMs: Long, val places: List<Place>)

    private val savedFile = File(context.applicationContext.cacheDir, "last_search.json")
    private val pausedUntil = ConcurrentHashMap<String, Long>()
    private val memory = ConcurrentHashMap<String, CacheEntry>()

    override suspend fun nearby(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        categories: Set<PlaceCategory>
    ): PlacesResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val found = LinkedHashMap<PlaceCategory, List<Place>>()
        val failed = LinkedHashSet<PlaceCategory>()
        var failure: PlacesException? = null

        for (category in categories.sortedBy { it.ordinal }) {
            // Lo ya obtenido hace poco para esta zona no se vuelve a pedir: al reintentar solo se repite lo que falló.
            val cacheKey = "${coord(latitude, 3)}|${coord(longitude, 3)}|$radiusMeters|${category.name}"
            val cached = memory[cacheKey]?.takeIf { System.currentTimeMillis() - it.savedAtMs < FRESH_MS }
            if (cached != null) {
                found[category] = cached.places
                continue
            }
            if (System.currentTimeMillis() - startedAt > TOTAL_BUDGET_MS) {
                failed += category
                continue
            }
            try {
                val places = fetchCategory(category, latitude, longitude, radiusMeters)
                memory[cacheKey] = CacheEntry(System.currentTimeMillis(), places)
                found[category] = places
            } catch (e: PlacesException) {
                failed += category
                if (failure == null || e.error == PlacesError.SERVER) failure = e
            }
        }

        if (found.isNotEmpty()) {
            val places = found.values.flatten().sortedBy { it.distanceMeters }
            if (failed.isEmpty()) saveCopy(latitude, longitude, radiusMeters, categories, places)
            return@withContext PlacesResult(places, failedCategories = failed)
        }

        // Ninguna categoría cargó: se usa la última búsqueda guardada si corresponde a esta zona.
        loadSavedCopy(latitude, longitude, radiusMeters, categories)?.let {
            Log.w(TAG, "Servidores no disponibles: se usa la copia guardada (${it.size} lugares)")
            return@withContext PlacesResult(it, fromSavedCopy = true)
        }
        Log.e(TAG, "Sin resultados. Último motivo: ${failure?.detail ?: "servidores en pausa"}")
        throw failure ?: PlacesException(PlacesError.SERVER, detail = "servidores-en-pausa")
    }

    /** Prueba los servidores en orden hasta que uno responda para esta categoría. */
    private fun fetchCategory(category: PlaceCategory, lat: Double, lon: Double, radiusMeters: Int): List<Place> {
        val query = buildQuery(category, lat, lon, radiusMeters)
        val startedAt = System.currentTimeMillis()
        var failure: PlacesException? = null

        for (endpoint in ENDPOINTS) {
            val now = System.currentTimeMillis()
            if (now - startedAt > CATEGORY_BUDGET_MS) break
            val pausedFor = maxOf(
                pausedUntil[endpoint] ?: 0L,
                pausedUntil["$endpoint|${category.name}"] ?: 0L
            ) - now
            if (pausedFor > 0) {
                Log.d(TAG, "${category.name} @ ${host(endpoint)} en pausa ${pausedFor / 1000} s más")
                continue
            }
            try {
                val places = parse(post(endpoint, query), lat, lon, radiusMeters, category)
                Log.d(TAG, "${category.name} @ ${host(endpoint)} -> OK: ${places.size} lugares en ${System.currentTimeMillis() - now} ms")
                return places
            } catch (e: PlacesException) {
                Log.w(TAG, "${category.name} @ ${host(endpoint)} falló tras ${System.currentTimeMillis() - now} ms (${e.detail ?: e.error})")
                if (e.cooldownMs > 0) {
                    // Un límite de uso pausa el servidor entero; un fallo de consulta solo esa categoría en ese servidor.
                    val key = if (e.limited) endpoint else "$endpoint|${category.name}"
                    pausedUntil[key] = System.currentTimeMillis() + e.cooldownMs
                }
                if (failure == null || e.error == PlacesError.SERVER) failure = e
            }
        }
        throw failure ?: PlacesException(PlacesError.SERVER, detail = "servidores-en-pausa")
    }

    // ---------- Consulta ----------

    /** Caja (sur, oeste, norte, este) que contiene el círculo de búsqueda. */
    private fun bbox(lat: Double, lon: Double, radiusMeters: Int): String {
        val dLat = radiusMeters / 111_320.0
        val dLon = radiusMeters / (111_320.0 * cos(Math.toRadians(lat)))
        return "(${coord(lat - dLat, 6)},${coord(lon - dLon, 6)},${coord(lat + dLat, 6)},${coord(lon + dLon, 6)})"
    }

    private fun buildQuery(category: PlaceCategory, lat: Double, lon: Double, radiusMeters: Int): String {
        val box = bbox(lat, lon, radiusMeters)
        val statements = RULES.getValue(category).flatMap { (key, values) ->
            values.map { value -> "  nwr[\"$key\"=\"$value\"][\"name\"]$box;" }
        }.joinToString("\n")
        // timeout del servidor menor que el de la app: si la consulta es pesada, el servidor lo dice en vez de callar.
        return "[out:json][timeout:$SERVER_TIMEOUT_S];\n(\n$statements\n);\nout center $MAX_RAW;"
    }

    private fun post(endpoint: String, query: String): String {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("User-Agent", USER_AGENT) // exigido por la política de uso de Overpass
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            }
            connection.outputStream.use {
                it.write("data=${URLEncoder.encode(query, "UTF-8")}".toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val text = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                Log.w(TAG, "${host(endpoint)} -> HTTP $code: ${text.take(160)}")
                val limited = code == 429 || code == 406 // la política de OSM pide esperar ≥ 30 s tras estos códigos
                throw PlacesException(
                    PlacesError.SERVER, detail = "HTTP $code",
                    cooldownMs = if (limited) LIMITED_COOLDOWN_MS else BUSY_COOLDOWN_MS, limited = limited
                )
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: SocketTimeoutException) {
            throw PlacesException(PlacesError.SERVER, e, "sin respuesta en ${READ_TIMEOUT_MS / 1000} s", BUSY_COOLDOWN_MS)
        } catch (e: IOException) {
            // Sin internet o servidor inalcanzable: no consume capacidad del servidor, así que no se pausa.
            throw PlacesException(PlacesError.NETWORK, e, "${e.javaClass.simpleName}: ${e.message}")
        } finally {
            connection.disconnect()
        }
    }

    // ---------- Interpretación de la respuesta ----------

    private fun parse(body: String, lat: Double, lon: Double, radiusMeters: Int, category: PlaceCategory): List<Place> {
        val root = try {
            JSONObject(body)
        } catch (e: JSONException) {
            Log.w(TAG, "La respuesta no es JSON: ${body.take(200)}")
            throw PlacesException(PlacesError.SERVER, e, "respuesta-no-json", BUSY_COOLDOWN_MS)
        }
        val elements = root.optJSONArray("elements") ?: JSONArray()
        // Overpass puede responder 200 con un aviso de error (p. ej. consulta demasiado pesada) y sin resultados.
        val remark = root.optString("remark")
        if (elements.length() == 0 && remark.contains("error", ignoreCase = true)) {
            throw PlacesException(PlacesError.SERVER, detail = "servidor: ${remark.take(120)}", cooldownMs = BUSY_COOLDOWN_MS)
        }

        val seen = HashSet<String>()
        val found = ArrayList<Place>()
        for (i in 0 until elements.length()) {
            val element = elements.optJSONObject(i) ?: continue
            val tags = element.optJSONObject("tags") ?: continue
            val name = tags.optString("name").trim()
            if (name.isEmpty() || !matches(category, tags)) continue
            val (placeLat, placeLon) = coordinatesOf(element) ?: continue
            val id = "${element.optString("type")}/${element.optLong("id")}"
            if (!seen.add(id)) continue
            val distance = distanceMeters(lat, lon, placeLat, placeLon)
            if (distance > radiusMeters) continue // la caja es cuadrada: aquí se recorta al círculo
            found += Place(id, name, category, placeLat, placeLon, distance.roundToInt(), addressOf(tags), phoneOf(tags))
        }
        return dedupe(found.sortedBy { it.distanceMeters }).take(MAX_PER_CATEGORY)
    }

    private fun matches(category: PlaceCategory, tags: JSONObject) =
        RULES.getValue(category).any { (key, values) -> tags.optString(key) in values }

    /** Nodos traen lat/lon; áreas y relaciones traen el punto central en "center". */
    private fun coordinatesOf(element: JSONObject): Pair<Double, Double>? =
        if (element.has("lat") && element.has("lon")) {
            element.getDouble("lat") to element.getDouble("lon")
        } else {
            element.optJSONObject("center")?.let { it.getDouble("lat") to it.getDouble("lon") }
        }

    private fun addressOf(tags: JSONObject): String? =
        listOf(tags.optString("addr:street"), tags.optString("addr:housenumber"))
            .filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }

    private fun phoneOf(tags: JSONObject): String? =
        tags.optString("phone").ifBlank { tags.optString("contact:phone") }.ifBlank { null }

    /** Un mismo hospital puede venir como punto y como edificio: se descarta el duplicado cercano. */
    private fun dedupe(sorted: List<Place>): List<Place> {
        val kept = ArrayList<Place>()
        for (place in sorted) {
            val duplicate = kept.any {
                it.name.equals(place.name, ignoreCase = true) &&
                        distanceMeters(it.latitude, it.longitude, place.latitude, place.longitude) < 150
            }
            if (!duplicate) kept += place
        }
        return kept
    }

    // ---------- Copia guardada de la última búsqueda completa ----------

    private fun saveCopy(lat: Double, lon: Double, radiusMeters: Int, categories: Set<PlaceCategory>, places: List<Place>) {
        try {
            val array = JSONArray()
            places.forEach { place ->
                array.put(JSONObject().apply {
                    put("id", place.id)
                    put("name", place.name)
                    put("cat", place.category.name)
                    put("lat", place.latitude)
                    put("lon", place.longitude)
                    place.address?.let { put("addr", it) }
                    place.phone?.let { put("phone", it) }
                })
            }
            val root = JSONObject().apply {
                put("savedAt", System.currentTimeMillis())
                put("lat", lat)
                put("lon", lon)
                put("radius", radiusMeters)
                put("cats", JSONArray(categories.map { it.name }))
                put("places", array)
            }
            savedFile.writeText(root.toString())
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo guardar la copia", e)
        }
    }

    /**
     * Devuelve la copia guardada si cubre la zona pedida (mismo punto, hasta 1,5 km de diferencia, mismo o mayor
     * radio y todas las categorías) y tiene menos de 24 h. Las distancias se recalculan desde la posición actual.
     */
    private fun loadSavedCopy(lat: Double, lon: Double, radiusMeters: Int, categories: Set<PlaceCategory>): List<Place>? = try {
        if (!savedFile.exists()) null else {
            val root = JSONObject(savedFile.readText())
            val offset = distanceMeters(lat, lon, root.getDouble("lat"), root.getDouble("lon"))
            val savedCategories = root.getJSONArray("cats").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
            val usable = System.currentTimeMillis() - root.getLong("savedAt") <= SAVED_MAX_AGE_MS &&
                    offset <= SAVED_MAX_OFFSET_M &&
                    root.getInt("radius") + SAVED_EDGE_TOLERANCE_M >= radiusMeters + offset &&
                    categories.all { it.name in savedCategories }
            if (!usable) null else {
                val array = root.getJSONArray("places")
                (0 until array.length()).mapNotNull { i ->
                    val item = array.getJSONObject(i)
                    val category = PlaceCategory.entries.firstOrNull { it.name == item.getString("cat") }
                        ?: return@mapNotNull null
                    val placeLat = item.getDouble("lat")
                    val placeLon = item.getDouble("lon")
                    Place(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        category = category,
                        latitude = placeLat,
                        longitude = placeLon,
                        distanceMeters = distanceMeters(lat, lon, placeLat, placeLon).roundToInt(),
                        address = item.optString("addr").ifBlank { null },
                        phone = item.optString("phone").ifBlank { null }
                    )
                }.filter { it.category in categories && it.distanceMeters <= radiusMeters }
                    .sortedBy { it.distanceMeters }
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo leer la copia guardada", e)
        null
    }

    private fun coord(value: Double, decimals: Int) = String.format(Locale.US, "%.${decimals}f", value)

    /** Solo el nombre del servidor en los registros, para no exponer la clave. */
    private fun host(endpoint: String) = URL(endpoint).host

    private companion object {
        const val TAG = "Overpass"

        // Primero las instancias con clave propia (si las configuraste); luego las públicas.
        val ENDPOINTS = buildList {
            if (OverpassKeys.NEXTGIS.isNotBlank()) add("https://overpass.nextgis.com/${OverpassKeys.NEXTGIS}/api/interpreter")
            if (OverpassKeys.OVERPASS_CH.isNotBlank()) add("https://api.overpass.ch/v1/${OverpassKeys.OVERPASS_CH}/interpreter")
            add("https://overpass-api.de/api/interpreter")
            add("https://maps.mail.ru/osm/tools/overpass/api/interpreter")
            add("https://overpass.private.coffee/api/interpreter")
        }
        const val USER_AGENT = "PuntosCercanos/1.0 (co.edu.unal.puntoscercanos; proyecto academico)"

        const val MAX_RAW = 600
        const val MAX_PER_CATEGORY = 60

        const val SERVER_TIMEOUT_S = 20
        const val CONNECT_TIMEOUT_MS = 6_000
        const val READ_TIMEOUT_MS = 28_000
        const val CATEGORY_BUDGET_MS = 45_000L
        const val TOTAL_BUDGET_MS = 60_000L
        const val BUSY_COOLDOWN_MS = 20_000L
        const val LIMITED_COOLDOWN_MS = 60_000L
        const val FRESH_MS = 5 * 60_000L

        const val SAVED_MAX_AGE_MS = 24 * 60 * 60_000L
        const val SAVED_MAX_OFFSET_M = 1_500.0
        const val SAVED_EDGE_TOLERANCE_M = 300

        /** Etiquetas de OpenStreetMap que definen cada categoría. Se usan para consultar y para clasificar. */
        val RULES: Map<PlaceCategory, List<Pair<String, Set<String>>>> = mapOf(
            PlaceCategory.HOSPITAL to listOf("amenity" to setOf("hospital", "clinic")),
            PlaceCategory.PHARMACY to listOf("amenity" to setOf("pharmacy")),
            PlaceCategory.RESTAURANT to listOf("amenity" to setOf("restaurant", "cafe")),
            PlaceCategory.TOURISM to listOf(
                "tourism" to setOf("attraction", "museum", "viewpoint", "gallery", "zoo", "theme_park"),
                "historic" to setOf("monument", "castle", "ruins", "archaeological_site")
            )
        )
    }
}