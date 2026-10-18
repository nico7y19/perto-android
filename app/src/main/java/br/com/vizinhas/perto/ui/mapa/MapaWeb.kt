package br.com.vizinhas.perto.ui.mapa

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import br.com.vizinhas.perto.model.Evento
import br.com.vizinhas.perto.model.Geografia
import br.com.vizinhas.perto.model.PontoMapa
import br.com.vizinhas.perto.ui.eventos.FormatoEvento
import org.json.JSONArray
import org.json.JSONObject

class MapaWeb(private val web: WebView, private val selecionar: (PontoMapa) -> Unit, private val abrir: (String) -> Unit) {
    private var pronto = false
    private var fechado = false
    private var centro: PontoMapa? = null
    private var ponto: PontoMapa? = null
    private var referencia: Pair<PontoMapa, Double>? = null
    private var eventos = emptyList<Evento>()
    private val origem = "https://appassets.androidplatform.net/mapa/"

    // Ativa JavaScript apenas para os arquivos locais do mapa e bloqueia navegação remota na WebView.
    @SuppressLint("SetJavaScriptEnabled")
    fun iniciar() {
        web.settings.javaScriptEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.settings.domStorageEnabled = false
        web.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        web.settings.userAgentString = "PertoAndroid/1.0 (br.com.vizinhas.perto; Android)"
        web.addJavascriptInterface(Ponte(), "PertoAndroid")
        web.webViewClient = object : WebViewClient() {
            // Serve apenas arquivos conhecidos do APK e permite imagens do provedor de mapas.
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url
                val nome = url.lastPathSegment.orEmpty()
                val tipos = mapOf("index.html" to "text/html", "mapa.js" to "application/javascript", "leaflet.js" to "application/javascript", "mapa.css" to "text/css", "leaflet.css" to "text/css")
                if (url.scheme == "https" && url.host == "appassets.androidplatform.net" && url.path == "/mapa/$nome" && nome in tipos) {
                    return WebResourceResponse(tipos.getValue(nome), "UTF-8", web.context.assets.open("mapa/$nome"))
                }
                if (url.scheme == "https" && url.host == "tile.openstreetmap.org" && url.path.orEmpty().endsWith(".png")) return null
                return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), java.io.ByteArrayInputStream(byteArrayOf()))
            }

            // Abre somente a atribuição do OpenStreetMap no navegador externo, mantendo a ponte isolada.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.toString() == origem + "index.html") return false
                if (request.url.scheme == "https" && request.url.host == "www.openstreetmap.org") {
                    try { web.context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) } catch (_: android.content.ActivityNotFoundException) { }
                }
                return true
            }

            // Reaplica o estado recebido enquanto a biblioteca local ainda estava carregando.
            override fun onPageFinished(view: WebView, url: String) {
                if (fechado || url != origem + "index.html") return
                pronto = true
                centro?.let { centralizar(it) }
                ponto?.let { selecionarPonto(it) }
                referencia?.let { mostrarReferencia(it.first, it.second) }
                mostrarEventos(eventos)
            }
        }
        web.loadUrl(origem + "index.html")
    }

    // Centraliza a visualização sem transformar o centro em uma escolha do organizador.
    fun centralizar(local: PontoMapa) {
        centro = local
        executar("centro", "${local.latitude},${local.longitude}")
    }

    // Destaca o ponto escolhido manualmente para o cadastro.
    fun selecionarPonto(local: PontoMapa) {
        ponto = local
        executar("selecionar", "${local.latitude},${local.longitude}")
    }

    // Remove uma seleção antiga quando o endereço de referência muda.
    fun limparPonto() {
        ponto = null
        executar("limparSelecao", "")
    }

    // Mostra a área usada no filtro por distância sem enviar a localização ao Firestore.
    fun mostrarReferencia(local: PontoMapa, raioKm: Double) {
        referencia = local to raioKm
        executar("referencia", "${local.latitude},${local.longitude},$raioKm")
    }

    // Codifica os dados como JSON e mantém uma lista dos IDs que a ponte pode abrir.
    fun mostrarEventos(lista: List<Evento>) {
        eventos = lista
        val dados = JSONArray()
        lista.forEach { evento ->
            if (Geografia.coordenadasValidas(evento.latitude, evento.longitude)) dados.put(JSONObject()
                .put("id", evento.id).put("nome", evento.nome).put("lat", evento.latitude).put("lon", evento.longitude)
                .put("info", FormatoEvento.data(evento.inicioMillis)))
        }
        executar("eventos", dados.toString())
    }

    // Encaminha apenas os comandos internos definidos pelo arquivo local do mapa.
    private fun executar(funcao: String, argumentos: String) {
        if (pronto && !fechado) web.evaluateJavascript("window.perto.$funcao($argumentos);", null)
    }

    // Interrompe requisições e remove a ponte ao destruir a tela.
    fun fechar() {
        fechado = true
        web.stopLoading()
        web.removeJavascriptInterface("PertoAndroid")
        web.destroy()
    }

    private inner class Ponte {
        // Valida as coordenadas antes de entregá-las à interface Android.
        @JavascriptInterface
        fun selecionar(latitude: Double, longitude: Double) {
            if (Geografia.coordenadasValidas(latitude, longitude)) web.post {
                if (!fechado) this@MapaWeb.selecionar(PontoMapa(latitude, longitude))
            }
        }

        // Aceita abrir somente eventos que já foram carregados nesta tela.
        @JavascriptInterface
        fun abrirEvento(id: String) {
            web.post { if (!fechado && eventos.any { it.id == id }) abrir(id) }
        }
    }
}
