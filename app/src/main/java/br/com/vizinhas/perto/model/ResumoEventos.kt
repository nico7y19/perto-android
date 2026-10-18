package br.com.vizinhas.perto.model

data class ResumoEventos(
    val total: Int,
    val agendados: Int,
    val iniciados: Int,
    val realizados: Int,
    val cancelados: Int
) {
    companion object {
        // Conta cada evento em uma única situação, dando prioridade ao cancelamento e à realização.
        fun calcular(eventos: Collection<Evento>, agora: Long): ResumoEventos {
            val cancelados = eventos.count { it.cancelado }
            val realizados = eventos.count { !it.cancelado && it.realizado }
            val agendados = eventos.count { !it.cancelado && !it.realizado && it.inicioMillis > agora }
            val iniciados = eventos.count { !it.cancelado && !it.realizado && it.inicioMillis <= agora }
            return ResumoEventos(eventos.size, agendados, iniciados, realizados, cancelados)
        }
    }
}
