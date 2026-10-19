package br.com.vizinhas.perto.model

import org.junit.Assert.*
import org.junit.Test

class RegraLembreteTest {
    // Agenda uma hora antes e permite aviso imediato quando o evento já está próximo.
    @Test
    fun calculaHorarioDoAviso() {
        assertEquals(4_400_000L, RegraLembrete.horario(8_000_000L, 1_000_000L))
        assertEquals(1_000_000L, RegraLembrete.horario(4_600_000L, 1_000_000L))
        assertEquals(1_000_000L, RegraLembrete.horario(1_000_001L, 1_000_000L))
    }

    // Impede lembretes de eventos iniciados, cancelados ou concluídos.
    @Test
    fun descartaEventosIndisponiveis() {
        assertNull(RegraLembrete.horario(1000L, 1000L))
        assertNull(RegraLembrete.horario(999L, 1000L))
        assertNull(RegraLembrete.horario(8000L, 1000L, cancelado = true))
        assertNull(RegraLembrete.horario(8000L, 1000L, realizado = true))
    }
}
