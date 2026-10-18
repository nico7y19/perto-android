package br.com.vizinhas.perto.ui.eventos

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object FormatoEvento {
    // Apresenta o instante no mesmo fuso utilizado no cadastro.
    fun data(inicioMillis: Long): String = Instant.ofEpochMilli(inicioMillis)
        .atZone(ZoneId.of("America/Sao_Paulo"))
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"))

    // Formata o preço em reais sem perder a precisão dos centavos.
    fun preco(valorCentavos: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
        .format(BigDecimal.valueOf(valorCentavos, 2))
}
