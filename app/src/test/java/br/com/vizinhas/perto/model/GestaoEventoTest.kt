package br.com.vizinhas.perto.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestaoEventoTest {
    private val agora = 1_800_000_000_000L
    private val evento = Evento("Festival na praça", "Um evento para a comunidade.", "musica",
        "Praça Central", agora + 60_000, 0, "musica", "dono")

    // A edição e o cancelamento exigem uma conta identificada como proprietária.
    @Test
    fun restringeAPropriedadeDoEvento() {
        assertTrue(GestaoEvento.podeAlterar(evento, "dono", agora))
        assertFalse(GestaoEvento.podeAlterar(evento, "outra-conta", agora))
        assertFalse(GestaoEvento.podeAlterar(evento.copy(organizadorId = ""), "", agora))
    }

    // Eventos cancelados ou já iniciados deixam de oferecer novas alterações.
    @Test
    fun restringeOEstadoEHorarioDoEvento() {
        assertFalse(GestaoEvento.podeAlterar(evento.copy(cancelado = true), "dono", agora))
        assertFalse(GestaoEvento.podeAlterar(evento.copy(realizado = true), "dono", agora))
        assertFalse(GestaoEvento.podeAlterar(evento.copy(inicioMillis = agora), "dono", agora))
        assertFalse(GestaoEvento.podeAlterar(evento.copy(inicioMillis = agora - 1), "dono", agora))
    }

    // A descoberta omite eventos cancelados mesmo que os demais critérios correspondam.
    @Test
    fun removeCanceladosDosResultadosDaBusca() {
        val filtro = FiltroEvento(nome = "festival", categoria = "musica", tipoPreco = TipoPreco.GRATUITOS)
        assertTrue(filtro.aceita(evento, agora))
        assertFalse(filtro.aceita(evento.copy(cancelado = true), agora))
        assertFalse(filtro.aceita(evento.copy(realizado = true), agora))
    }

    // A confirmação só fica disponível a partir do início, inclusive no instante exato.
    @Test
    fun permiteConcluirSomenteDepoisDoInicio() {
        assertFalse(GestaoEvento.podeConcluir(evento, "dono", agora))
        assertTrue(GestaoEvento.podeConcluir(evento.copy(inicioMillis = agora), "dono", agora))
        assertTrue(GestaoEvento.podeConcluir(evento.copy(inicioMillis = agora - 1), "dono", agora))
    }

    // Outra conta, evento cancelado ou já concluído não pode confirmar a realização.
    @Test
    fun restringeConclusaoPorProprietarioEEstado() {
        val iniciado = evento.copy(inicioMillis = agora - 1)
        assertFalse(GestaoEvento.podeConcluir(iniciado, "outra-conta", agora))
        assertFalse(GestaoEvento.podeConcluir(iniciado.copy(organizadorId = ""), "", agora))
        assertFalse(GestaoEvento.podeConcluir(iniciado.copy(cancelado = true), "dono", agora))
        assertFalse(GestaoEvento.podeConcluir(iniciado.copy(realizado = true), "dono", agora))
    }
}
