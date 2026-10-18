package br.com.vizinhas.perto.model

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltroEventoTest {
    private val dia = LocalDate.of(2026, 10, 20)
    private val fuso = ZoneId.of("America/Sao_Paulo")
    private val agora = dia.atStartOfDay(fuso).toInstant().toEpochMilli()
    private val evento = Evento("Festival de MÚSICA na Praça", "Uma noite para toda a comunidade.",
        "musica", "Praça Central", agora + 3_600_000, 2500, "cultura", "organizador")

    // Aceita qualquer categoria selecionada e exige a combinação com nome e preço.
    @Test
    fun combinaMultiplasCategoriasComNomeEPreco() {
        val filtro = FiltroEvento(nome = "musica", categorias = setOf("musica", "cultura"), tipoPreco = TipoPreco.PAGOS)
        assertTrue(filtro.aceita(evento, agora))
        assertTrue(filtro.aceita(evento.copy(categoria = "cultura"), agora))
        assertFalse(filtro.aceita(evento.copy(categoria = "esportes"), agora))
        assertFalse(filtro.aceita(evento.copy(nome = "Feira de artes"), agora))
        assertFalse(filtro.aceita(evento.copy(valorCentavos = 0), agora))
        assertTrue(filtro.copy(tipoPreco = TipoPreco.GRATUITOS).aceita(evento.copy(valorCentavos = 0), agora))
    }

    // Não selecionar categorias significa incluir todas, mas valores desconhecidos são inválidos.
    @Test
    fun validaSelecaoMultiplaDeCategorias() {
        assertTrue(FiltroEvento(categorias = emptySet()).aceita(evento, agora))
        assertFalse(FiltroEvento(categorias = setOf("musica", "desconhecida")).valido())
    }

    // A busca aceita trechos do nome com diferenças de acentos, maiúsculas e espaços.
    @Test
    fun buscaNomeSemAcentos() {
        assertTrue(FiltroEvento(nome = "  musica   na praca ").aceita(evento, agora))
        assertTrue(FiltroEvento(nome = "FESTIVAL").aceita(evento, agora))
        assertFalse(FiltroEvento(nome = "gastronomia").aceita(evento, agora))
    }

    // A categoria do evento independe da capa ilustrativa selecionada no cadastro.
    @Test
    fun filtraCategoriaDoEvento() {
        assertTrue(FiltroEvento(categoria = "musica").aceita(evento, agora))
        assertFalse(FiltroEvento(categoria = "cultura").aceita(evento, agora))
        assertFalse(FiltroEvento(categoria = "inexistente").valido())
    }

    // O preço máximo inclui o limite exato e eventos gratuitos, sem arredondar centavos.
    @Test
    fun combinaOpcoesDePreco() {
        assertFalse(FiltroEvento(tipoPreco = TipoPreco.GRATUITOS).aceita(evento, agora))
        assertTrue(FiltroEvento(tipoPreco = TipoPreco.PAGOS).aceita(evento, agora))
        assertTrue(FiltroEvento(tipoPreco = TipoPreco.ATE_VALOR, valorMaximo = 2500).aceita(evento, agora))
        assertFalse(FiltroEvento(tipoPreco = TipoPreco.ATE_VALOR, valorMaximo = 2499).aceita(evento, agora))
        val gratuito = evento.copy(valorCentavos = 0)
        assertTrue(FiltroEvento(tipoPreco = TipoPreco.GRATUITOS).aceita(gratuito, agora))
        assertFalse(FiltroEvento(tipoPreco = TipoPreco.PAGOS).aceita(gratuito, agora))
        assertTrue(FiltroEvento(tipoPreco = TipoPreco.ATE_VALOR, valorMaximo = 0).aceita(gratuito, agora))
    }

    // A descoberta continua excluindo eventos passados sem um filtro de período.
    @Test
    fun mantemSomenteEventosFuturos() {
        assertTrue(FiltroEvento().aceita(evento, agora))
        assertFalse(FiltroEvento().aceita(evento.copy(inicioMillis = agora - 1), agora))
    }

    // Todos os critérios precisam corresponder ao mesmo evento.
    @Test
    fun combinaFiltrosERejeitaEntradasInvalidas() {
        val filtro = FiltroEvento("musica", "musica", TipoPreco.ATE_VALOR, 2500)
        assertTrue(filtro.aceita(evento, agora))
        assertFalse(filtro.aceita(evento.copy(categoria = "esportes"), agora))
        assertFalse(filtro.copy(valorMaximo = null).valido())
        assertFalse(filtro.copy(valorMaximo = -1).valido())
    }
}
