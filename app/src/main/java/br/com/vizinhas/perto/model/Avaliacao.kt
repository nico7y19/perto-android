package br.com.vizinhas.perto.model

data class Avaliacao(val nota: Long = 0, val comentario: String = "")

object ValidacaoAvaliacao {
    // Aceita notas inteiras de uma a cinco estrelas e comentários de até mil caracteres.
    fun valida(avaliacao: Avaliacao): Boolean =
        avaliacao.nota in 1L..5L && avaliacao.comentario.length <= 1000

    // Permite avaliar antes ou depois da realização, apenas por usuários comuns em eventos não cancelados.
    fun podeAvaliar(evento: Evento, usuario: Usuario, uid: String): Boolean =
        uid.isNotBlank() && usuario.tipo == "usuario" && evento.organizadorId != uid &&
            !evento.cancelado
}
