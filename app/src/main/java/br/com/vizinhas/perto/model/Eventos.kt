package br.com.vizinhas.perto.model

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.DocumentSnapshot
import java.util.concurrent.TimeUnit

class EventoJaPublicadoException : IllegalStateException()
data class GravacaoEvento(val uid: String, val eventoId: String, val tarefa: Task<Void>)

object Eventos {
    var gravacaoPendente: GravacaoEvento? = null
    var cancelamentoPendente: GravacaoEvento? = null
    var conclusaoPendente: GravacaoEvento? = null
    private val banco get() = FirebaseFirestore.getInstance()
    val usuarioId get() = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    // Valida a conta e grava o mesmo identificador em novas tentativas, sem duplicar eventos.
    fun cadastrar(id: String, evento: Evento): Task<Void> {
        val uid = usuarioId
        if (uid.isEmpty() || evento.organizadorId != uid || evento.cancelado ||
            !ValidacaoEvento.eventoValido(evento, System.currentTimeMillis())) {
            return Tasks.forException(IllegalArgumentException())
        }
        val tarefa = banco.runTransaction<Void> { transacao ->
            val perfil = transacao.get(banco.collection("usuarios").document(uid))
            check(perfil.getString("tipo") == "organizador")
            val referencia = banco.collection("eventos").document(id)
            val existente = transacao.get(referencia)
            if (existente.exists()) {
                // Uma resposta perdida pode ser repetida sem criar outro documento.
                if (existente.toObject(Evento::class.java) != evento) throw EventoJaPublicadoException()
            } else {
                transacao.set(referencia, evento)
            }
            null
        }
        return Tasks.withTimeout(tarefa, 30, TimeUnit.SECONDS)
    }

    // Atualiza os dados após reconferir o proprietário e o estado atual no servidor.
    fun editar(id: String, evento: Evento): Task<Void> {
        val uid = usuarioId
        if (id.isBlank() || '/' in id || evento.organizadorId != uid || evento.cancelado ||
            !ValidacaoEvento.eventoValido(evento, System.currentTimeMillis())) {
            return Tasks.forException(IllegalArgumentException())
        }
        val tarefa = banco.runTransaction<Void> { transacao ->
            val perfil = transacao.get(banco.collection("usuarios").document(uid))
            check(perfil.getString("tipo") == "organizador")
            val referencia = banco.collection("eventos").document(id)
            val atual = transacao.get(referencia).toObject(Evento::class.java)
            check(atual != null && GestaoEvento.podeAlterar(atual, uid, System.currentTimeMillis()))
            transacao.set(referencia, evento.copy(id = ""))
            null
        }
        return Tasks.withTimeout(tarefa, 30, TimeUnit.SECONDS)
    }

    // Cancela sem apagar o documento e aceita a repetição de um cancelamento já confirmado.
    fun cancelar(id: String): Task<Void> {
        val uid = usuarioId
        if (uid.isBlank() || id.isBlank() || '/' in id) return Tasks.forException(IllegalArgumentException())
        val tarefa = banco.runTransaction<Void> { transacao ->
            val perfil = transacao.get(banco.collection("usuarios").document(uid))
            check(perfil.getString("tipo") == "organizador")
            val referencia = banco.collection("eventos").document(id)
            val atual = transacao.get(referencia).toObject(Evento::class.java)
            check(atual != null && atual.organizadorId == uid)
            if (!atual.cancelado) {
                check(GestaoEvento.podeAlterar(atual, uid, System.currentTimeMillis()))
                transacao.update(referencia, "cancelado", true)
            }
            null
        }
        return Tasks.withTimeout(tarefa, 30, TimeUnit.SECONDS)
    }

    // Confirma a realização sem modificar os demais dados e aceita uma repetição já concluída.
    fun concluir(id: String): Task<Void> {
        val uid = usuarioId
        if (uid.isBlank() || id.isBlank() || '/' in id) return Tasks.forException(IllegalArgumentException())
        return banco.runTransaction<Void> { transacao ->
            val perfil = transacao.get(banco.collection("usuarios").document(uid))
            check(perfil.getString("tipo") == "organizador")
            val referencia = banco.collection("eventos").document(id)
            val atual = transacao.get(referencia).toObject(Evento::class.java)
            check(atual != null && atual.organizadorId == uid && !atual.cancelado)
            if (!atual.realizado) {
                check(GestaoEvento.podeConcluir(atual, uid, System.currentTimeMillis()))
                transacao.update(referencia, "realizado", true)
            }
            null
        }
    }

    // Percorre os eventos da conta, incluindo cancelados somente quando solicitado para o relatório.
    fun listarProprios(depois: DocumentSnapshot? = null, incluirCancelados: Boolean = false): Task<PaginaEventos> {
        val uid = usuarioId
        if (uid.isEmpty()) return Tasks.forException(IllegalStateException())
        var consulta = banco.collection("eventos").whereEqualTo("organizadorId", uid).limit(50)
        if (depois != null) consulta = consulta.startAfter(depois)
        return Tasks.withTimeout(
            consulta.get(Source.SERVER), 30, TimeUnit.SECONDS
        ).continueWith { tarefa ->
            val documentos = tarefa.getResult(Exception::class.java).documents
            val eventos = documentos.mapNotNull { documento ->
                documento.toObject(Evento::class.java)?.copy(id = documento.id)
            }.filter { incluirCancelados || !it.cancelado }
            PaginaEventos(eventos, documentos.lastOrNull(), documentos.size == 50)
        }
    }

    // Consulta páginas por data e aplica os demais filtros sem exigir novos índices.
    fun pesquisar(filtro: FiltroEvento, agora: Long, depois: DocumentSnapshot? = null): Task<PaginaEventos> {
        if (usuarioId.isEmpty() || !filtro.valido()) return Tasks.forException(IllegalArgumentException())
        var consulta = banco.collection("eventos").whereGreaterThanOrEqualTo("inicioMillis", agora)
            .orderBy("inicioMillis").limit(50)
        if (depois != null) consulta = consulta.startAfter(depois)
        return Tasks.withTimeout(consulta.get(Source.SERVER), 30, TimeUnit.SECONDS)
            .continueWith { tarefa ->
                val documentos = tarefa.getResult(Exception::class.java).documents
                val eventos = documentos.mapNotNull { documento ->
                    documento.toObject(Evento::class.java)?.copy(id = documento.id)
                }.filter { filtro.aceita(it, agora) }
                PaginaEventos(eventos, documentos.lastOrNull(), documentos.size == 50)
            }
    }

    // Reconsulta o documento escolhido para mostrar seus dados atuais nos detalhes.
    fun buscar(id: String): Task<Evento?> {
        if (usuarioId.isEmpty() || id.isBlank() || '/' in id) {
            return Tasks.forException(IllegalArgumentException())
        }
        return Tasks.withTimeout(
            banco.collection("eventos").document(id).get(Source.SERVER), 30, TimeUnit.SECONDS
        ).continueWith { tarefa ->
            tarefa.getResult(Exception::class.java).toObject(Evento::class.java)?.copy(id = id)
        }
    }

}
