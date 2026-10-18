package br.com.vizinhas.perto.model

import java.math.BigDecimal

object ValidacaoEvento {
    val capas = listOf("musica", "gastronomia", "esportes", "cultura", "comunidade")
    val categorias = listOf("musica", "gastronomia", "esportes", "cultura", "comunidade")

    // Converte reais em centavos exatos, sem arredondar nem aceitar frações de centavo.
    fun valorEmCentavos(texto: String): Long? {
        if (!Regex("[0-9]{1,7}([,.][0-9]{1,2})?").matches(texto.trim())) return null
        return try {
            BigDecimal(texto.trim().replace(',', '.')).movePointRight(2).longValueExact()
                .takeIf { it in 0..100_000_000L }
        } catch (_: ArithmeticException) { null }
    }

    // Aplica as regras do cadastro antes de enviar o evento ao Firestore.
    fun eventoValido(evento: Evento, agora: Long): Boolean =
        evento.nome.length in 3..120 && evento.descricao.length in 10..3000 &&
            evento.endereco.length in 5..300 && evento.categoria in categorias &&
            evento.capa in capas && !evento.realizado && evento.inicioMillis > agora &&
            evento.valorCentavos in 0..100_000_000L && evento.organizadorId.isNotBlank() &&
            ((evento.latitude == null && evento.longitude == null) || Geografia.coordenadasValidas(evento.latitude, evento.longitude))
}
