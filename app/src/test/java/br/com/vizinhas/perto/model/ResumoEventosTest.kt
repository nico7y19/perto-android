package br.com.vizinhas.perto.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumoEventosTest {
    // Uma conta sem eventos deve receber todos os totais zerados.
    @Test
    fun resumeListaVazia() {
        assertEquals(ResumoEventos(0, 0, 0, 0, 0), ResumoEventos.calcular(emptyList(), 100))
    }

    // Classifica o instante exato de início como iniciado e separa situações definitivas.
    @Test
    fun contaCadaSituacaoSemSobreposicao() {
        val eventos = listOf(Evento(inicioMillis = 101), Evento(inicioMillis = 100),
            Evento(inicioMillis = 99), Evento(inicioMillis = 99, realizado = true),
            Evento(inicioMillis = 101, cancelado = true))
        val resumo = ResumoEventos.calcular(eventos, 100)
        assertEquals(ResumoEventos(5, 1, 2, 1, 1), resumo)
        assertEquals(resumo.total, resumo.agendados + resumo.iniciados + resumo.realizados + resumo.cancelados)
    }

    // Mesmo dados inconsistentes não devem contar o mesmo evento em mais de uma situação.
    @Test
    fun priorizaSituacoesDefinitivas() {
        val eventos = listOf(Evento(inicioMillis = 200, realizado = true),
            Evento(inicioMillis = 50, realizado = true, cancelado = true))
        assertEquals(ResumoEventos(2, 0, 0, 1, 1), ResumoEventos.calcular(eventos, 100))
    }
}
