# Puntos cercanos — Reto 9: Accediendo al GPS

Aplicación Android (Kotlin + Jetpack Compose) que toma la posición GPS del usuario y muestra en un mapa los
puntos de interés cercanos (hospitales, lugares turísticos, farmacias y restaurantes) dentro de un radio en
kilómetros configurable. Cada punto se muestra con su nombre.

## Requisitos del reto

| Requisito | Implementación |
|---|---|
| Coordenadas GPS del usuario | `LocationManager` (GPS + red), permisos en tiempo de ejecución |
| Puntos de interés en un mapa | Mapa OpenStreetMap con osmdroid; marcadores con el nombre de cada lugar |
| Radio en km mediante preferencias | Pantalla de ajustes (1–20 km) guardada con `SharedPreferences` |
| Nombre de cada punto de interés | Etiqueta bajo cada marcador y tarjeta de detalle |

## Cómo funciona

1. Se pide el permiso de ubicación y se obtiene la posición (primero aproximada, luego precisa).
2. Se consultan los lugares a la API Overpass (datos de OpenStreetMap), una consulta por categoría.
3. Los resultados se filtran por distancia (Haversine) y se dibujan en el mapa con el círculo del radio.
4. Al tocar un lugar se ve su categoría, distancia y acciones (Cómo llegar, Llamar, Compartir).

## Arquitectura

- `location/`: acceso al GPS (`LocationRepository`).
- `data/`: Overpass (`PlacesRepository`) y preferencias (`SettingsRepository`).
- `model/`: `Place`, `PlaceCategory`.
- `map/`: integración de osmdroid con Compose.
- `ui/`: ViewModels y pantallas (mapa y ajustes).

## Ejecutar

1. Abrir en Android Studio y ejecutar en un dispositivo con Android 7.0 (API 24) o superior.
2. Activar la ubicación del teléfono y tener internet.

### Clave de Overpass (opcional)

Los servidores públicos de Overpass suelen estar saturados. La app prueba varios y, opcionalmente, una
instancia con clave gratuita propia. Para usarla, pegar la clave en `data/OverpassKeys.kt`
(NextGIS: https://my.nextgis.com/software?tab=api-keys). Este repositorio la deja vacía.

## Limitaciones

- Overpass es un servicio público compartido: si todos los servidores fallan, se muestra la última búsqueda
  guardada (hasta 24 h y 1,5 km) o un mensaje de error.
- Se muestran como máximo los 60 lugares más cercanos de cada categoría.
- osmdroid está archivado (última versión 6.1.20, agosto de 2024).

## Créditos

Datos y mapa: © OpenStreetMap contributors (licencia ODbL).