package br.com.vizinhas.perto.model

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.util.concurrent.TimeUnit

object Conta {
    // As tarefas sobrevivem à recriação das telas sem guardar referências à interface.
    var cadastroPendente: Task<Void>? = null
    var loginPendente: Task<Usuario?>? = null
    var recuperacaoPendente: Task<Void>? = null
    var edicaoPendente: Task<Usuario>? = null
    var uidEdicao: String? = null
    private val auth get() = FirebaseAuth.getInstance()
    private val banco get() = FirebaseFirestore.getInstance()
    val autenticada get() = auth.currentUser != null
    val emailAtual get() = auth.currentUser?.email.orEmpty()

    // Cria a credencial; uma sessão de cadastro incompleto pode salvar o perfil novamente.
    fun cadastrar(usuario: Usuario, senha: String): Task<Void> {
        val atual = auth.currentUser
        if (atual != null) {
            if (atual.email != usuario.email) return Tasks.forException(IllegalStateException())
            return salvarPerfil(usuario)
        }
        return auth.createUserWithEmailAndPassword(usuario.email, senha)
            .onSuccessTask { salvarPerfil(usuario) }
    }

    // Salva somente os dados do perfil da própria conta, nunca a senha.
    private fun salvarPerfil(usuario: Usuario): Task<Void> {
        val atual = auth.currentUser ?: return Tasks.forException(IllegalStateException())
        val perfil = usuario.copy(email = atual.email.orEmpty())
        return Tasks.withTimeout(
            banco.collection("usuarios").document(atual.uid).set(perfil), 30, TimeUnit.SECONDS
        )
    }

    // Autentica a conta e consulta seu perfil para identificar cadastros incompletos.
    fun entrar(email: String, senha: String): Task<Usuario?> =
        auth.signInWithEmailAndPassword(email, senha).onSuccessTask { carregarPerfil() }

    // Consulta o servidor para não confundir ausência de cache com perfil inexistente.
    fun carregarPerfil(): Task<Usuario?> {
        val uid = auth.currentUser?.uid ?: return Tasks.forException(IllegalStateException())
        return Tasks.withTimeout(
            banco.collection("usuarios").document(uid).get(Source.SERVER), 30, TimeUnit.SECONDS
        )
            .continueWith { tarefa -> tarefa.getResult(Exception::class.java).toObject(Usuario::class.java) }
    }

    // Solicita o envio das instruções de redefinição pelo Firebase Authentication.
    fun recuperarSenha(email: String): Task<Void> = auth.sendPasswordResetEmail(email)

    // Atualiza apenas campos editáveis, preservando e-mail e tipo lidos na transação.
    fun editarPerfil(nome: String, endereco: String, cnpj: String, telefone: String): Task<Usuario> {
        val uid = auth.currentUser?.uid ?: return Tasks.forException(IllegalStateException())
        edicaoPendente?.takeIf { uidEdicao == uid && !it.isComplete }?.let { return it }
        val documento = banco.collection("usuarios").document(uid)
        uidEdicao = uid
        return banco.runTransaction { transacao ->
            val atual = transacao.get(documento).toObject(Usuario::class.java)
                ?: throw IllegalStateException()
            val perfil = atual.copy(nome = nome.trim(), endereco = endereco.trim(),
                cnpj = if (atual.tipo == "organizador") cnpj.trim() else "",
                telefone = if (atual.tipo == "organizador") telefone.trim() else "")
            require(ValidacaoConta.perfilValido(perfil))
            transacao.update(documento, mapOf("nome" to perfil.nome, "endereco" to perfil.endereco,
                "cnpj" to perfil.cnpj, "telefone" to perfil.telefone))
            perfil
        }.also { edicaoPendente = it }
    }

    // Recupera somente a edição pertencente à sessão atual.
    fun edicaoAtual(): Task<Usuario>? = edicaoPendente.takeIf { uidEdicao == auth.currentUser?.uid }

    // Encerra a sessão local da conta.
    fun sair() = auth.signOut()
}
