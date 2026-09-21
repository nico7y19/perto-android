package br.com.vizinhas.perto.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import br.com.vizinhas.perto.databinding.ActivitySplashBinding
import br.com.vizinhas.perto.ui.inicio.InicioActivity
import br.com.vizinhas.perto.ui.principal.PrincipalActivity
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        auth = Firebase.auth


    }

    override fun onStart() {

        super.onStart()

        lifecycleScope.launch {

            val userAtual = auth.currentUser
            delay(3.seconds)


            //se o usuario estiver autenticado, ele vai para a tela principal
            if (userAtual != null) {

                startActivity(Intent(this@SplashActivity, PrincipalActivity::class.java))

            }
            //se o usuario nao estiver autenticado, ele vai para a tela de login
            else{
                startActivity(Intent(this@SplashActivity, InicioActivity::class.java))

            }





        }
    }
}