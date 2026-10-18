package br.com.vizinhas.perto.ui.mapa

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import br.com.vizinhas.perto.R
import br.com.vizinhas.perto.databinding.ActivityEscolherLocalBinding
import br.com.vizinhas.perto.model.Geografia
import br.com.vizinhas.perto.model.PontoMapa
import kotlinx.coroutines.launch

class EscolherLocalActivity : AppCompatActivity() {
    companion object {
        const val LATITUDE = "latitude"
        const val LONGITUDE = "longitude"
        const val ENDERECO = "endereco"
    }
    private lateinit var binding: ActivityEscolherLocalBinding
    private lateinit var mapa: MapaWeb
    private lateinit var localizacao: LocalizacaoAtual
    private var ponto: PontoMapa? = null
    private val permissao = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissoes ->
        if (permissoes.values.any { it }) localizar() else binding.tvLocalizacao.setText(R.string.map_permission_denied)
    }

    // Prepara a escolha manual e restaura um ponto previamente salvo no formulário.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEscolherLocalBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val margem = binding.root.paddingLeft
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(margem + barras.left, margem + barras.top, margem + barras.right, margem + barras.bottom)
            insets
        }
        mapa = MapaWeb(binding.webMapa, { escolher(it) }, {})
        mapa.iniciar()
        localizacao = LocalizacaoAtual(this) { local ->
            binding.btnLocalizacao.isEnabled = true
            if (local == null) binding.tvLocalizacao.setText(R.string.map_location_unavailable)
            else { mapa.centralizar(local); escolher(local) }
        }
        binding.etEndereco.setText(intent.getStringExtra(ENDERECO))
        val estado = savedInstanceState ?: intent.extras
        val lat = estado?.getDouble(LATITUDE, Double.NaN)
        val lon = estado?.getDouble(LONGITUDE, Double.NaN)
        if (lat != null && lon != null && Geografia.coordenadasValidas(lat, lon)) { val local = PontoMapa(lat, lon); mapa.centralizar(local); escolher(local) }
        binding.btnVoltar.setOnClickListener { finish() }
        binding.btnLocalizacao.setOnClickListener { pedirLocalizacao() }
        binding.btnBuscar.setOnClickListener {
            WindowCompat.getInsetsController(window, binding.root).hide(WindowInsetsCompat.Type.ime())
            binding.btnBuscar.isEnabled = false
            binding.tvLocalizacao.setText(R.string.loading)
            lifecycleScope.launch {
                val local = buscarEndereco(this@EscolherLocalActivity, binding.etEndereco.text.toString())
                binding.btnBuscar.isEnabled = true
                if (local == null) binding.tvLocalizacao.setText(R.string.map_address_not_found)
                else { ponto = null; binding.btnConfirmar.isEnabled = false; mapa.limparPonto(); mapa.centralizar(local); binding.tvLocalizacao.setText(R.string.map_pick_hint) }
            }
        }
        binding.btnConfirmar.setOnClickListener {
            ponto?.let { setResult(RESULT_OK, Intent().putExtra(LATITUDE, it.latitude).putExtra(LONGITUDE, it.longitude)); finish() }
        }
    }

    // Destaca apenas uma escolha explícita, que pode ser ajustada tocando no mapa.
    private fun escolher(local: PontoMapa) {
        ponto = local
        mapa.selecionarPonto(local)
        binding.btnConfirmar.isEnabled = true
        binding.tvLocalizacao.setText(R.string.map_point_selected)
    }

    // Solicita autorização somente após o toque na ação de localização.
    private fun pedirLocalizacao() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) localizar()
        else permissao.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }

    // Inicia uma solicitação de posição sem manter monitoramento em segundo plano.
    private fun localizar() {
        binding.btnLocalizacao.isEnabled = false
        binding.tvLocalizacao.setText(R.string.loading)
        localizacao.buscar()
    }

    // Preserva a escolha manual ao girar o aparelho.
    override fun onSaveInstanceState(outState: Bundle) {
        ponto?.let { outState.putDouble(LATITUDE, it.latitude); outState.putDouble(LONGITUDE, it.longitude) }
        super.onSaveInstanceState(outState)
    }

    // Libera a solicitação de localização ao sair da tela.
    override fun onStop() {
        localizacao.cancelar()
        binding.btnLocalizacao.isEnabled = true
        super.onStop()
    }

    // Destrói a WebView e a ponte junto com a Activity.
    override fun onDestroy() {
        mapa.fechar()
        super.onDestroy()
    }
}
