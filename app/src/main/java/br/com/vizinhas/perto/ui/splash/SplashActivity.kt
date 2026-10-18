package br.com.vizinhas.perto.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import br.com.vizinhas.perto.model.Conta
import br.com.vizinhas.perto.ui.inicio.InicioActivity
import br.com.vizinhas.perto.ui.principal.PrincipalActivity

class SplashActivity : AppCompatActivity() {
    // Encaminha a sessão existente sem manter a abertura na pilha de navegação.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val destino = if (Conta.autenticada) PrincipalActivity::class.java else InicioActivity::class.java
        startActivity(Intent(this, destino))
        finish()
    }
}
