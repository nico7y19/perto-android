package br.com.vizinhas.perto.ui.principal.fragmentos

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.KeyEvent
import android.widget.CheckBox
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import br.com.vizinhas.perto.databinding.DialogFiltrosBinding
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import br.com.vizinhas.perto.R
import br.com.vizinhas.perto.databinding.FragmentInicioBinding
import br.com.vizinhas.perto.databinding.ItemEventoBinding
import br.com.vizinhas.perto.model.Conta
import br.com.vizinhas.perto.model.Evento
import br.com.vizinhas.perto.model.Eventos
import br.com.vizinhas.perto.model.FiltroEvento
import br.com.vizinhas.perto.model.TipoPreco
import br.com.vizinhas.perto.model.ValidacaoEvento
import br.com.vizinhas.perto.ui.eventos.CadastroEventoActivity
import br.com.vizinhas.perto.ui.eventos.CapasEvento
import br.com.vizinhas.perto.ui.eventos.DetalhesEventoActivity
import br.com.vizinhas.perto.ui.eventos.FormatoEvento
import br.com.vizinhas.perto.ui.eventos.mensagemErroEvento
import com.google.firebase.firestore.DocumentSnapshot

class InicioFragment : Fragment() {
    private var _binding: FragmentInicioBinding? = null
    private var consulta = 0
    private var filtro = FiltroEvento()
    private var dialogoFiltros: AlertDialog? = null
    private var organizador: Boolean? = null
    private var carregando = false
    private var dadosCarregados = false
    private var uidCarregado = ""
    private var cursor: DocumentSnapshot? = null
    private var temMais = false
    private var agoraBusca = 0L
    private val idsExibidos = mutableSetOf<String>()
    private val eventosProprios = linkedMapOf<String, Evento>()

    // Restaura os filtros aplicados após recriação da tela.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { estado ->
            filtro = FiltroEvento(nome = estado.getString("nome").orEmpty(),
                tipoPreco = TipoPreco.entries.getOrElse(estado.getInt("preco")) { TipoPreco.TODOS }
                    .takeUnless { it == TipoPreco.ATE_VALOR } ?: TipoPreco.TODOS,
                categorias = estado.getStringArrayList("categorias")?.toSet()
                    ?: setOfNotNull(estado.getString("categoria")))
        }
    }

    // Prepara os filtros, a atualização e o acesso do organizador ao cadastro.
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val tela = FragmentInicioBinding.inflate(inflater, container, false)
        _binding = tela
        dadosCarregados = false
        tela.etBusca.setText(filtro.nome)
        tela.btnFiltros.setOnClickListener { abrirFiltros() }
        atualizarResumoFiltros()
        tela.btnCriar.setOnClickListener { startActivity(Intent(requireContext(), CadastroEventoActivity::class.java)) }
        tela.btnAtualizar.setOnClickListener { carregar() }
        tela.btnCarregarMais.setOnClickListener { if (!carregando && temMais) carregarPagina(consulta) }
        tela.etBusca.setOnEditorActionListener { _, acao, tecla ->
            val enter = tecla?.keyCode == KeyEvent.KEYCODE_ENTER || tecla?.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
            when {
                enter -> {
                    if (tecla.action == KeyEvent.ACTION_DOWN && tecla.repeatCount == 0 && !tecla.isCanceled) pesquisar()
                    true
                }
                acao == EditorInfo.IME_ACTION_SEARCH || acao == EditorInfo.IME_ACTION_DONE -> { pesquisar(); true }
                else -> false
            }
        }
        return tela.root
    }

    // Guarda as escolhas sem persistir cursores ou resultados antigos do servidor.
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("nome", filtro.nome)
        outState.putStringArrayList("categorias", ArrayList(filtro.categorias))
        outState.putInt("preco", filtro.tipoPreco.ordinal)
        super.onSaveInstanceState(outState)
    }

    // Preserva a busca ao voltar dos detalhes e atualiza o painel após criar eventos.
    override fun onResume() {
        super.onResume()
        if (!dadosCarregados || organizador != false || uidCarregado != Eventos.usuarioId) carregar()
    }

    // Confirma o nome pelo teclado, mantendo os filtros escolhidos e descartando respostas antigas.
    private fun pesquisar() {
        val tela = _binding ?: return
        filtro = filtro.copy(nome = tela.etBusca.text.toString().trim())
        atualizarResumoFiltros()
        WindowCompat.getInsetsController(requireActivity().window, tela.root).hide(WindowInsetsCompat.Type.ime())
        carregar()
    }

    // Abre as categorias múltiplas e as três opções de preço sem alterar a seleção ao cancelar.
    private fun abrirFiltros() {
        if (dialogoFiltros?.isShowing == true) return
        val painel = DialogFiltrosBinding.inflate(layoutInflater)
        val nomes = resources.getStringArray(R.array.event_categories)
        val caixas = ValidacaoEvento.categorias.mapIndexed { indice, categoria ->
            CheckBox(requireContext()).apply {
                text = nomes[indice]
                setTextColor(requireContext().getColor(R.color.white))
                isChecked = categoria in filtro.categorias
                painel.categorias.addView(this)
            }
        }
        painel.precos.check(when (filtro.tipoPreco) {
            TipoPreco.GRATUITOS -> R.id.preco_gratuitos
            TipoPreco.PAGOS -> R.id.preco_pagos
            else -> R.id.preco_todos
        })
        dialogoFiltros = AlertDialog.Builder(requireContext())
            .setTitle(R.string.filter_open).setView(painel.root)
            .setNegativeButton(R.string.filter_cancel, null)
            .setNeutralButton(R.string.filter_clear) { _, _ ->
                filtro = FiltroEvento(nome = filtro.nome)
                pesquisar()
            }
            .setPositiveButton(R.string.filter_confirm) { _, _ ->
                val categorias = ValidacaoEvento.categorias.filterIndexed { indice, _ -> caixas[indice].isChecked }.toSet()
                val preco = when (painel.precos.checkedRadioButtonId) {
                    R.id.preco_gratuitos -> TipoPreco.GRATUITOS
                    R.id.preco_pagos -> TipoPreco.PAGOS
                    else -> TipoPreco.TODOS
                }
                filtro = FiltroEvento(nome = filtro.nome, tipoPreco = preco, categorias = categorias)
                pesquisar()
            }.create()
        dialogoFiltros?.show()
    }

    // Informa os critérios ativos mesmo quando o painel de filtros está fechado.
    private fun atualizarResumoFiltros() {
        val tela = _binding ?: return
        val nomes = resources.getStringArray(R.array.event_categories)
        val categorias = ValidacaoEvento.categorias.mapIndexedNotNull { indice, categoria ->
            nomes[indice].takeIf { categoria in filtro.categorias }
        }.joinToString(", ").ifEmpty { getString(R.string.filter_all_categories) }
        val preco = resources.getStringArray(R.array.filter_price_options)[filtro.tipoPreco.ordinal.coerceIn(0, 2)]
        tela.tvFiltros.text = getString(R.string.filter_selection, categorias, preco)
    }

    // Confere o perfil e inicia uma consulta independente das páginas anteriores.
    private fun carregar() {
        val tela = _binding ?: return
        val numero = ++consulta
        uidCarregado = Eventos.usuarioId
        val uid = uidCarregado
        cursor = null
        temMais = false
        dadosCarregados = false
        idsExibidos.clear()
        eventosProprios.clear()
        agoraBusca = System.currentTimeMillis()
        tela.listaEventos.removeAllViews()
        tela.painelDestaque.visibility = View.GONE
        tela.tvStatus.setText(R.string.loading)
        tela.btnCriar.visibility = View.GONE
        tela.btnAtualizar.visibility = View.GONE
        atualizarCarregamento(true)
        Conta.carregarPerfil().addOnCompleteListener { tarefa ->
            if (_binding !== tela || numero != consulta || uid != Eventos.usuarioId) return@addOnCompleteListener
            if (!tarefa.isSuccessful) { mostrarErro(tarefa.exception); return@addOnCompleteListener }
            organizador = tarefa.result?.tipo == "organizador"
            tela.tvNomeConta.text = tarefa.result?.nome?.trim()?.substringBefore(" ")?.takeIf { it.isNotBlank() } ?: getString(R.string.app_name)
            tela.tvIntro.setText(if (organizador == true) R.string.home_organizer_intro else R.string.home_intro)
            tela.painelFiltros.visibility = if (organizador == false) View.VISIBLE else View.GONE
            tela.tvTitulo.setText(if (organizador == true) R.string.event_mine else R.string.event_common_title)
            if (organizador == true) {
                tela.btnCriar.visibility = View.VISIBLE
            }
            carregarPagina(numero)
        }
    }

    // Avança pelo cursor original e ordena todos os eventos próprios já carregados por data.
    private fun carregarPagina(numero: Int) {
        val tela = _binding ?: return
        val uid = Eventos.usuarioId
        atualizarCarregamento(true)
        tela.tvStatus.setText(R.string.loading)
        val busca = if (organizador == true) Eventos.listarProprios(cursor)
            else Eventos.pesquisar(filtro, agoraBusca, cursor)
        busca.addOnCompleteListener { tarefa ->
            if (_binding !== tela || numero != consulta || uid != Eventos.usuarioId) return@addOnCompleteListener
            if (!tarefa.isSuccessful) { mostrarErro(tarefa.exception); return@addOnCompleteListener }
            val pagina = tarefa.result
            cursor = pagina.ultimoDocumento
            temMais = pagina.temMais
            if (organizador == true) {
                pagina.eventos.forEach { eventosProprios[it.id] = it }
                tela.listaEventos.removeAllViews()
                idsExibidos.clear()
                adicionarCards(eventosProprios.values.sortedWith(compareBy<Evento> { it.inicioMillis }.thenBy { it.id }))
            } else adicionarCards(pagina.eventos)
            if (organizador == false && idsExibidos.isEmpty() && temMais) {
                carregarPagina(numero)
                return@addOnCompleteListener
            }
            dadosCarregados = true
            atualizarCarregamento(false)
            tela.tvStatus.text = when {
                organizador == true && idsExibidos.isEmpty() && temMais -> getString(R.string.event_mine_not_yet)
                organizador == true && idsExibidos.isEmpty() -> getString(R.string.event_empty)
                organizador == true && temMais -> getString(R.string.event_mine_count_more, idsExibidos.size)
                organizador == true -> getString(R.string.event_mine_count, idsExibidos.size)
                idsExibidos.isEmpty() && temMais -> getString(R.string.filter_not_yet)
                idsExibidos.isEmpty() -> getString(R.string.filter_empty)
                temMais -> getString(R.string.filter_count_more, idsExibidos.size)
                else -> getString(R.string.filter_count, idsExibidos.size)
            }
        }
    }

    // Mantém os resultados anteriores em falhas de paginação e permite repetir a consulta.
    private fun mostrarErro(erro: Exception?) {
        val tela = _binding ?: return
        atualizarCarregamento(false)
        tela.tvStatus.setText(mensagemErroEvento(erro, R.string.event_error_load))
        tela.btnAtualizar.setText(R.string.retry)
    }

    // Bloqueia atualização e paginação durante a consulta, permitindo substituir a busca pelo teclado.
    private fun atualizarCarregamento(ativo: Boolean) {
        val tela = _binding ?: return
        carregando = ativo
        listOf(tela.btnAtualizar, tela.btnCarregarMais).forEach { it.isEnabled = !ativo }
        tela.btnCarregarMais.visibility = if (temMais) View.VISIBLE else View.GONE
        tela.btnCarregarMais.setText(if (ativo) R.string.loading else R.string.filter_more)
        if (!ativo) { tela.btnAtualizar.visibility = View.VISIBLE; tela.btnAtualizar.setText(R.string.event_refresh) }
    }

    // Apresenta cartões compactos e destaca o primeiro evento encontrado na descoberta.
    private fun adicionarCards(eventos: List<Evento>) {
        val tela = _binding ?: return
        val nomes = resources.getStringArray(R.array.event_categories)
        eventos.forEach { evento ->
            if (!idsExibidos.add(evento.id)) return@forEach
            if (organizador == false && idsExibidos.size == 1) {
                tela.painelDestaque.visibility = View.VISIBLE
                tela.ivDestaque.setImageResource(CapasEvento.recurso(evento.capa))
                tela.tvDestaqueNome.text = evento.nome
                tela.tvDestaqueLocal.text = evento.endereco
                tela.cardDestaque.contentDescription = evento.nome
                tela.cardDestaque.setOnClickListener {
                    startActivity(Intent(requireContext(), DetalhesEventoActivity::class.java)
                        .putExtra(DetalhesEventoActivity.EXTRA_EVENTO_ID, evento.id))
                }
            }
            val item = ItemEventoBinding.inflate(layoutInflater, tela.listaEventos, false)
            item.ivCapa.setImageResource(CapasEvento.recurso(evento.capa))
            item.tvNome.text = evento.nome
            val preco = if (evento.valorCentavos == 0L) getString(R.string.event_free) else FormatoEvento.preco(evento.valorCentavos)
            val indice = ValidacaoEvento.categorias.indexOf(evento.categoria)
            item.tvCategoria.text = nomes.getOrElse(indice) { evento.categoria }
            item.tvPreco.text = preco
            item.tvPreco.setBackgroundResource(if (evento.valorCentavos == 0L) R.drawable.perto_pill_free else R.drawable.perto_pill)
            item.tvPreco.setTextColor(requireContext().getColor(if (evento.valorCentavos == 0L) R.color.perto_green else R.color.perto_accent))
            item.tvInfo.text = listOfNotNull(if (evento.realizado) getString(R.string.event_completed) else null, FormatoEvento.data(evento.inicioMillis), evento.endereco).joinToString("\n")
            item.tvDescricao.text = evento.descricao
            item.root.setOnClickListener {
                startActivity(Intent(requireContext(), DetalhesEventoActivity::class.java).putExtra(DetalhesEventoActivity.EXTRA_EVENTO_ID, evento.id))
            }
            tela.listaEventos.addView(item.root)
        }
    }

    // Descarta respostas antigas e fecha o diálogo ao destruir a interface do fragmento.
    override fun onDestroyView() {
        consulta++
        dialogoFiltros?.dismiss()
        dialogoFiltros = null
        _binding = null
        super.onDestroyView()
    }
}
