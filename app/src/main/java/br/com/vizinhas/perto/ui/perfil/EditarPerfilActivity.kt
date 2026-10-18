package br.com.vizinhas.perto.ui.perfil

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import br.com.vizinhas.perto.R
import br.com.vizinhas.perto.databinding.ActivityEditarPerfilBinding
import br.com.vizinhas.perto.model.Conta
import br.com.vizinhas.perto.model.Usuario
import br.com.vizinhas.perto.model.ValidacaoConta
import br.com.vizinhas.perto.ui.mensagemErroConta
import com.google.android.gms.tasks.Task

class EditarPerfilActivity : AppCompatActivity() {
    private lateinit var tela: ActivityEditarPerfilBinding
    private var perfil: Usuario? = null
    private var restaurado = false

    // Configura o formulário e reconecta uma gravação iniciada antes da rotação.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tela = ActivityEditarPerfilBinding.inflate(layoutInflater)
        setContentView(tela.root)
        ViewCompat.setOnApplyWindowInsetsListener(tela.root) { view, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }
        restaurado = savedInstanceState?.getBoolean("preenchido") == true
        tela.voltar.setOnClickListener { finish() }
        tela.tentar.setOnClickListener { carregar() }
        tela.salvar.setOnClickListener { salvar() }
    }

    // Reconecta consultas e gravações ao voltar de segundo plano.
    override fun onStart() {
        super.onStart()
        carregar()
    }

    // Consulta o perfil sem substituir os campos restaurados pelo Android.
    private fun carregar() {
        ocupada(true)
        tela.tentar.visibility = View.GONE
        tela.status.setText(R.string.loading)
        Conta.carregarPerfil().addOnCompleteListener(this) { tarefa ->
            if (!tarefa.isSuccessful || tarefa.result == null) {
                tela.status.setText(mensagemErroConta(tarefa.exception))
                tela.tentar.visibility = View.VISIBLE
                return@addOnCompleteListener
            }
            val usuario = tarefa.result ?: return@addOnCompleteListener
            perfil = usuario
            if (!restaurado) {
                tela.nome.setText(usuario.nome)
                tela.endereco.setText(usuario.endereco)
                tela.cnpj.setText(usuario.cnpj)
                tela.telefone.setText(usuario.telefone)
                restaurado = true
            }
            val visibilidade = if (usuario.tipo == "organizador") View.VISIBLE else View.GONE
            listOf(tela.cnpj, tela.telefone, tela.labelCnpj, tela.labelTelefone).forEach { it.visibility = visibilidade }
            val pendente = Conta.edicaoAtual()
            if (pendente != null) acompanhar(pendente)
            else {
                ocupada(false)
                tela.status.setText(R.string.profile_edit_info)
            }
        }
    }

    // Valida os dados do formulário antes de solicitar a atualização do perfil.
    private fun salvar() {
        val atual = perfil ?: return
        val editado = atual.copy(nome = tela.nome.text.toString().trim(),
            endereco = tela.endereco.text.toString().trim(),
            cnpj = if (atual.tipo == "organizador") tela.cnpj.text.toString().trim() else "",
            telefone = if (atual.tipo == "organizador") tela.telefone.text.toString().trim() else "")
        if (!ValidacaoConta.perfilValido(editado)) {
            tela.status.setText(R.string.profile_invalid)
            return
        }
        acompanhar(Conta.editarPerfil(editado.nome, editado.endereco, editado.cnpj, editado.telefone))
    }

    // Aguarda a confirmação do servidor e mantém os dados em caso de falha.
    private fun acompanhar(tarefa: Task<Usuario>) {
        ocupada(true)
        tela.status.setText(R.string.loading)
        tarefa.addOnCompleteListener(this) { resultado ->
            if (Conta.edicaoPendente === tarefa) Conta.edicaoPendente = null
            ocupada(false)
            if (resultado.isSuccessful) {
                Toast.makeText(this, R.string.profile_saved, Toast.LENGTH_SHORT).show()
                finish()
            } else tela.status.setText(mensagemErroConta(resultado.exception))
        }
    }

    // Evita mudanças concorrentes enquanto o perfil é consultado ou gravado.
    private fun ocupada(valor: Boolean) {
        listOf(tela.nome, tela.endereco, tela.cnpj, tela.telefone, tela.salvar).forEach { it.isEnabled = !valor }
    }

    // Permite restaurar os campos digitados sem sobrescrevê-los com a consulta.
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("preenchido", restaurado)
        super.onSaveInstanceState(outState)
    }
}
