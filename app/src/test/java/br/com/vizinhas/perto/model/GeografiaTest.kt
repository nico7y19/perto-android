package br.com.vizinhas.perto.model

import org.junit.Assert.*
import org.junit.Test

class GeografiaTest {
    // Rejeita coordenadas incompletas, infinitas e fora dos limites antes da persistência.
    @Test
    fun validaParesDeCoordenadas() {
        assertTrue(Geografia.coordenadasValidas(-22.97, -49.87))
        assertTrue(Geografia.coordenadasValidas(-90.0, 180.0))
        assertFalse(Geografia.coordenadasValidas(null, -49.87))
        assertFalse(Geografia.coordenadasValidas(-22.97, null))
        assertFalse(Geografia.coordenadasValidas(Double.NaN, 0.0))
        assertFalse(Geografia.coordenadasValidas(0.0, Double.POSITIVE_INFINITY))
        assertFalse(Geografia.coordenadasValidas(90.01, 0.0))
        assertFalse(Geografia.coordenadasValidas(0.0, -180.01))
    }

    // Verifica a escala da distância e a passagem pelo meridiano de 180 graus.
    @Test
    fun calculaDistanciasEmQuilometros() {
        assertEquals(0.0, Geografia.distanciaKm(PontoMapa(0.0, 0.0), PontoMapa(0.0, 0.0)), 0.00001)
        assertEquals(111.195, Geografia.distanciaKm(PontoMapa(0.0, 0.0), PontoMapa(0.0, 1.0)), 0.01)
        assertEquals(22.239, Geografia.distanciaKm(PontoMapa(0.0, 179.9), PontoMapa(0.0, -179.9)), 0.01)
    }

    // Preserva eventos antigos sem ponto e rejeita dados geográficos parcialmente preenchidos.
    @Test
    fun validaEventosComESemLocalizacao() {
        val evento = Evento("Evento na praça", "Uma atividade comunitária.", "comunidade", "Praça Central", 2000, 0, "comunidade", "dono")
        assertTrue(ValidacaoEvento.eventoValido(evento, 1000))
        assertTrue(ValidacaoEvento.eventoValido(evento.copy(latitude = -22.97, longitude = -49.87), 1000))
        assertFalse(ValidacaoEvento.eventoValido(evento.copy(latitude = -22.97), 1000))
        assertFalse(ValidacaoEvento.eventoValido(evento.copy(latitude = -91.0, longitude = 0.0), 1000))
    }
}
