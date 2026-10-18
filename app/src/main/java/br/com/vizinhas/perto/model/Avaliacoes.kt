package br.com.vizinhas.perto.model

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.util.concurrent.TimeUnit

data class GravacaoAvaliacao(val uid: String, val eventoId: String, val tarefa: Task<Void>)
data class PaginaAvaliacoes(val avaliacoes: List<Avaliacao>, val cursor: DocumentSnapshot?, val temMais: Boolean)

object Avaliacoes {
    private val banco get() = FirebaseFirestore.getInstance()
    var gravacaoPendente: GravacaoAvaliacao? = null

    // Busca a avaliação da própria conta para permitir atualizá-la sem criar duplicatas.
    fun minha(eventoId: String): Task<Avaliacao?> {
        val uid = Eventos.usuarioId
        if (uid.isBlank() || eventoId.isBlank() || '/' in eventoId) return Tasks.forException(IllegalArgumentException())
        return Tasks.withTimeout(banco.collection("eventos").document(eventoId)
            .collection("avaliacoes").document(uid).get(Source.SERVER), 30, TimeUnit.SECONDS)
            .continueWith { tarefa -> ler(tarefa.getResult(Exception::class.java)) }
    }

    // Consulta páginas de avaliações sem expor dados privados dos perfis participantes.
    fun listar(eventoId: String, depois: DocumentSnapshot? = null): Task<PaginaAvaliacoes> {
        if (Eventos.usuarioId.isBlank() || eventoId.isBlank() || '/' in eventoId) return Tasks.forException(IllegalArgumentException())
        var consulta = banco.collection("eventos").document(eventoId).collection("avaliacoes").limit(20)
        if (depois != null) consulta = consulta.startAfter(depois)
        return Tasks.withTimeout(consulta.get(Source.SERVER), 30, TimeUnit.SECONDS).continueWith { tarefa ->
            val documentos = tarefa.getResult(Exception::class.java).documents
            PaginaAvaliacoes(documentos.mapNotNull { ler(it) }, documentos.lastOrNull(), documentos.size == 20)
        }
    }

    // Reconfere perfil e evento no servidor e sobrescreve somente a avaliação da própria conta.
    fun salvar(eventoId: String, avaliacao: Avaliacao): GravacaoAvaliacao {
        val uid = Eventos.usuarioId
        gravacaoPendente?.takeIf { !it.tarefa.isComplete }?.let {
            return if (it.uid == uid && it.eventoId == eventoId) it
                else GravacaoAvaliacao(uid, eventoId, Tasks.forException(IllegalStateException()))
        }
        val tarefa = if (uid.isBlank() || eventoId.isBlank() || '/' in eventoId || !ValidacaoAvaliacao.valida(avaliacao)) {
            Tasks.forException<Void>(IllegalArgumentException())
        } else banco.runTransaction<Void> { transacao ->
            val perfil = transacao.get(banco.collection("usuarios").document(uid)).toObject(Usuario::class.java)
            val evento = transacao.get(banco.collection("eventos").document(eventoId)).toObject(Evento::class.java)
            check(perfil != null && evento != null && ValidacaoAvaliacao.podeAvaliar(evento, perfil, uid))
            transacao.set(banco.collection("eventos").document(eventoId).collection("avaliacoes").document(uid),
                mapOf("nota" to avaliacao.nota, "comentario" to avaliacao.comentario,
                    "atualizadoEm" to FieldValue.serverTimestamp()))
            null
        }
        return GravacaoAvaliacao(uid, eventoId, tarefa).also { gravacaoPendente = it }
    }

    // Converte apenas os campos públicos esperados de um documento existente.
    private fun ler(documento: DocumentSnapshot): Avaliacao? = if (!documento.exists()) null
        else Avaliacao(documento.getLong("nota") ?: 0, documento.getString("comentario").orEmpty())
}
