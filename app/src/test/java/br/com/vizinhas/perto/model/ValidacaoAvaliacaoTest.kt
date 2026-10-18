package br.com.vizinhas.perto.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidacaoAvaliacaoTest {
    // Garante os limites inclusivos de nota e comentário e aceita comentário vazio.
    @Test
    fun validaNotaEComentario() {
        assertTrue(ValidacaoAvaliacao.valida(Avaliacao(1, "")))
        assertTrue(ValidacaoAvaliacao.valida(Avaliacao(5, "a".repeat(1000))))
        assertFalse(ValidacaoAvaliacao.valida(Avaliacao(0)))
        assertFalse(ValidacaoAvaliacao.valida(Avaliacao(6)))
        assertFalse(ValidacaoAvaliacao.valida(Avaliacao(3, "a".repeat(1001))))
    }

    // Aceita eventos futuros, iniciados e realizados, mantendo o bloqueio dos cancelados.
    @Test
    fun permiteAvaliarIndependentementeDaRealizacao() {
        val usuario = Usuario(tipo = "usuario")
        val evento = Evento(organizadorId = "organizador", inicioMillis = 1, realizado = true)
        assertTrue(ValidacaoAvaliacao.podeAvaliar(evento, usuario, "participante"))
        assertTrue(ValidacaoAvaliacao.podeAvaliar(evento.copy(realizado = false), usuario, "participante"))
        assertTrue(ValidacaoAvaliacao.podeAvaliar(evento.copy(realizado = false, inicioMillis = Long.MAX_VALUE), usuario, "participante"))
        assertFalse(ValidacaoAvaliacao.podeAvaliar(evento.copy(realizado = false, cancelado = true), usuario, "participante"))
        assertFalse(ValidacaoAvaliacao.podeAvaliar(evento.copy(cancelado = true), usuario, "participante"))
    }

    // Impede organizadores, proprietários e contas sem identificação de avaliar.
    @Test
    fun restringeAContaAvaliadora() {
        val evento = Evento(organizadorId = "dono", realizado = true)
        assertFalse(ValidacaoAvaliacao.podeAvaliar(evento, Usuario(tipo = "organizador"), "outra-conta"))
        assertFalse(ValidacaoAvaliacao.podeAvaliar(evento, Usuario(tipo = "usuario"), "dono"))
        assertFalse(ValidacaoAvaliacao.podeAvaliar(evento, Usuario(tipo = "usuario"), ""))
    }
}
