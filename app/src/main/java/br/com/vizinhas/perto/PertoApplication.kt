package br.com.vizinhas.perto

import android.app.Application
import br.com.vizinhas.perto.ui.notificacoes.LembretesLocais
import com.google.firebase.auth.FirebaseAuth

class PertoApplication : Application() {
    // Prepara os avisos e remove dados locais ao encerrar ou trocar a conta.
    override fun onCreate() {
        super.onCreate()
        LembretesLocais.criarCanal(this)
        val auth = FirebaseAuth.getInstance()
        LembretesLocais.observarConta(this, auth.currentUser?.uid.orEmpty())
        auth.addAuthStateListener { LembretesLocais.observarConta(this, it.currentUser?.uid.orEmpty()) }
    }
}
