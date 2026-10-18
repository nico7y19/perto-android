package br.com.vizinhas.perto.ui.inicio

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import br.com.vizinhas.perto.R
import br.com.vizinhas.perto.databinding.ActivityInicioBinding
import br.com.vizinhas.perto.model.Conta
import br.com.vizinhas.perto.model.ValidacaoConta
import br.com.vizinhas.perto.ui.mensagemErroConta
import br.com.vizinhas.perto.ui.cadastro.CadastroActivity
import br.com.vizinhas.perto.ui.principal.PrincipalActivity

class InicioActivity : AppCompatActivity() {
    private lateinit var binding: ActivityInicioBinding
    private var carregando = false

    // Prepara o formulário e as ações de autenticação.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInicioBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.etPassword.isSaveEnabled = false
        binding.etEmail.doAfterTextChanged {
            binding.tvEmailErro.visibility = View.GONE
            binding.tvLoginErro.visibility = View.GONE
        }
        binding.etPassword.doAfterTextChanged {
            binding.tvSenhaErro.visibility = View.GONE
            binding.tvLoginErro.visibility = View.GONE
        }
        binding.btnEntrar.setOnClickListener { entrar() }
        binding.btnCriarConta.setOnClickListener {
            startActivity(Intent(this, CadastroActivity::class.java))
        }
        binding.tvForgotPassword.setOnClickListener { recuperarSenha() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            // Evita abandonar uma autenticação que ainda pode abrir uma sessão.
            override fun handleOnBackPressed() {
                if (!carregando) finish()
            }
        })
    }

    // Verifica ambos os campos e só autentica quando as credenciais estão preenchidas corretamente.
    private fun entrar() {
        if (carregando) return
        limparErros()
        val email = binding.etEmail.text.toString().trim()
        val senha = binding.etPassword.text.toString()
        val erros = ValidacaoConta.errosLogin(email, senha)
        if (erros.emailInvalido) {
            binding.tvEmailErro.setText(if (email.isEmpty()) R.string.error_email_required else R.string.error_email)
            binding.tvEmailErro.visibility = View.VISIBLE
        }
        if (erros.senhaAusente) {
            binding.tvSenhaErro.setText(R.string.error_password_required)
            binding.tvSenhaErro.visibility = View.VISIBLE
        }
        if (erros.emailInvalido || erros.senhaAusente) {
            if (erros.emailInvalido) binding.etEmail.requestFocus() else binding.etPassword.requestFocus()
            return
        }
        atualizarCarregamento(true)
        Conta.loginPendente = Conta.entrar(email, senha)
        observarOperacoes()
    }

    // Usa o e-mail informado para solicitar a recuperação sem exigir a senha atual.
    private fun recuperarSenha() {
        if (carregando || !validarEmail()) return
        atualizarCarregamento(true)
        Conta.recuperacaoPendente = Conta.recuperarSenha(binding.etEmail.text.toString().trim())
        observarOperacoes()
    }

    // Reconecta as operações e mantém rejeições do login visíveis junto ao formulário.
    private fun observarOperacoes() {
        atualizarCarregamento(Conta.loginPendente != null || Conta.recuperacaoPendente != null)
        Conta.loginPendente?.addOnCompleteListener(this) { tarefa ->
            if (Conta.loginPendente !== tarefa) return@addOnCompleteListener
            Conta.loginPendente = null
            atualizarCarregamento(false)
            if (tarefa.isSuccessful) {
                val destino = if (tarefa.result == null) CadastroActivity::class.java else PrincipalActivity::class.java
                startActivity(Intent(this, destino).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            } else {
                Conta.sair()
                mostrarErroLogin(tarefa.exception)
            }
        }
        Conta.recuperacaoPendente?.addOnCompleteListener(this) { tarefa ->
            if (Conta.recuperacaoPendente !== tarefa) return@addOnCompleteListener
            Conta.recuperacaoPendente = null
            atualizarCarregamento(false)
            val mensagem = if (tarefa.isSuccessful) R.string.reset_sent else mensagemErroConta(tarefa.exception)
            Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show()
        }
    }

    // Verifica apenas o e-mail ao solicitar recuperação de senha.
    private fun validarEmail(): Boolean {
        limparErros()
        val email = binding.etEmail.text.toString().trim()
        if (ValidacaoConta.emailValido(email)) return true
        binding.tvEmailErro.setText(if (email.isEmpty()) R.string.error_email_required else R.string.error_email)
        binding.tvEmailErro.visibility = View.VISIBLE
        binding.etEmail.requestFocus()
        return false
    }

    // Remove mensagens anteriores ao iniciar uma nova tentativa.
    private fun limparErros() {
        listOf(binding.tvEmailErro, binding.tvSenhaErro, binding.tvLoginErro).forEach { it.visibility = View.GONE }
    }

    // Associa formato inválido ao e-mail e mostra rejeições ambíguas abaixo dos campos.
    private fun mostrarErroLogin(erro: Exception?) {
        val mensagem = mensagemErroConta(erro)
        val campo = if (mensagem == R.string.error_email) binding.tvEmailErro else binding.tvLoginErro
        campo.setText(mensagem)
        campo.visibility = View.VISIBLE
        if (mensagem == R.string.error_email) binding.etEmail.requestFocus()
        else if (mensagem == R.string.error_credentials) binding.etPassword.requestFocus()
    }

    // Impede envios repetidos e informa a operação em andamento.
    private fun atualizarCarregamento(ativo: Boolean) {
        carregando = ativo
        binding.btnEntrar.isEnabled = !ativo
        binding.btnCriarConta.isEnabled = !ativo
        binding.tvForgotPassword.isEnabled = !ativo
        binding.etEmail.isEnabled = !ativo
        binding.etPassword.isEnabled = !ativo
        binding.btnEntrar.setText(if (ativo) R.string.loading else R.string.btn_login)
    }

    // Reconecta o formulário às tarefas ao retornar ao primeiro plano.
    override fun onStart() {
        super.onStart()
        observarOperacoes()
    }
}
