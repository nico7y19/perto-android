package br.com.vizinhas.perto.ui.principal.fragmentos

import android.Manifest
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import br.com.vizinhas.perto.ui.notificacoes.LembretesLocais
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import br.com.vizinhas.perto.R
import br.com.vizinhas.perto.databinding.FragmentPerfilBinding
import br.com.vizinhas.perto.model.Conta
import br.com.vizinhas.perto.ui.mensagemErroConta
import br.com.vizinhas.perto.ui.cadastro.CadastroActivity
import br.com.vizinhas.perto.ui.inicio.InicioActivity
import br.com.vizinhas.perto.ui.perfil.EditarPerfilActivity

class PerfilFragment : Fragment() {
    private var atualizandoLembretes = false
    private val permissaoNotificacao = registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        context?.let {
            LembretesLocais.definirAtivos(it, concedida)
            if (concedida) LembretesLocais.sincronizar(it)
            atualizarLembretes()
        }
    }
    private var _binding: FragmentPerfilBinding? = null

    // Prepara a leitura do perfil e permite encerrar a sessão.
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val tela = FragmentPerfilBinding.inflate(inflater, container, false)
        _binding = tela
        tela.btnEditar.setOnClickListener {
            startActivity(Intent(requireContext(), EditarPerfilActivity::class.java))
        }
        tela.btnSair.setOnClickListener {
            Conta.sair()
            startActivity(Intent(requireContext(), InicioActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        tela.swLembretes.setOnCheckedChangeListener { _, marcado ->
            if (!atualizandoLembretes) {
                val contexto = requireContext()
                if (marcado && Build.VERSION.SDK_INT >= 33 && contexto.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    permissaoNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
                else {
                    LembretesLocais.definirAtivos(contexto, marcado)
                    if (marcado) LembretesLocais.sincronizar(contexto)
                }
                atualizarLembretes()
            }
        }
        tela.btnConfigurarLembretes.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName))
        }
        tela.btnTentar.setOnClickListener { carregarPerfil() }
        return tela.root
    }

    // Atualiza o perfil ao retornar da edição e ao abrir a aba.
    override fun onResume() {
        super.onResume()
        carregarPerfil()
    }

    // Exibe dados reais e descarta respostas de uma View que já foi destruída.
    private fun carregarPerfil() {
        val tela = _binding ?: return
        tela.tvDados.setText(R.string.loading)
        tela.btnTentar.visibility = View.GONE
        tela.btnEditar.visibility = View.GONE
        Conta.carregarPerfil().addOnCompleteListener { tarefa ->
            if (_binding !== tela) return@addOnCompleteListener
            if (!tarefa.isSuccessful) {
                tela.tvDados.setText(mensagemErroConta(tarefa.exception))
                tela.btnTentar.visibility = View.VISIBLE
            } else {
                val usuario = tarefa.result
                if (usuario == null) {
                    startActivity(Intent(requireContext(), CadastroActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                } else {
                    tela.btnEditar.visibility = View.VISIBLE
                    tela.painelLembretes.visibility = if (usuario.tipo == "usuario") View.VISIBLE else View.GONE
                    atualizarLembretes()
                    val tipo = getString(if (usuario.tipo == "organizador") R.string.type_organizer else R.string.type_user)
                    tela.tvDados.text = listOf(
                        usuario.nome, usuario.email, getString(R.string.profile_type, tipo),
                        usuario.endereco.takeIf { it.isNotBlank() }?.let { getString(R.string.profile_address, it) },
                        usuario.cnpj.takeIf { it.isNotBlank() }?.let { getString(R.string.profile_cnpj, it) },
                        usuario.telefone.takeIf { it.isNotBlank() }?.let { getString(R.string.profile_phone, it) }
                    ).filterNotNull().joinToString("\n\n")
                }
            }
        }
    }

    // Mostra a preferência do aplicativo e informa se o Android bloqueou os avisos.
    private fun atualizarLembretes() {
        val tela = _binding ?: return
        val contexto = context ?: return
        atualizandoLembretes = true
        val ativo = LembretesLocais.ativados(contexto)
        tela.swLembretes.isChecked = ativo
        tela.tvLembretes.setText(when {
            !ativo -> R.string.reminder_disabled
            !LembretesLocais.permitidas(contexto) -> R.string.reminder_blocked
            else -> R.string.reminder_enabled
        })
        atualizandoLembretes = false
    }

    // Libera referências à interface ao sair do fragmento.
    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
