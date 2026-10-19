package br.com.vizinhas.perto.ui.principal

import br.com.vizinhas.perto.ui.notificacoes.LembretesLocais
import android.os.Bundle
import android.content.Intent
import android.view.View
import androidx.appcompat.app.AlertDialog
import br.com.vizinhas.perto.model.Conta
import br.com.vizinhas.perto.ui.mensagemErroConta
import br.com.vizinhas.perto.ui.inicio.InicioActivity
import br.com.vizinhas.perto.ui.cadastro.CadastroActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import br.com.vizinhas.perto.R
import br.com.vizinhas.perto.databinding.ActivityPrincipalBinding
import br.com.vizinhas.perto.ui.principal.fragmentos.FavoritosFragment
import br.com.vizinhas.perto.ui.principal.fragmentos.InicioFragment
import br.com.vizinhas.perto.ui.principal.fragmentos.MapaFragment
import br.com.vizinhas.perto.ui.principal.fragmentos.PerfilFragment
import br.com.vizinhas.perto.ui.principal.fragmentos.RelatoriosFragment

class PrincipalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrincipalBinding
    private var dialogoErro: AlertDialog? = null
    private var menuOrganizador: Boolean? = null
    private var abaAtual = R.id.menu_inicio

    // Configura a navegação e reserva a área segura da câmera e das barras do sistema para as abas.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrincipalBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val areaSegura = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            binding.containerPrincipal.setPadding(areaSegura.left, areaSegura.top, areaSegura.right, 0)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        abaAtual = savedInstanceState?.getInt("aba_atual", R.id.menu_inicio) ?: R.id.menu_inicio
        configurarNavegacao()
    }

    // Revalida o perfil ao abrir ou retomar a área autenticada.
    override fun onStart() {
        super.onStart()
        verificarPerfil()
    }

    // Evita manter um diálogo antigo quando a consulta for retomada.
    override fun onStop() {
        dialogoErro?.dismiss()
        dialogoErro = null
        super.onStop()
    }

    // Só libera o conteúdo após consultar o perfil autenticado no servidor.
    private fun verificarPerfil() {
        binding.progressAuth.visibility = View.VISIBLE
        binding.containerPrincipal.visibility = View.INVISIBLE
        binding.bottomNavigation.visibility = View.INVISIBLE
        if (!Conta.autenticada) {
            abrirConta(InicioActivity::class.java)
            return
        }
        Conta.carregarPerfil().addOnCompleteListener(this) { tarefa ->
            if (isFinishing || isDestroyed || supportFragmentManager.isStateSaved) return@addOnCompleteListener
            binding.progressAuth.visibility = View.GONE
            if (tarefa.isSuccessful) {
                if (tarefa.result == null) abrirConta(CadastroActivity::class.java)
                else {
                    configurarMenu(tarefa.result?.tipo == "organizador")
                    if (tarefa.result?.tipo == "usuario") LembretesLocais.sincronizar(applicationContext)
                    binding.containerPrincipal.visibility = View.VISIBLE
                    binding.bottomNavigation.visibility = View.VISIBLE
                }
            } else {
                dialogoErro = AlertDialog.Builder(this)
                    .setMessage(mensagemErroConta(tarefa.exception))
                    .setPositiveButton(R.string.retry) { _, _ -> verificarPerfil() }
                    .setNegativeButton(R.string.logout) { _, _ ->
                        Conta.sair()
                        abrirConta(InicioActivity::class.java)
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    // Substitui a pilha ao exigir login ou conclusão do cadastro.
    private fun abrirConta(destino: Class<*>) {
        startActivity(Intent(this, destino)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    // Instala a navegação apenas depois de confirmar o tipo da conta no servidor.
    private fun configurarNavegacao() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (menuOrganizador == null) false else abrirAba(item.itemId)
        }
    }

    // Aplica os três menus do organizador ou os menus do usuário e restaura uma aba válida.
    private fun configurarMenu(organizador: Boolean) {
        if (menuOrganizador == organizador) return
        menuOrganizador = organizador
        binding.bottomNavigation.setOnItemSelectedListener(null)
        binding.bottomNavigation.menu.clear()
        binding.bottomNavigation.inflateMenu(if (organizador) R.menu.menu_organizador else R.menu.menu_principal)
        if (binding.bottomNavigation.menu.findItem(abaAtual) == null) abaAtual = R.id.menu_inicio
        binding.bottomNavigation.selectedItemId = abaAtual
        configurarNavegacao()
        abrirAba(abaAtual)
    }

    // Abre somente abas disponíveis para a conta e preserva o fragmento restaurado da mesma tela.
    private fun abrirAba(id: Int): Boolean {
        val fragmento = when (id) {
            R.id.menu_inicio -> InicioFragment()
            R.id.menu_relatorios -> if (menuOrganizador == true) RelatoriosFragment() else return false
            R.id.menu_mapa -> if (menuOrganizador == false) MapaFragment() else return false
            R.id.menu_favoritos -> if (menuOrganizador == false) FavoritosFragment() else return false
            R.id.menu_perfil -> PerfilFragment()
            else -> return false
        }
        abaAtual = id
        val atual = supportFragmentManager.findFragmentById(R.id.container_principal)
        if (atual?.javaClass != fragmento.javaClass) trocarFragmento(fragmento)
        return true
    }

    // Mantém a aba selecionada ao girar a tela ou recriar a Activity.
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("aba_atual", abaAtual)
        super.onSaveInstanceState(outState)
    }

    // Mostra o fragmento correspondente à aba escolhida.
    private fun trocarFragmento(fragmento: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container_principal, fragmento)
            .commit()
    }
}
