package br.com.vizinhas.perto.ui.mapa

import android.content.Context
import android.location.Geocoder
import br.com.vizinhas.perto.model.PontoMapa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

// Usa o serviço do dispositivo fora da thread principal e permite seleção manual se indisponível.
@Suppress("DEPRECATION")
suspend fun buscarEndereco(contexto: Context, endereco: String): PontoMapa? = withContext(Dispatchers.IO) {
    if (!Geocoder.isPresent() || endereco.isBlank()) return@withContext null
    try {
        Geocoder(contexto, Locale.forLanguageTag("pt-BR")).getFromLocationName(endereco.take(300), 1)
            ?.firstOrNull()?.let { PontoMapa(it.latitude, it.longitude) }
    } catch (_: java.io.IOException) { null }
      catch (_: IllegalArgumentException) { null }
}
