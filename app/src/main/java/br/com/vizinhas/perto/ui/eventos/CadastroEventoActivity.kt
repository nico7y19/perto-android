package br.com.vizinhas.perto.ui.eventos

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import br.com.vizinhas.perto.model.Geografia
import br.com.vizinhas.perto.ui.mapa.EscolherLocalActivity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import br.com.vizinhas.perto.R
import br.com.vizinhas.perto.databinding.ActivityCadastroEventoBinding
import br.com.vizinhas.perto.model.GestaoEvento
import br.com.vizinhas.perto.model.GravacaoEvento
import java.time.Instant
import br.com.vizinhas.perto.model.Conta
import br.com.vizinhas.perto.model.Evento
import br.com.vizinhas.perto.model.Eventos
import br.com.vizinhas.perto.model.EventoJaPublicadoException
import br.com.vizinhas.perto.model.ValidacaoEvento
import com.google.android.material.card.MaterialCardView
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class CadastroEventoActivity : AppCompatActivity() {
    companion object { const val EXTRA_EDITAR_ID = "editar_evento_id" }
    private var editando = false
    private var preenchido = false
    private lateinit var binding: ActivityCadastroEventoBinding
    private val fuso = ZoneId.of("America/Sao_Paulo")
    private var data: LocalDate? = null
    private var hora: LocalTime? = null
    private var capa = "musica"
    private var identificador = ""
    private var autorizado = false
    private var carregando = false
    private lateinit var cartoes: Map<String, MaterialCardView>

    private var latitude: Double? = null
    private var longitude: Double? = null
    private val escolherPonto = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultado ->
        if (resultado.resultCode == RESULT_OK) {
            val lat = resultado.data?.getDoubleExtra(EscolherLocalActivity.LATITUDE, Double.NaN)
            val lon = resultado.data?.getDoubleExtra(EscolherLocalActivity.LONGITUDE, Double.NaN)
            if (Geografia.coordenadasValidas(lat, lon)) { latitude = lat; longitude = lon; atualizarPonto() }
        }
    }

    // Prepara os campos e restaura as escolhas sem recriar um evento em andamento.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCadastroEventoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        editando = intent.hasExtra(EXTRA_EDITAR_ID)
        identificador = savedInstanceState?.getString("eventoId") ?: intent.getStringExtra(EXTRA_EDITAR_ID) ?: UUID.randomUUID().toString()
        preenchido = savedInstanceState?.getBoolean("preenchido") ?: false
        if (savedInstanceState?.containsKey("latitude") == true) {
            latitude = savedInstanceState.getDouble("latitude")
            longitude = savedInstanceState.getDouble("longitude")
        }
        if (editando) binding.tvTitulo.setText(R.string.event_edit)
        data = savedInstanceState?.getString("data")?.let { LocalDate.parse(it) }
        hora = savedInstanceState?.getString("hora")?.let { LocalTime.parse(it) }
        capa = savedInstanceState?.getString("capa") ?: "musica"
        cartoes = mapOf("musica" to binding.capaMusica, "gastronomia" to binding.capaGastronomia,
            "esportes" to binding.capaEsportes, "cultura" to binding.capaCultura,
            "comunidade" to binding.capaComunidade)
        cartoes.forEach { (id, cartao) -> cartao.setOnClickListener { selecionarCapa(id) } }
        selecionarCapa(capa)
        binding.spCategoria.adapter = ArrayAdapter.createFromResource(this, R.array.event_categories, R.layout.spinner_perto)
        binding.btnData.setOnClickListener { escolherData() }
        binding.btnHora.setOnClickListener { escolherHora() }
        binding.btnVoltar.setOnClickListener { if (!carregando) finish() }
        binding.swGratuito.setOnCheckedChangeListener { _, gratuito ->
            binding.etValor.visibility = if (gratuito) View.GONE else View.VISIBLE
        }
        binding.btnPublicar.setOnClickListener { publicar() }
        binding.btnPontoMapa.setOnClickListener {
            val destino = Intent(this, EscolherLocalActivity::class.java).putExtra(EscolherLocalActivity.ENDERECO, binding.etEndereco.text.toString())
            latitude?.let { destino.putExtra(EscolherLocalActivity.LATITUDE, it) }
            longitude?.let { destino.putExtra(EscolherLocalActivity.LONGITUDE, it) }
            escolherPonto.launch(destino)
        }
        binding.btnRemoverPonto.setOnClickListener { latitude = null; longitude = null; atualizarPonto() }
        binding.etEndereco.doAfterTextChanged {
            if (!carregando && latitude != null) { latitude = null; longitude = null; atualizarPonto() }
        }
        atualizarPonto()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            // Mantém o formulário enquanto a publicação aguarda confirmação.
            override fun handleOnBackPressed() { if (!carregando) finish() }
        })
        atualizarDataHora()
    }

    // Confere o tipo de perfil no servidor a cada abertura da tela.
    override fun onStart() {
        super.onStart()
        atualizarCarregamento(true)
        Conta.carregarPerfil().addOnCompleteListener(this) { tarefa ->
            autorizado = tarefa.isSuccessful && tarefa.result?.tipo == "organizador"
            if (!autorizado) {
                Toast.makeText(this, R.string.event_organizer_only, Toast.LENGTH_LONG).show()
                finish()
            } else {
                val pendente = Eventos.gravacaoPendente
                if (pendente != null && pendente.eventoId == identificador && pendente.uid == Eventos.usuarioId) observarPublicacao()
                else if (editando) carregarEdicao() else atualizarCarregamento(false)
            }
        }
    }

    // Guarda as escolhas que não são restauradas automaticamente pelos campos Android.
    override fun onSaveInstanceState(outState: Bundle) {
        latitude?.let { outState.putDouble("latitude", it) }
        longitude?.let { outState.putDouble("longitude", it) }
        outState.putBoolean("preenchido", preenchido)
        outState.putString("eventoId", identificador)
        outState.putString("data", data?.toString())
        outState.putString("hora", hora?.toString())
        outState.putString("capa", capa)
        super.onSaveInstanceState(outState)
    }

    // Reconfere a propriedade e preenche o formulário apenas na primeira abertura.
    private fun carregarEdicao() {
        Eventos.buscar(identificador).addOnCompleteListener(this) { tarefa ->
            val evento = if (tarefa.isSuccessful) tarefa.result else null
            if (!tarefa.isSuccessful) {
                Toast.makeText(this, mensagemErroEvento(tarefa.exception, R.string.event_error_load), Toast.LENGTH_LONG).show()
                finish()
            } else if (evento == null || !GestaoEvento.podeAlterar(evento, Eventos.usuarioId, System.currentTimeMillis())) {
                Toast.makeText(this, R.string.event_cannot_edit, Toast.LENGTH_LONG).show()
                finish()
            } else {
                if (!preenchido) {
                    binding.etNome.setText(evento.nome)
                    binding.etDescricao.setText(evento.descricao)
                    binding.etEndereco.setText(evento.endereco)
                    binding.spCategoria.setSelection(ValidacaoEvento.categorias.indexOf(evento.categoria).coerceAtLeast(0))
                    binding.swGratuito.isChecked = evento.valorCentavos == 0L
                    binding.etValor.setText(java.math.BigDecimal.valueOf(evento.valorCentavos, 2).toPlainString().replace('.', ','))
                    binding.etValor.visibility = if (binding.swGratuito.isChecked) View.GONE else View.VISIBLE
                    val inicio = Instant.ofEpochMilli(evento.inicioMillis).atZone(fuso)
                    data = inicio.toLocalDate()
                    hora = inicio.toLocalTime()
                    latitude = evento.latitude
                    longitude = evento.longitude
                    atualizarPonto()
                    selecionarCapa(evento.capa)
                    atualizarDataHora()
                    preenchido = true
                }
                atualizarCarregamento(false)
            }
        }
    }

    // Destaca visualmente uma única capa e anuncia a seleção para acessibilidade.
    private fun selecionarCapa(id: String) {
        capa = id
        val nomes = resources.getStringArray(R.array.event_categories)
        cartoes.forEach { (chave, cartao) ->
            val nome = nomes[ValidacaoEvento.capas.indexOf(chave)]
            cartao.contentDescription = if (chave == id) getString(R.string.event_selected_cover, nome) else nome
            cartao.isChecked = chave == id
            cartao.isSelected = chave == id
            cartao.strokeColor = ContextCompat.getColor(this, if (chave == id) R.color.perto_accent else R.color.perto_border)
        }
    }

    // Abre o calendário com a última escolha ou o dia atual no fuso de Brasília.
    private fun escolherData() {
        val atual = data ?: LocalDate.now(fuso)
        DatePickerDialog(this, { _, ano, mes, dia ->
            data = LocalDate.of(ano, mes + 1, dia)
            atualizarDataHora()
        }, atual.year, atual.monthValue - 1, atual.dayOfMonth).show()
    }

    // Permite escolher o horário sem converter datas ou horários digitados.
    private fun escolherHora() {
        val atual = hora ?: LocalTime.now(fuso)
        TimePickerDialog(this, { _, horas, minutos ->
            hora = LocalTime.of(horas, minutos)
            atualizarDataHora()
        }, atual.hour, atual.minute, true).show()
    }

    // Mostra as escolhas de data e horário no formulário.
    private fun atualizarDataHora() {
        binding.btnData.text = data?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) ?: getString(R.string.event_date)
        binding.btnHora.text = hora?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: getString(R.string.event_time)
    }

    // Valida o formulário e inicia o cadastro ou a edição sem alterar o ID do evento.
    private fun publicar() {
        if (carregando || !autorizado) return
        binding.tvStatus.text = ""
        listOf(binding.etNome, binding.etDescricao, binding.etEndereco, binding.etValor).forEach { it.error = null }
        val dia = data
        val horario = hora
        val inicio = if (dia != null && horario != null) dia.atTime(horario).atZone(fuso).toInstant().toEpochMilli() else 0
        val valor = if (binding.swGratuito.isChecked) 0L else ValidacaoEvento.valorEmCentavos(binding.etValor.text.toString())
        val evento = Evento(binding.etNome.text.toString().trim(), binding.etDescricao.text.toString().trim(),
            ValidacaoEvento.categorias[binding.spCategoria.selectedItemPosition], binding.etEndereco.text.toString().trim(),
            inicio, valor ?: -1, capa, Eventos.usuarioId, latitude = latitude, longitude = longitude)
        when {
            evento.nome.length !in 3..120 -> { binding.etNome.error = getString(R.string.event_error_nome); binding.etNome.requestFocus(); return }
            evento.descricao.length !in 10..3000 -> { binding.etDescricao.error = getString(R.string.event_error_descricao); binding.etDescricao.requestFocus(); return }
            evento.endereco.length !in 5..300 -> { binding.etEndereco.error = getString(R.string.event_error_endereco); binding.etEndereco.requestFocus(); return }
            inicio <= System.currentTimeMillis() -> { binding.tvStatus.setText(R.string.event_error_date); return }
            valor == null -> { binding.etValor.error = getString(R.string.event_error_price); binding.etValor.requestFocus(); return }
        }
        if (!ValidacaoEvento.eventoValido(evento, System.currentTimeMillis())) return
        val tarefa = if (editando) Eventos.editar(identificador, evento) else Eventos.cadastrar(identificador, evento)
        Eventos.gravacaoPendente = GravacaoEvento(Eventos.usuarioId, identificador, tarefa)
        observarPublicacao()
    }

    // Retoma a confirmação de uma publicação após rotação ou retorno à tela.
    private fun observarPublicacao() {
        val operacao = Eventos.gravacaoPendente ?: return
        if (operacao.eventoId != identificador || operacao.uid != Eventos.usuarioId) return
        atualizarCarregamento(true)
        operacao.tarefa.addOnCompleteListener(this) { tarefa ->
            if (Eventos.gravacaoPendente !== operacao || operacao.uid != Eventos.usuarioId) return@addOnCompleteListener
            Eventos.gravacaoPendente = null
            atualizarCarregamento(false)
            if (tarefa.isSuccessful) {
                Toast.makeText(this, if (editando) R.string.event_updated else R.string.event_saved, Toast.LENGTH_SHORT).show()
                finish()
            } else if (tarefa.exception is EventoJaPublicadoException) {
                Toast.makeText(this, R.string.event_already_saved, Toast.LENGTH_LONG).show()
                finish()
            } else binding.tvStatus.setText(mensagemErroEvento(tarefa.exception, R.string.event_error_save))
        }
    }

    // Bloqueia a edição durante o envio e permite corrigir os dados após uma falha.
    private fun atualizarCarregamento(ativo: Boolean) {
        carregando = ativo
        listOf(binding.etNome, binding.etDescricao, binding.etEndereco, binding.etValor,
            binding.spCategoria, binding.swGratuito, binding.btnData, binding.btnHora, binding.btnPontoMapa, binding.btnRemoverPonto).forEach { it.isEnabled = !ativo }
        cartoes.values.forEach { it.isEnabled = !ativo }
        binding.btnPublicar.isEnabled = !ativo && autorizado
        binding.btnVoltar.isEnabled = !ativo
        binding.btnPublicar.setText(if (ativo) R.string.loading else if (editando) R.string.event_save_changes else R.string.event_publish)
    }

    // Informa se o evento já tem um ponto e permite removê-lo antes de salvar.
    private fun atualizarPonto() {
        val possui = Geografia.coordenadasValidas(latitude, longitude)
        binding.tvPontoMapa.setText(if (possui) R.string.map_point_selected else R.string.map_no_point)
        binding.btnRemoverPonto.visibility = if (possui) View.VISIBLE else View.GONE
    }

}
