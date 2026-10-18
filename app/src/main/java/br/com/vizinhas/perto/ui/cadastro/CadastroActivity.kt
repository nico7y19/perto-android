package br.com.vizinhas.perto.ui.cadastro

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import br.com.vizinhas.perto.R
import br.com.vizinhas.perto.databinding.ActivityCadastroBinding
import br.com.vizinhas.perto.model.Conta
import br.com.vizinhas.perto.model.Usuario
import br.com.vizinhas.perto.model.ValidacaoConta
import br.com.vizinhas.perto.ui.mensagemErroConta
import br.com.vizinhas.perto.ui.inicio.InicioActivity
import br.com.vizinhas.perto.ui.principal.PrincipalActivity

class CadastroActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCadastroBinding
    private var organizador = false
    private var carregando = false

    // Prepara o cadastro ou a conclusão de um perfil que ainda não foi salvo.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCadastroBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.etPassword.isSaveEnabled = false
        binding.etConfirmPassword.isSaveEnabled = false
        selectUserType(savedInstanceState?.getBoolean("organizador") ?: false)
        binding.btnVoltar.setOnClickListener { voltarAoLogin() }
        binding.btnEntrarFooter.setOnClickListener { voltarAoLogin() }
        binding.btnCriarConta.setOnClickListener { cadastrar() }
        binding.btnTipoUsuario.setOnClickListener { selectUserType(false) }
        binding.btnTipoOrganizador.setOnClickListener { selectUserType(true) }
        atualizarSessao()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            // Evita abandonar um envio e encerra a sessão incompleta ao voltar.
            override fun handleOnBackPressed() { voltarAoLogin() }
        })
    }

    // Preserva o tipo de usuário ao recriar a tela.
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("organizador", organizador)
        super.onSaveInstanceState(outState)
    }

    // Exibe os campos adicionais sem perder a seleção em memória.
    private fun selectUserType(isOrganizer: Boolean) {
        organizador = isOrganizer
        binding.btnTipoOrganizador.background = if (organizador) ContextCompat.getDrawable(this, R.drawable.bg_segmented_selected) else null
        binding.btnTipoUsuario.background = if (!organizador) ContextCompat.getDrawable(this, R.drawable.bg_segmented_selected) else null
        binding.btnTipoOrganizador.setTextColor(ContextCompat.getColor(this, if (organizador) R.color.white else R.color.text_gray))
        binding.btnTipoUsuario.setTextColor(ContextCompat.getColor(this, if (!organizador) R.color.white else R.color.text_gray))
        binding.llExtraFields.visibility = if (organizador) View.VISIBLE else View.GONE
    }

    // Valida os dados e mantém a credencial para permitir repetir um salvamento interrompido.
    private fun cadastrar() {
        if (carregando) return
        val usuario = Usuario(
            nome = binding.etName.text.toString().trim(),
            email = binding.etEmail.text.toString().trim(),
            tipo = if (organizador) "organizador" else "usuario",
            endereco = binding.etAddress.text.toString().trim(),
            cnpj = if (organizador) binding.etCnpj.text.toString().trim() else "",
            telefone = if (organizador) binding.etPhone.text.toString().trim() else ""
        )
        if (!validar(usuario)) return
        atualizarCarregamento(true)
        Conta.cadastroPendente = Conta.cadastrar(usuario, binding.etPassword.text.toString())
        observarCadastro()
    }

    // Reconecta o resultado após rotação ou retorno do aplicativo ao primeiro plano.
    private fun observarCadastro() {
        atualizarCarregamento(Conta.cadastroPendente != null)
        atualizarSessao()
        Conta.cadastroPendente?.addOnCompleteListener(this) { tarefa ->
            if (Conta.cadastroPendente !== tarefa) return@addOnCompleteListener
            Conta.cadastroPendente = null
            atualizarCarregamento(false)
            atualizarSessao()
            if (tarefa.isSuccessful) {
                startActivity(Intent(this, PrincipalActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            } else {
                Toast.makeText(this, mensagemErroConta(tarefa.exception), Toast.LENGTH_LONG).show()
            }
        }
    }

    // Restaura a observação de uma operação que continuou em segundo plano.
    override fun onStart() {
        super.onStart()
        observarCadastro()
    }

    // Confere limites do perfil e a confirmação da senha antes de criar a conta.
    private fun validar(usuario: Usuario): Boolean {
        listOf(binding.etName, binding.etEmail, binding.etPassword, binding.etConfirmPassword, binding.etAddress, binding.etCnpj, binding.etPhone).forEach { it.error = null }
        val campos = listOf(
            Triple(binding.etName, usuario.nome.length in 2..120, R.string.error_name),
            Triple(binding.etEmail, ValidacaoConta.emailValido(usuario.email), R.string.error_email),
            Triple(binding.etAddress, usuario.endereco.length <= 300, R.string.error_address),
            Triple(binding.etCnpj, usuario.cnpj.length <= 18, R.string.error_cnpj),
            Triple(binding.etPhone, usuario.telefone.length <= 20, R.string.error_phone)
        )
        for ((campo, valido, mensagem) in campos) {
            if (!valido) { campo.error = getString(mensagem); campo.requestFocus(); return false }
        }
        if (!Conta.autenticada) {
            val senha = binding.etPassword.text.toString()
            if (!ValidacaoConta.senhaValida(senha)) {
                binding.etPassword.error = getString(R.string.error_password_policy)
                return false
            }
            if (senha != binding.etConfirmPassword.text.toString()) {
                binding.etConfirmPassword.error = getString(R.string.error_password_confirmation)
                return false
            }
        }
        return ValidacaoConta.perfilValido(usuario)
    }

    // Informa que a credencial já existe quando falta somente gravar o perfil.
    private fun atualizarSessao() {
        if (Conta.autenticada) {
            binding.etEmail.setText(Conta.emailAtual)
            binding.etEmail.isEnabled = false
            binding.etPassword.visibility = View.GONE
            binding.etConfirmPassword.visibility = View.GONE
            binding.tvLabelPassword.visibility = View.GONE
            binding.tvSubtitle.setText(R.string.complete_profile)
        }
    }

    // Bloqueia edição e novos envios durante o cadastro.
    private fun atualizarCarregamento(ativo: Boolean) {
        carregando = ativo
        listOf(binding.btnCriarConta, binding.btnVoltar, binding.btnEntrarFooter,
            binding.btnTipoUsuario, binding.btnTipoOrganizador, binding.etName,
            binding.etEmail, binding.etPassword, binding.etConfirmPassword,
            binding.etAddress, binding.etCnpj, binding.etPhone).forEach { it.isEnabled = !ativo }
        binding.btnCriarConta.setText(if (ativo) R.string.loading else R.string.signup_title)
    }

    // Abandona a sessão incompleta e retorna ao formulário de entrada.
    private fun voltarAoLogin() {
        if (carregando) return
        Conta.sair()
        startActivity(Intent(this, InicioActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }
}
