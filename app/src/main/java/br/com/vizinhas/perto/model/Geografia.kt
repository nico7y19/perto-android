package br.com.vizinhas.perto.model

import kotlin.math.*

data class PontoMapa(val latitude: Double, val longitude: Double)

object Geografia {
    // Aceita apenas pares completos de coordenadas finitas dentro dos limites geográficos.
    fun coordenadasValidas(latitude: Double?, longitude: Double?): Boolean =
        latitude != null && longitude != null && latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0

    // Calcula a distância aproximada em linha reta entre dois pontos, em quilômetros.
    fun distanciaKm(a: PontoMapa, b: PontoMapa): Double {
        val lat = Math.toRadians(b.latitude - a.latitude)
        val lon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(lon / 2).pow(2)
        return 6371.0 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
