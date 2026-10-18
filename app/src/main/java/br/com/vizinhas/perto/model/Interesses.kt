package br.com.vizinhas.perto.model

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.util.concurrent.TimeUnit

data class AlteracaoInteresse(val uid: String, val eventoId: String, val tarefa: Task<Boolean>)
data class Favorito(val eventoId: String, val evento: Evento?)
data class PaginaFavoritos(val favoritos: List<Favorito>, val ultimoDocumento: DocumentSnapshot?, val temMais: Boolean)

object Interesses {
    var alteracaoPendente: AlteracaoInteresse? = null
    private val banco get() = FirebaseFirestore.getInstance()

    // Consulta a marcação da própria conta sem depender de um valor antigo do cache.
    fun estaInteressado(eventoId: String): Task<Boolean> {
        val uid = Eventos.usuarioId
        if (uid.isBlank() || eventoId.isBlank() || '/' in eventoId) return Tasks.forException(IllegalArgumentException())
        return Tasks.withTimeout(banco.collection("usuarios").document(uid).collection("interesses")
            .document(eventoId).get(Source.SERVER), 30, TimeUnit.SECONDS)
            .continueWith { it.getResult(Exception::class.java).exists() }
    }

    // Registra ou remove o interesse uma única vez, conferindo o evento em uma transação.
    fun alterar(eventoId: String, marcar: Boolean): AlteracaoInteresse {
        val uid = Eventos.usuarioId
        val tarefa = if (uid.isBlank() || eventoId.isBlank() || '/' in eventoId) {
            Tasks.forException<Boolean>(IllegalArgumentException())
        } else banco.runTransaction { transacao ->
            val referencia = banco.collection("usuarios").document(uid).collection("interesses").document(eventoId)
            val existente = transacao.get(referencia)
            if (marcar && !existente.exists()) {
                val evento = transacao.get(banco.collection("eventos").document(eventoId))
                val inicio = evento.getLong("inicioMillis")
                check(inicio != null && inicio > System.currentTimeMillis() && evento.getBoolean("cancelado") != true && evento.getBoolean("realizado") != true)
                transacao.set(referencia, mapOf("eventoId" to eventoId, "criadoEm" to FieldValue.serverTimestamp()))
            } else if (!marcar && existente.exists()) transacao.delete(referencia)
            marcar
        }
        val operacao = AlteracaoInteresse(uid, eventoId, Tasks.withTimeout(tarefa, 30, TimeUnit.SECONDS))
        alteracaoPendente = operacao
        return operacao
    }

    // Consulta favoritos sem exibir cancelados e preserva o cursor original e referências removidas.
    fun listar(depois: DocumentSnapshot? = null): Task<PaginaFavoritos> {
        val uid = Eventos.usuarioId
        if (uid.isBlank()) return Tasks.forException(IllegalStateException())
        var consulta = banco.collection("usuarios").document(uid).collection("interesses")
            .orderBy("criadoEm", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(20)
        if (depois != null) consulta = consulta.startAfter(depois)
        val tarefa = consulta.get(Source.SERVER).onSuccessTask { pagina ->
            val tarefas = pagina.documents.map { documento ->
                Eventos.buscar(documento.id).continueWith { resultado ->
                    Favorito(documento.id, resultado.getResult(Exception::class.java))
                }
            }
            Tasks.whenAllSuccess<Favorito>(tarefas).continueWith { resultado ->
                val favoritos = resultado.getResult(Exception::class.java).filterNot { it.evento?.cancelado == true }
                PaginaFavoritos(favoritos, pagina.documents.lastOrNull(), pagina.size() == 20)
            }
        }
        return Tasks.withTimeout(tarefa, 30, TimeUnit.SECONDS)
    }
}
