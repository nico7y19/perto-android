package br.com.vizinhas.perto.model

import java.text.Normalizer
import java.util.Locale

enum class TipoPreco { TODOS, GRATUITOS, PAGOS, ATE_VALOR }

data class FiltroEvento(
    val nome: String = "",
    val categoria: String? = null,
    val tipoPreco: TipoPreco = TipoPreco.TODOS,
    val valorMaximo: Long? = null,
    val categorias: Set<String> = emptySet()
) {
    // Confere as categorias escolhidas e os limites de preço antes de iniciar uma busca.
    fun valido(): Boolean =
        (categoria == null || categoria in ValidacaoEvento.categorias) &&
            categorias.all { it in ValidacaoEvento.categorias } &&
            (tipoPreco != TipoPreco.ATE_VALOR || (valorMaximo != null && valorMaximo in 0..100_000_000L))

    // Combina nome e preço com qualquer categoria selecionada, ignorando acentos no nome.
    fun aceita(evento: Evento, agora: Long): Boolean {
        if (!valido() || evento.cancelado || evento.realizado || evento.inicioMillis < agora) return false
        if (categoria != null && evento.categoria != categoria) return false
        if (categorias.isNotEmpty() && evento.categoria !in categorias) return false
        if (!normalizar(evento.nome).contains(normalizar(nome))) return false
        return when (tipoPreco) {
            TipoPreco.TODOS -> true
            TipoPreco.GRATUITOS -> evento.valorCentavos == 0L
            TipoPreco.PAGOS -> evento.valorCentavos > 0L
            TipoPreco.ATE_VALOR -> evento.valorCentavos <= (valorMaximo ?: return false)
        }
    }

    // Remove acentos e espaços repetidos para comparar os nomes digitados.
    private fun normalizar(texto: String): String = Normalizer.normalize(texto, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
}
