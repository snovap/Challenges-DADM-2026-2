package co.edu.unal.puntoscercanos.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.merge

/** Posición del usuario, independiente de las clases de Android. */
data class UserLocation(val latitude: Double, val longitude: Double, val accuracyMeters: Float)

interface LocationRepository {
    fun hasPermission(): Boolean
    fun isLocationEnabled(): Boolean

    /** Emite posiciones (primero las rápidas y aproximadas, luego las precisas) hasta cancelar el flujo. */
    fun locationUpdates(): Flow<UserLocation>
}

class AndroidLocationRepository(context: Context) : LocationRepository {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    override fun hasPermission() =
        granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    override fun isLocationEnabled() = LocationManagerCompat.isLocationEnabled(manager)

    override fun locationUpdates(): Flow<UserLocation> {
        // El GPS exige permiso preciso; con solo ubicación aproximada se usa únicamente la red.
        val providers = buildList {
            if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }.filter { manager.isProviderEnabled(it) }

        return providers.map { fixesFrom(it) }.merge()
    }

    @SuppressLint("MissingPermission")
    private fun fixesFrom(provider: String): Flow<UserLocation> = callbackFlow {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(
                    UserLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE
                    )
                )
            }

            // Obligatorios en Android anterior al 11, donde aún no son opcionales.
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        manager.requestLocationUpdates(provider, 1_000L, 0f, listener, Looper.getMainLooper())
        awaitClose { manager.removeUpdates(listener) }
    }
}