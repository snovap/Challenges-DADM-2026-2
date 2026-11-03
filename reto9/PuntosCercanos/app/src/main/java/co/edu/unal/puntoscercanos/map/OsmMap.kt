package co.edu.unal.puntoscercanos.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import co.edu.unal.puntoscercanos.model.Place
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File

private val DEFAULT_CENTER = GeoPoint(4.7110, -74.0721) // Bogotá, mientras no hay ubicación
private const val DEFAULT_ZOOM = 13.0
private const val USER_ZOOM = 16.0
private const val MIN_LABEL_ZOOM = 12.0
private const val MAX_LABEL_CHARS = 24

/** Configuración global de osmdroid. Se llama una vez, antes de crear cualquier MapView. */
object OsmSetup {
    fun init(context: Context) {
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = context.packageName // obligatorio: identifica la app ante los servidores de mosaicos
            osmdroidBasePath = File(context.cacheDir, "osmdroid") // caché interna: sin permisos de almacenamiento
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
        }
    }
}

private fun dot(context: Context, color: Int, sizeDp: Int, strokeDp: Int): Drawable {
    val density = context.resources.displayMetrics.density
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke((strokeDp * density).toInt(), AndroidColor.WHITE)
        setSize((sizeDp * density).toInt(), (sizeDp * density).toInt())
    }
}

/**
 * Dibuja el nombre de cada lugar bajo su marcador. Los lugares llegan ordenados por cercanía, así que
 * los más cercanos tienen prioridad y las etiquetas que se solaparían con otra ya dibujada se omiten.
 */
private class LabelsOverlay(
    private val places: List<Place>,
    private val density: Float,
    textColor: Int,
    haloColor: Int
) : Overlay() {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = 12f * density
        typeface = Typeface.DEFAULT_BOLD
    }
    private val haloPaint = Paint(textPaint).apply {
        color = haloColor
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeJoin = Paint.Join.ROUND
    }
    private val point = Point()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || mapView.zoomLevelDouble < MIN_LABEL_ZOOM) return
        val projection = mapView.projection
        val screen = RectF(0f, 0f, mapView.width.toFloat(), mapView.height.toFloat())
        val occupied = ArrayList<RectF>()
        for (place in places) {
            projection.toPixels(GeoPoint(place.latitude, place.longitude), point)
            val text = if (place.name.length > MAX_LABEL_CHARS) place.name.take(MAX_LABEL_CHARS - 1) + "…" else place.name
            val width = textPaint.measureText(text)
            val left = point.x - width / 2f
            val top = point.y + 12f * density
            val box = RectF(left - 2f * density, top, left + width + 2f * density, top + 16f * density)
            if (!RectF.intersects(screen, box) || occupied.any { RectF.intersects(it, box) }) continue
            occupied += box
            val baseline = box.bottom - 4f * density
            canvas.drawText(text, left, baseline, haloPaint)
            canvas.drawText(text, left, baseline, textPaint)
        }
    }
}

/** MapView de osmdroid (basado en Views) envuelto para usarse dentro de Compose. */
@Composable
fun OsmMap(
    modifier: Modifier = Modifier,
    dark: Boolean,
    userDotColor: Int,
    markerColor: Int,
    selectedColor: Int,
    labelColor: Int,
    labelHaloColor: Int,
    circleColor: Int,
    userLocation: GeoPoint?,
    recenterSignal: Int,
    autoCenter: Boolean,
    places: List<Place>,
    selectedId: String?,
    radiusMeters: Int,
    fitSignal: Int,
    onPlaceClick: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycle = (context as ComponentActivity).lifecycle
    val density = context.resources.displayMetrics.density
    val currentOnPlaceClick by rememberUpdatedState(onPlaceClick)

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(DEFAULT_ZOOM)
            controller.setCenter(DEFAULT_CENTER)
        }
    }
    val userMarker = remember {
        Marker(mapView).apply {
            title = "Tu ubicación"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        }
    }

    // Al crear el mapa (p. ej. tras rotar o volver de ajustes) se centra en la última posición conocida.
    LaunchedEffect(Unit) {
        userLocation?.let {
            mapView.controller.setZoom(USER_ZOOM)
            mapView.controller.setCenter(it)
        }
    }
    // Mientras se afina la posición, la cámara sigue al usuario.
    LaunchedEffect(userLocation, autoCenter) {
        if (userLocation != null && autoCenter) {
            if (mapView.zoomLevelDouble < 15.0) mapView.controller.setZoom(USER_ZOOM)
            mapView.controller.animateTo(userLocation)
        }
    }
    // Botón "centrar en mi ubicación".
    LaunchedEffect(recenterSignal) {
        if (recenterSignal > 0) userLocation?.let { mapView.controller.animateTo(it) }
    }
    // Al llegar resultados nuevos, se encuadra todo el círculo de búsqueda.
    LaunchedEffect(fitSignal) {
        val center = userLocation
        if (fitSignal > 0 && center != null) {
            val box = BoundingBox.fromGeoPoints(Polygon.pointsAsCircle(center, radiusMeters.toDouble()))
            mapView.zoomToBoundingBox(box, true, (40 * density).toInt())
        }
    }
    // Al elegir un lugar, la cámara se desplaza hacia él.
    LaunchedEffect(selectedId) {
        places.firstOrNull { it.id == selectedId }?.let {
            mapView.controller.animateTo(GeoPoint(it.latitude, it.longitude))
        }
    }

    // osmdroid pide avisarle del ciclo de vida para pausar y reanudar la carga de mosaicos.
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            // Modo oscuro: invierte los colores de los mosaicos para que combinen con la app.
            view.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
            view.overlays.clear()

            // Orden de capas (de abajo hacia arriba): radio, lugares, nombres, tu posición.
            if (userLocation != null) {
                view.overlays.add(Polygon(view).apply {
                    points = Polygon.pointsAsCircle(userLocation, radiusMeters.toDouble())
                    fillPaint.color = (circleColor and 0x00FFFFFF) or (0x1F shl 24)
                    outlinePaint.color = circleColor
                    outlinePaint.strokeWidth = 2f * density
                    setOnClickListener { _, _, _ -> true } // evita que abra un globo de información vacío
                })
            }

            val normalIcon = dot(context, markerColor, 18, 3)
            val selectedIcon = dot(context, selectedColor, 28, 4)
            // El lugar seleccionado se agrega al final para que quede encima de los demás.
            (places.filter { it.id != selectedId } + places.filter { it.id == selectedId }).forEach { place ->
                view.overlays.add(Marker(view).apply {
                    position = GeoPoint(place.latitude, place.longitude)
                    title = place.name
                    icon = if (place.id == selectedId) selectedIcon else normalIcon
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setOnMarkerClickListener { _, _ -> currentOnPlaceClick(place.id); true }
                })
            }
            view.overlays.add(LabelsOverlay(places, density, labelColor, labelHaloColor))

            if (userLocation != null) {
                userMarker.icon = dot(context, userDotColor, 22, 3)
                userMarker.position = userLocation
                view.overlays.add(userMarker)
            }
            view.invalidate()
        }
    )
}