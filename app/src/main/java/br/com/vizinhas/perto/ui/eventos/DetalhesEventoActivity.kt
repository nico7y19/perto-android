package br.com.vizinhas.perto.ui.eventos

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import br.com.vizinhas.perto.model.Geografia
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import br.com.vizinhas.perto.model.GestaoEvento
import br.com.vizinhas.perto.model.GravacaoEvento
import androidx.activity.OnBackPressedCallback
import br.com.vizinhas.perto.model.Interesses
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import br.com.vizinhas.perto.R
import br.com.vizinhas.perto.databinding.ActivityDetalhesEventoBinding
import br.com.vizinhas.perto.databinding.ItemAvaliacaoBinding
import br.com.vizinhas.perto.model.Avaliacoes
import com.google.firebase.firestore.DocumentSnapshot
import br.com.vizinhas.perto.model.Conta
import br.com.vizinhas.perto.model.Evento
import br.com.vizinhas.perto.model.Eventos
import br.com.vizinhas.perto.model.ValidacaoEvento
import br.com.vizinhas.perto.ui.inicio.InicioActivity

class DetalhesEventoActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_EVENTO_ID = "evento_id"
    }

    private lateinit var binding: ActivityDetalhesEventoBinding
    private var consulta = 0
    private var eventoAtual: Evento? = null
    private var interessado: Boolean? = null
    private var salvandoInteresse = false
    private var salvandoEvento = false
    private var dialogoCancelamento: AlertDialog? = null
    private var cursorAvaliacoes: DocumentSnapshot? = null
    private var carregandoAvaliacoes = false

    // Prepara os detalhes e respeita o espaço ocupado pelas barras do sistema.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetalhesEventoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }
        binding.btnVoltar.setOnClickListener { if (!salvandoInteresse && !salvandoEvento) finish() }
        binding.btnTentar.setOnClickListener { carregar() }
        binding.btnInteresse.setOnClickListener { alterarInteresse() }
        binding.btnTentarInteresse.setOnClickListener { carregarInteresse() }
        binding.btnTentarAvaliacoes.setOnClickListener { carregarAvaliacoes() }
        binding.btnEditar.setOnClickListener {
            val evento = eventoAtual
            if (!salvandoInteresse && !salvandoEvento && evento != null)
                startActivity(Intent(this, CadastroEventoActivity::class.java).putExtra(CadastroEventoActivity.EXTRA_EDITAR_ID, evento.id))
        }
        binding.btnCancelar.setOnClickListener { confirmarCancelamento() }
        binding.btnConcluir.setOnClickListener { confirmarRealizacao() }
        binding.btnAbrirMapa.setOnClickListener { abrirLocalizacao() }
        binding.btnAvaliacoes.setOnClickListener {
            val evento = eventoAtual
            if (!salvandoEvento && !salvandoInteresse && evento != null && !evento.cancelado)
                startActivity(Intent(this, AvaliacoesActivity::class.java).putExtra(EXTRA_EVENTO_ID, evento.id))
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            // Aguarda a confirmação de uma alteração antes de sair dos detalhes.
            override fun handleOnBackPressed() { if (!salvandoInteresse && !salvandoEvento) finish() }
        })
    }

    // Reconsulta o evento ao abrir ou retomar a tela.
    override fun onStart() {
        super.onStart()
        carregar()
    }

    // Descarta respostas de consultas iniciadas antes de sair da tela.
    override fun onStop() {
        consulta++
        dialogoCancelamento?.dismiss()
        dialogoCancelamento = null
        super.onStop()
    }

    // Busca pelo ID do documento e trata falha de conexão ou evento indisponível.
    private fun carregar() {
        if (!Conta.autenticada) {
            startActivity(Intent(this, InicioActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            return
        }
        salvandoInteresse = false
        salvandoEvento = false
        binding.btnVoltar.isEnabled = true
        val id = intent.getStringExtra(EXTRA_EVENTO_ID).orEmpty()
        val uid = Eventos.usuarioId
        val numeroConsulta = ++consulta
        binding.conteudo.visibility = View.GONE
        eventoAtual = null
        interessado = null
        binding.btnInteresse.isEnabled = false
        binding.btnTentar.visibility = View.GONE
        binding.progresso.visibility = View.VISIBLE
        binding.tvStatus.setText(R.string.loading)
        if (id.isBlank() || '/' in id) {
            binding.progresso.visibility = View.GONE
            binding.tvStatus.setText(R.string.event_unavailable)
            return
        }
        Eventos.buscar(id).addOnCompleteListener(this) { tarefa ->
            if (numeroConsulta != consulta || Eventos.usuarioId != uid) return@addOnCompleteListener
            binding.progresso.visibility = View.GONE
            if (!tarefa.isSuccessful) {
                binding.tvStatus.setText(mensagemErroEvento(tarefa.exception, R.string.event_error_details))
                binding.btnTentar.visibility = View.VISIBLE
            } else {
                val evento = tarefa.result
                if (evento == null) binding.tvStatus.setText(R.string.event_unavailable)
                else mostrar(evento)
            }
        }
    }

    // Exibe os dados do evento e inicia a leitura de todas as avaliações abaixo da descrição.
    private fun mostrar(evento: Evento) {
        eventoAtual = evento
        binding.tvStatus.text = ""
        binding.conteudo.visibility = View.VISIBLE
        binding.ivCapa.setImageResource(CapasEvento.recurso(evento.capa))
        binding.tvNome.text = evento.nome
        val nomes = resources.getStringArray(R.array.event_categories)
        val indice = ValidacaoEvento.categorias.indexOf(evento.categoria)
        binding.tvCategoria.text = nomes.getOrElse(indice) { evento.categoria }
        binding.tvData.text = FormatoEvento.data(evento.inicioMillis)
        binding.tvValor.text = if (evento.valorCentavos == 0L) getString(R.string.event_free)
            else FormatoEvento.preco(evento.valorCentavos)
        binding.tvEndereco.text = evento.endereco
        binding.btnAbrirMapa.visibility = if (Geografia.coordenadasValidas(evento.latitude, evento.longitude)) View.VISIBLE else View.GONE
        binding.tvDescricao.text = evento.descricao
        binding.tvSituacao.setText(when {
            evento.cancelado -> R.string.event_canceled
            evento.realizado -> R.string.event_completed
            else -> R.string.event_started
        })
        binding.tvSituacao.visibility = if (evento.cancelado || evento.realizado || evento.inicioMillis <= System.currentTimeMillis()) View.VISIBLE else View.GONE
        atualizarGestao()
        reiniciarAvaliacoes()
        val conclusao = Eventos.conclusaoPendente
        if (conclusao != null && conclusao.eventoId == evento.id && conclusao.uid == Eventos.usuarioId) {
            observarGestao(true)
            return
        }
        val cancelamento = Eventos.cancelamentoPendente
        if (cancelamento != null && cancelamento.eventoId == evento.id && cancelamento.uid == Eventos.usuarioId) {
            observarGestao()
            return
        }
        val pendente = Interesses.alteracaoPendente
        if (pendente != null && pendente.uid == Eventos.usuarioId && pendente.eventoId == evento.id) observarAlteracao()
        else carregarInteresse()
    }

    // Consulta o estado real sem alterar o botão antes da confirmação do servidor.
    private fun carregarInteresse() {
        val evento = eventoAtual ?: return
        val numero = consulta
        val uid = Eventos.usuarioId
        interessado = null
        binding.btnInteresse.isEnabled = false
        binding.btnTentarInteresse.visibility = View.GONE
        binding.tvInteresseStatus.setText(R.string.loading)
        Interesses.estaInteressado(evento.id).addOnCompleteListener(this) { tarefa ->
            if (numero != consulta || uid != Eventos.usuarioId) return@addOnCompleteListener
            if (tarefa.isSuccessful) {
                interessado = tarefa.result
                atualizarBotaoInteresse()
            } else {
                binding.tvInteresseStatus.setText(mensagemErroInteresse(tarefa.exception, R.string.interest_load_error))
                binding.btnTentarInteresse.visibility = View.VISIBLE
            }
        }
    }

    // Inicia uma alteração idempotente e preserva a tarefa durante a rotação.
    private fun alterarInteresse() {
        val evento = eventoAtual ?: return
        val estado = interessado ?: return
        if (salvandoInteresse || salvandoEvento) return
        Interesses.alterar(evento.id, !estado)
        observarAlteracao()
    }

    // Retoma o resultado de uma alteração ao retornar ao primeiro plano.
    private fun observarAlteracao() {
        val operacao = Interesses.alteracaoPendente ?: return
        salvandoInteresse = true
        atualizarGestao()
        binding.btnVoltar.isEnabled = false
        binding.btnInteresse.isEnabled = false
        binding.btnTentarInteresse.visibility = View.GONE
        binding.tvInteresseStatus.setText(R.string.loading)
        operacao.tarefa.addOnCompleteListener(this) { tarefa ->
            if (Interesses.alteracaoPendente !== operacao || operacao.uid != Eventos.usuarioId) return@addOnCompleteListener
            Interesses.alteracaoPendente = null
            salvandoInteresse = false
            atualizarGestao()
            binding.btnVoltar.isEnabled = true
            if (tarefa.isSuccessful) {
                interessado = tarefa.result
                atualizarBotaoInteresse()
                binding.tvInteresseStatus.setText(if (tarefa.result) R.string.interest_saved else R.string.interest_removed)
            } else {
                interessado = null
                binding.tvInteresseStatus.setText(mensagemErroInteresse(tarefa.exception, R.string.interest_save_error))
                binding.btnTentarInteresse.visibility = View.VISIBLE
            }
        }
    }

    // Atualiza o coração após confirmação e permite remover interesses de eventos antigos.
    private fun atualizarBotaoInteresse() {
        val evento = eventoAtual ?: return
        val marcado = interessado == true
        val futuro = !evento.cancelado && !evento.realizado && evento.inicioMillis > System.currentTimeMillis()
        binding.btnInteresse.setText(if (marcado) R.string.interest_remove else R.string.interest_add)
        binding.btnInteresse.setIconResource(if (marcado) R.drawable.ic_favoritos else R.drawable.ic_favoritos_contorno)
        binding.btnInteresse.isEnabled = !salvandoEvento && !salvandoInteresse && interessado != null && (marcado || futuro)
        binding.tvInteresseStatus.text = when {
            evento.cancelado -> getString(R.string.interest_canceled)
            evento.realizado -> getString(R.string.interest_completed)
            !marcado && !futuro -> getString(R.string.interest_expired)
            else -> ""
        }
    }

    // Limpa resultados antigos e prepara a consulta das avaliações do evento atual.
    private fun reiniciarAvaliacoes() {
        cursorAvaliacoes = null
        carregandoAvaliacoes = false
        binding.listaAvaliacoes.removeAllViews()
        binding.btnTentarAvaliacoes.visibility = View.GONE
        if (eventoAtual?.cancelado == true) {
            binding.tvListaAvaliacoesStatus.setText(R.string.review_canceled)
            return
        }
        carregarAvaliacoes()
    }

    // Lê as páginas em sequência para exibir todas as avaliações, preservando as anteriores se houver erro.
    private fun carregarAvaliacoes() {
        val evento = eventoAtual ?: return
        if (carregandoAvaliacoes || evento.cancelado) return
        val numero = consulta
        val uid = Eventos.usuarioId
        carregandoAvaliacoes = true
        binding.btnTentarAvaliacoes.visibility = View.GONE
        binding.tvListaAvaliacoesStatus.setText(R.string.loading)
        Avaliacoes.listar(evento.id, cursorAvaliacoes).addOnCompleteListener(this) { tarefa ->
            if (numero != consulta || uid != Eventos.usuarioId) return@addOnCompleteListener
            carregandoAvaliacoes = false
            if (!tarefa.isSuccessful) {
                binding.tvListaAvaliacoesStatus.setText(mensagemErroEvento(tarefa.exception, R.string.review_error))
                binding.btnTentarAvaliacoes.visibility = View.VISIBLE
                return@addOnCompleteListener
            }
            val pagina = tarefa.result
            pagina.avaliacoes.forEach { avaliacao ->
                val item = ItemAvaliacaoBinding.inflate(layoutInflater, binding.listaAvaliacoes, false)
                item.nota.text = getString(R.string.review_score_value, avaliacao.nota)
                item.comentario.text = avaliacao.comentario
                item.comentario.visibility = if (avaliacao.comentario.isBlank()) View.GONE else View.VISIBLE
                binding.listaAvaliacoes.addView(item.root)
            }
            cursorAvaliacoes = pagina.cursor
            if (pagina.temMais) carregarAvaliacoes()
            else if (binding.listaAvaliacoes.childCount == 0) binding.tvListaAvaliacoesStatus.setText(R.string.review_empty)
            else binding.tvListaAvaliacoesStatus.text = ""
        }
    }


    // Atualiza o acesso às avaliações e oferece ações de gestão apenas ao dono.
    private fun atualizarGestao() {
        val evento = eventoAtual ?: return
        binding.btnAvaliacoes.isEnabled = !evento.cancelado && !salvandoEvento && !salvandoInteresse
        binding.tvAvaliacoesStatus.setText(when {
            evento.cancelado -> R.string.review_canceled
            else -> R.string.review_available
        })
        val agora = System.currentTimeMillis()
        val editar = GestaoEvento.podeAlterar(evento, Eventos.usuarioId, agora)
        val concluir = GestaoEvento.podeConcluir(evento, Eventos.usuarioId, agora)
        binding.painelGestao.visibility = if (editar || concluir) View.VISIBLE else View.GONE
        binding.btnEditar.visibility = if (editar) View.VISIBLE else View.GONE
        binding.btnCancelar.visibility = if (editar) View.VISIBLE else View.GONE
        binding.btnConcluir.visibility = if (concluir) View.VISIBLE else View.GONE
        binding.btnEditar.isEnabled = !salvandoInteresse && !salvandoEvento
        binding.btnCancelar.isEnabled = !salvandoInteresse && !salvandoEvento
        binding.btnConcluir.isEnabled = !salvandoInteresse && !salvandoEvento
    }

    // Apresenta as consequências antes de solicitar o cancelamento ao servidor.
    private fun confirmarCancelamento() {
        val evento = eventoAtual ?: return
        if (salvandoInteresse || salvandoEvento || !GestaoEvento.podeAlterar(evento, Eventos.usuarioId, System.currentTimeMillis())) return
        dialogoCancelamento = AlertDialog.Builder(this)
            .setTitle(R.string.event_cancel_title).setMessage(R.string.event_cancel_message)
            .setNegativeButton(R.string.event_keep, null)
            .setPositiveButton(R.string.event_cancel) { _, _ ->
                Eventos.cancelamentoPendente = GravacaoEvento(Eventos.usuarioId, evento.id, Eventos.cancelar(evento.id))
                observarGestao()
            }.show()
    }

    // Confirma a ação irreversível antes de marcar um evento iniciado como realizado.
    private fun confirmarRealizacao() {
        val evento = eventoAtual ?: return
        if (salvandoInteresse || salvandoEvento || !GestaoEvento.podeConcluir(evento, Eventos.usuarioId, System.currentTimeMillis())) return
        dialogoCancelamento = AlertDialog.Builder(this)
            .setTitle(R.string.event_complete).setMessage(R.string.event_complete_message)
            .setNegativeButton(R.string.event_back, null)
            .setPositiveButton(R.string.event_complete) { _, _ ->
                Eventos.conclusaoPendente = GravacaoEvento(Eventos.usuarioId, evento.id, Eventos.concluir(evento.id))
                observarGestao(true)
            }.show()
    }

    // Reconecta cancelamentos e conclusões e atualiza a tela somente após a confirmação.
    private fun observarGestao(concluir: Boolean = false) {
        val operacao = (if (concluir) Eventos.conclusaoPendente else Eventos.cancelamentoPendente) ?: return
        salvandoEvento = true
        atualizarGestao()
        binding.btnVoltar.isEnabled = false
        binding.btnInteresse.isEnabled = false
        binding.btnTentarInteresse.visibility = View.GONE
        binding.tvStatus.setText(R.string.loading)
        operacao.tarefa.addOnCompleteListener(this) { tarefa ->
            val pendente = if (concluir) Eventos.conclusaoPendente else Eventos.cancelamentoPendente
            if (pendente !== operacao || operacao.uid != Eventos.usuarioId) return@addOnCompleteListener
            if (concluir) Eventos.conclusaoPendente = null else Eventos.cancelamentoPendente = null
            salvandoEvento = false
            binding.btnVoltar.isEnabled = true
            atualizarGestao()
            if (tarefa.isSuccessful) carregar()
            else {
                binding.tvStatus.setText(mensagemErroEvento(tarefa.exception,
                    if (concluir) R.string.event_error_complete else R.string.event_error_cancel))
                binding.btnTentar.visibility = View.VISIBLE
                atualizarBotaoInteresse()
            }
        }
    }


    // Abre um aplicativo de mapas no ponto público do evento sem solicitar a posição do usuário.
    private fun abrirLocalizacao() {
        val evento = eventoAtual ?: return
        if (!Geografia.coordenadasValidas(evento.latitude, evento.longitude)) return
        val ponto = "${evento.latitude},${evento.longitude}"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$ponto?q=$ponto(${Uri.encode(evento.nome)})")))
        } catch (_: android.content.ActivityNotFoundException) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/?mlat=${evento.latitude}&mlon=${evento.longitude}#map=16/${evento.latitude}/${evento.longitude}"))) }
            catch (_: android.content.ActivityNotFoundException) { Toast.makeText(this, R.string.map_no_app, Toast.LENGTH_LONG).show() }
        }
    }

}
