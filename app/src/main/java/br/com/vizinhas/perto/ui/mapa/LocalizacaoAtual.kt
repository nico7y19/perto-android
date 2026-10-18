package br.com.vizinhas.perto.ui.mapa

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import br.com.vizinhas.perto.model.PontoMapa

class LocalizacaoAtual(private val contexto: Context, private val resultado: (PontoMapa?) -> Unit) {
    private var sinal: CancellationSignal? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ativo = false
    private val limite = Runnable { concluir(null) }

    // Obtém uma posição somente após a tela verificar a permissão, com limite de espera.
    @SuppressLint("MissingPermission")
    fun buscar() {
        cancelar()
        ativo = true
        try {
            val servico = contexto.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val provedor = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .firstOrNull { servico.isProviderEnabled(it) }
            if (provedor == null) { concluir(null); return }
            val cancelamento = CancellationSignal()
            sinal = cancelamento
            handler.postDelayed(limite, 25_000)
            LocationManagerCompat.getCurrentLocation(servico, provedor, cancelamento, ContextCompat.getMainExecutor(contexto)) { local ->
                concluir(local?.let { PontoMapa(it.latitude, it.longitude) })
            }
        } catch (_: SecurityException) { concluir(null) }
          catch (_: IllegalArgumentException) { concluir(null) }
    }

    // Entrega uma única resposta e libera a solicitação nativa de localização.
    private fun concluir(ponto: PontoMapa?) {
        if (!ativo) return
        cancelar()
        resultado(ponto)
    }

    // Encerra a solicitação quando a tela deixa de estar visível.
    fun cancelar() {
        ativo = false
        handler.removeCallbacks(limite)
        sinal?.cancel()
        sinal = null
    }
}
