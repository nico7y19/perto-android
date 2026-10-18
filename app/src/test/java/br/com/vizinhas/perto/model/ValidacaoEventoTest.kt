package br.com.vizinhas.perto.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidacaoEventoTest {
    private val agora = 1_800_000_000_000L
    private val evento = Evento("Feira na praça", "Uma feira aberta para toda a comunidade.",
        "comunidade", "Praça Central, Ourinhos", agora + 60_000, 0, "comunidade", "organizador")

    // Mantém centavos exatos e aceita os separadores decimal brasileiro e numérico.
    @Test
    fun convertePrecoSemArredondamento() {
        assertEquals(0L, ValidacaoEvento.valorEmCentavos("0"))
        assertEquals(2590L, ValidacaoEvento.valorEmCentavos("25,90"))
        assertEquals(2590L, ValidacaoEvento.valorEmCentavos("25.90"))
        assertEquals(100_000_000L, ValidacaoEvento.valorEmCentavos("1000000,00"))
        listOf("", "-1", "1,999", "1.000,00", "1000000,01", "NaN", "1e3").forEach {
            assertNull(ValidacaoEvento.valorEmCentavos(it))
        }
    }

    // Impede eventos sem informações obrigatórias ou com horário passado.
    @Test
    fun validaCamposEDataFutura() {
        assertTrue(ValidacaoEvento.eventoValido(evento, agora))
        assertFalse(ValidacaoEvento.eventoValido(evento.copy(nome = "ab"), agora))
        assertFalse(ValidacaoEvento.eventoValido(evento.copy(nome = "x".repeat(121)), agora))
        assertFalse(ValidacaoEvento.eventoValido(evento.copy(descricao = "curta"), agora))
        assertFalse(ValidacaoEvento.eventoValido(evento.copy(endereco = ""), agora))
        assertFalse(ValidacaoEvento.eventoValido(evento.copy(inicioMillis = agora), agora))
        assertFalse(ValidacaoEvento.eventoValido(evento.copy(organizadorId = ""), agora))
        assertFalse(ValidacaoEvento.eventoValido(evento.copy(realizado = true), agora))
    }

    // Aceita apenas as cinco capas conhecidas e valores não negativos.
    @Test
    fun restringeCapasCategoriasEValor() {
        ValidacaoEvento.capas.forEach {
            assertTrue(ValidacaoEvento.eventoValido(evento.copy(capa = it), agora))
        }
        assertFalse(ValidacaoEvento.eventoValido(evento.copy(capa = "foto_externa"), agora))
        assertFalse(ValidacaoEvento.eventoValido(evento.copy(categoria = "desconhecida"), agora))
        assertFalse(ValidacaoEvento.eventoValido(evento.copy(valorCentavos = -1), agora))
        assertFalse(ValidacaoEvento.eventoValido(evento.copy(valorCentavos = 100_000_001L), agora))
    }
}
