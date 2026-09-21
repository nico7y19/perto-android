package br.com.vizinhas.perto.ui.inicio

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import br.com.vizinhas.perto.databinding.ActivityInicioBinding
import br.com.vizinhas.perto.ui.cadastro.CadastroActivity
import br.com.vizinhas.perto.ui.principal.PrincipalActivity

class InicioActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInicioBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInicioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnEntrar.setOnClickListener {
            startActivity(Intent(this, PrincipalActivity::class.java))
            finish()
        }

        binding.btnCriarConta.setOnClickListener {
            startActivity(Intent(this, CadastroActivity::class.java))
        }
    }
}