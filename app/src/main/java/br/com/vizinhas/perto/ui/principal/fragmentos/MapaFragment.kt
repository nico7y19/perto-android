package br.com.vizinhas.perto.ui.principal.fragmentos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import kotlinx.coroutines.Job
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import br.com.vizinhas.perto.R
import br.com.vizinhas.perto.databinding.FragmentMapaBinding
import br.com.vizinhas.perto.model.Evento
import br.com.vizinhas.perto.model.Eventos
import br.com.vizinhas.perto.model.FiltroEvento
import br.com.vizinhas.perto.model.Geografia
import br.com.vizinhas.perto.model.PontoMapa
import br.com.vizinhas.perto.ui.eventos.DetalhesEventoActivity
import br.com.vizinhas.perto.ui.eventos.mensagemErroEvento
import br.com.vizinhas.perto.ui.mapa.LocalizacaoAtual
import br.com.vizinhas.perto.ui.mapa.MapaWeb
import br.com.vizinhas.perto.ui.mapa.buscarEndereco
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.launch

class MapaFragment : Fragment() {
    private var _binding: FragmentMapaBinding? = null
    private var mapa: MapaWeb? = null
    private var localizacao: LocalizacaoAtual? = null
    private var buscaEndereco: Job? = null
    private var referencia: PontoMapa? = null
    private val eventos = linkedMapOf<String, Evento>()
    private var cursor: DocumentSnapshot? = null
    private var temMais = false
    private var carregando = false
    private var consulta = 0
    private var agora = 0L
    private val raios = listOf(0.0, 5.0, 10.0, 25.0, 50.0)
    private val permissao = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultado ->
        if (resultado.values.any { it }) localizar() else _binding?.tvLocalizacao?.setText(R.string.map_permission_denied)
    }

    // Restaura a área escolhida sem armazenar a posição do aparelho no Firebase.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState?.containsKey("latitude") == true)
            referencia = PontoMapa(savedInstanceState.getDouble("latitude"), savedInstanceState.getDouble("longitude"))
    }

    // Prepara o mapa, a navegação pelos marcadores e a busca manual de uma área.
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val tela = FragmentMapaBinding.inflate(inflater, container, false)
        _binding = tela
        mapa = MapaWeb(tela.webMapa, { selecionarReferencia(it) }, { id ->
            startActivity(Intent(requireContext(), DetalhesEventoActivity::class.java).putExtra(DetalhesEventoActivity.EXTRA_EVENTO_ID, id))
        }).also { it.iniciar() }
        referencia?.let { mapa?.centralizar(it) }
        localizacao = LocalizacaoAtual(requireContext()) { ponto ->
            if (_binding !== tela) return@LocalizacaoAtual
            tela.btnLocalizacao.isEnabled = true
            if (ponto == null) tela.tvLocalizacao.setText(R.string.map_location_unavailable)
            else { mapa?.centralizar(ponto); selecionarReferencia(ponto); if (tela.spRaio.selectedItemPosition == 0) tela.spRaio.setSelection(2) }
        }
        tela.spRaio.adapter = ArrayAdapter.createFromResource(requireContext(), R.array.map_radius_options, R.layout.spinner_perto)
        tela.spRaio.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            // Recalcula somente os marcadores carregados quando o raio muda.
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { atualizarMarcadores() }
            // Preserva o estado quando o seletor ainda não possui um valor.
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        tela.btnLocalizacao.setOnClickListener { pedirLocalizacao() }
        tela.etEndereco.setOnEditorActionListener { _, acao, tecla ->
            val enter = tecla?.keyCode == KeyEvent.KEYCODE_ENTER
            if (acao == EditorInfo.IME_ACTION_SEARCH || enter) {
                if (tecla == null || tecla.action == KeyEvent.ACTION_DOWN) pesquisarEndereco()
                true
            } else false
        }
        tela.btnAtualizar.setOnClickListener { if (!carregando) carregar() }
        tela.btnMais.setOnClickListener { if (!carregando && temMais) carregarPagina(consulta) }
        return tela.root
    }

    // Atualiza os eventos ao retornar de detalhes ou após editar suas localizações.
    override fun onResume() {
        super.onResume()
        carregar()
    }

    // Pesquisa pelo teclado e cancela a consulta anterior para preservar o resultado mais recente.
    private fun pesquisarEndereco() {
        val tela = _binding ?: return
        WindowCompat.getInsetsController(requireActivity().window, tela.root).hide(WindowInsetsCompat.Type.ime())
        buscaEndereco?.cancel()
        tela.tvLocalizacao.setText(R.string.loading)
        val endereco = tela.etEndereco.text.toString()
        buscaEndereco = viewLifecycleOwner.lifecycleScope.launch {
            val ponto = buscarEndereco(requireContext(), endereco)
            if (_binding !== tela) return@launch
            if (ponto == null) tela.tvLocalizacao.setText(R.string.map_address_not_found)
            else { mapa?.centralizar(ponto); selecionarReferencia(ponto); if (tela.spRaio.selectedItemPosition == 0) tela.spRaio.setSelection(2) }
        }
    }

    // Preserva a área selecionada ao girar o aparelho.
    override fun onSaveInstanceState(outState: Bundle) {
        referencia?.let { outState.putDouble("latitude", it.latitude); outState.putDouble("longitude", it.longitude) }
        super.onSaveInstanceState(outState)
    }

    // Reinicia a consulta por data sem baixar todas as páginas automaticamente.
    private fun carregar() {
        consulta++
        cursor = null
        temMais = false
        eventos.clear()
        agora = System.currentTimeMillis()
        carregarPagina(consulta)
    }

    // Consulta páginas de eventos futuros e mantém a continuação mesmo sem pontos nesta página.
    private fun carregarPagina(numero: Int) {
        val tela = _binding ?: return
        val uid = Eventos.usuarioId
        carregando = true
        tela.btnAtualizar.isEnabled = false
        tela.btnMais.isEnabled = false
        tela.tvStatus.setText(R.string.loading)
        Eventos.pesquisar(FiltroEvento(), agora, cursor).addOnCompleteListener { tarefa ->
            if (_binding !== tela || numero != consulta || uid != Eventos.usuarioId) return@addOnCompleteListener
            carregando = false
            tela.btnAtualizar.isEnabled = true
            tela.btnMais.isEnabled = true
            if (tarefa.isSuccessful) {
                val pagina = tarefa.result
                cursor = pagina.ultimoDocumento
                temMais = pagina.temMais
                pagina.eventos.forEach { evento ->
                    if (Geografia.coordenadasValidas(evento.latitude, evento.longitude)) eventos[evento.id] = evento
                }
                atualizarMarcadores()
            } else tela.tvStatus.setText(mensagemErroEvento(tarefa.exception, R.string.event_error_load))
            tela.btnMais.visibility = if (temMais) View.VISIBLE else View.GONE
        }
    }

    // Define o centro do filtro pelo toque manual ou por uma localização autorizada.
    private fun selecionarReferencia(ponto: PontoMapa) {
        referencia = ponto
        _binding?.tvLocalizacao?.setText(R.string.map_reference_selected)
        atualizarMarcadores()
    }

    // Filtra os pontos por distância em linha reta e preserva a indicação de mais resultados.
    private fun atualizarMarcadores() {
        val tela = _binding ?: return
        val raio = raios.getOrElse(tela.spRaio.selectedItemPosition) { 0.0 }
        val centro = referencia
        if (raio > 0 && centro == null) {
            mapa?.mostrarEventos(emptyList())
            tela.tvStatus.setText(R.string.map_choose_reference)
            return
        }
        val visiveis = eventos.values.filter { evento ->
            val lat = evento.latitude
            val lon = evento.longitude
            lat != null && lon != null && (raio == 0.0 || centro == null || Geografia.distanciaKm(centro, PontoMapa(lat, lon)) <= raio)
        }
        centro?.let { mapa?.mostrarReferencia(it, raio) }
        mapa?.mostrarEventos(visiveis)
        tela.tvStatus.text = getString(if (temMais) R.string.map_count_more else R.string.map_count, visiveis.size)
    }

    // Pede autorização no momento de uso e mantém a escolha manual disponível.
    private fun pedirLocalizacao() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) localizar()
        else permissao.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }

    // Obtém uma posição única, sem acompanhar o aparelho em segundo plano.
    private fun localizar() {
        _binding?.btnLocalizacao?.isEnabled = false
        _binding?.tvLocalizacao?.setText(R.string.loading)
        localizacao?.buscar()
    }

    // Cancela a localização quando o mapa deixa de estar visível.
    override fun onStop() {
        localizacao?.cancelar()
        _binding?.btnLocalizacao?.isEnabled = true
        super.onStop()
    }

    // Libera a WebView e invalida callbacks que ainda estejam em andamento.
    override fun onDestroyView() {
        consulta++
        localizacao?.cancelar()
        localizacao = null
        mapa?.fechar()
        mapa = null
        _binding = null
        super.onDestroyView()
    }
}
