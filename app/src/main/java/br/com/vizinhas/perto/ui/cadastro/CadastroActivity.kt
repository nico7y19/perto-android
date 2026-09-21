package br.com.vizinhas.perto.ui.cadastro

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import br.com.vizinhas.perto.R
import br.com.vizinhas.perto.databinding.ActivityCadastroBinding
import br.com.vizinhas.perto.ui.principal.PrincipalActivity

class CadastroActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCadastroBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCadastroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnVoltar.setOnClickListener {
            finish()
        }

        binding.btnEntrarFooter.setOnClickListener {
            finish() // Retorna para a tela de login
        }

        binding.btnCriarConta.setOnClickListener {
            startActivity(Intent(this, PrincipalActivity::class.java))
            finishAffinity()
        }

        binding.btnTipoUsuario.setOnClickListener {
            selectUserType(isOrganizer = false)
        }

        binding.btnTipoOrganizador.setOnClickListener {
            selectUserType(isOrganizer = true)
        }
    }

    private fun selectUserType(isOrganizer: Boolean) {
        if (isOrganizer) {
            binding.btnTipoOrganizador.background = ContextCompat.getDrawable(this, R.drawable.bg_segmented_selected)
            binding.btnTipoOrganizador.setTextColor(ContextCompat.getColor(this, R.color.white))
            
            binding.btnTipoUsuario.background = null
            binding.btnTipoUsuario.setTextColor(ContextCompat.getColor(this, R.color.text_gray))
            
            binding.llExtraFields.visibility = View.VISIBLE
        } else {
            binding.btnTipoUsuario.background = ContextCompat.getDrawable(this, R.drawable.bg_segmented_selected)
            binding.btnTipoUsuario.setTextColor(ContextCompat.getColor(this, R.color.white))
            
            binding.btnTipoOrganizador.background = null
            binding.btnTipoOrganizador.setTextColor(ContextCompat.getColor(this, R.color.text_gray))
            
            binding.llExtraFields.visibility = View.GONE
        }
    }
}