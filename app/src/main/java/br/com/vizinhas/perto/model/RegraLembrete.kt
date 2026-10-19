package br.com.vizinhas.perto.model

object RegraLembrete {
    // Define o aviso uma hora antes ou assim que possível quando o evento já estiver próximo.
    fun horario(inicio: Long, agora: Long, cancelado: Boolean = false, realizado: Boolean = false): Long? =
        if (cancelado || realizado || inicio <= agora) null else maxOf(agora, inicio - 3_600_000L)
}
