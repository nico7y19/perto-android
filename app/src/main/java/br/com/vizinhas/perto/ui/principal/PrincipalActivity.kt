package br.com.vizinhas.perto.ui.principal

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import br.com.vizinhas.perto.R
import br.com.vizinhas.perto.databinding.ActivityPrincipalBinding
import br.com.vizinhas.perto.ui.principal.fragmentos.FavoritosFragment
import br.com.vizinhas.perto.ui.principal.fragmentos.InicioFragment
import br.com.vizinhas.perto.ui.principal.fragmentos.MapaFragment
import br.com.vizinhas.perto.ui.principal.fragmentos.PerfilFragment

class PrincipalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrincipalBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrincipalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarNavegacao()
    }

    private fun configurarNavegacao() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_inicio -> {
                    trocarFragmento(InicioFragment())
                    true
                }
                R.id.menu_mapa -> {
                    trocarFragmento(MapaFragment())
                    true
                }
                R.id.menu_favoritos -> {
                    trocarFragmento(FavoritosFragment())
                    true
                }
                R.id.menu_perfil -> {
                    trocarFragmento(PerfilFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun trocarFragmento(fragmento: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container_principal, fragmento)
            .commit()
    }
}