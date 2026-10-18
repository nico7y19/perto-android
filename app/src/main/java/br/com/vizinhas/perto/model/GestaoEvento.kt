package br.com.vizinhas.perto.model

object GestaoEvento {
    // Restringe alterações ao proprietário de um evento futuro que ainda esteja ativo.
    fun podeAlterar(evento: Evento, uid: String, agora: Long): Boolean =
        uid.isNotBlank() && evento.organizadorId == uid && !evento.cancelado && !evento.realizado && evento.inicioMillis > agora

    // Permite ao dono confirmar a realização de um evento iniciado que não foi cancelado.
    fun podeConcluir(evento: Evento, uid: String, agora: Long): Boolean =
        uid.isNotBlank() && evento.organizadorId == uid && !evento.cancelado &&
            !evento.realizado && evento.inicioMillis <= agora
}
