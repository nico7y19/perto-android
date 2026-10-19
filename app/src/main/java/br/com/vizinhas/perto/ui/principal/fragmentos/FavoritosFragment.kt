package br.com.vizinhas.perto.ui.principal.fragmentos

import br.com.vizinhas.perto.ui.notificacoes.LembretesLocais
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import br.com.vizinhas.perto.R
import br.com.vizinhas.perto.databinding.FragmentFavoritosBinding
import br.com.vizinhas.perto.databinding.ItemFavoritoBinding
import br.com.vizinhas.perto.model.Eventos
import br.com.vizinhas.perto.model.Favorito
import br.com.vizinhas.perto.model.Interesses
import br.com.vizinhas.perto.model.ValidacaoEvento
import br.com.vizinhas.perto.ui.eventos.CapasEvento
import br.com.vizinhas.perto.ui.eventos.DetalhesEventoActivity
import br.com.vizinhas.perto.ui.eventos.FormatoEvento
import br.com.vizinhas.perto.ui.eventos.mensagemErroInteresse
import com.google.firebase.firestore.DocumentSnapshot

class FavoritosFragment : Fragment() {
    private var _binding: FragmentFavoritosBinding? = null
    private var consulta = 0
    private var cursor: DocumentSnapshot? = null
    private var temMais = false
    private var carregando = false
    private var alterando = false
    private val ids = mutableSetOf<String>()
    private val botoes = mutableListOf<Button>()

    // Prepara a lista e permite atualizar ou continuar a consulta dos favoritos.
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val tela = FragmentFavoritosBinding.inflate(inflater, container, false)
        _binding = tela
        tela.btnAtualizar.setOnClickListener { if (!carregando) carregar() }
        tela.btnMais.setOnClickListener { if (!carregando && temMais) carregarPagina(consulta) }
        return tela.root
    }

    // Reconsulta marcações ao voltar dos detalhes e retoma uma remoção interrompida pela rotação.
    override fun onResume() {
        super.onResume()
        val pendente = Interesses.alteracaoPendente
        if (pendente != null && pendente.uid == Eventos.usuarioId) observarAlteracao() else carregar()
    }

    // Reinicia a paginação com o estado atual da conta autenticada.
    private fun carregar() {
        val tela = _binding ?: return
        consulta++
        alterando = false
        ids.clear()
        botoes.clear()
        cursor = null
        temMais = false
        tela.listaEventos.removeAllViews()
        carregarPagina(consulta)
    }

    // Recupera uma página e só informa lista vazia quando não restam páginas para consultar.
    private fun carregarPagina(numero: Int) {
        val tela = _binding ?: return
        val uid = Eventos.usuarioId
        atualizarCarregamento(true)
        tela.tvStatus.setText(R.string.loading)
        Interesses.listar(cursor).addOnCompleteListener { tarefa ->
            if (_binding !== tela || numero != consulta || uid != Eventos.usuarioId) return@addOnCompleteListener
            if (tarefa.isSuccessful) {
                val pagina = tarefa.result
                cursor = pagina.ultimoDocumento
                temMais = pagina.temMais
                pagina.favoritos.forEach { adicionarCard(it) }
                tela.tvStatus.text = when {
                    ids.isEmpty() && temMais -> getString(R.string.favorites_not_yet)
                    ids.isEmpty() -> getString(R.string.favorites_empty)
                    else -> getString(R.string.favorites_count, ids.size)
                }
            } else tela.tvStatus.setText(mensagemErroInteresse(tarefa.exception, R.string.favorites_error))
            atualizarCarregamento(false)
        }
    }

    // Apresenta favoritos com capa arredondada, categoria e preço, preservando referências indisponíveis.
    private fun adicionarCard(favorito: Favorito) {
        val tela = _binding ?: return
        if (!ids.add(favorito.eventoId)) return
        val item = ItemFavoritoBinding.inflate(layoutInflater, tela.listaEventos, false)
        val evento = favorito.evento
        if (evento == null) {
            item.cardEvento.root.visibility = View.GONE
            item.tvIndisponivel.visibility = View.VISIBLE
        } else {
            val card = item.cardEvento
            card.ivCapa.setImageResource(CapasEvento.recurso(evento.capa))
            card.tvNome.text = evento.nome
            val nomes = resources.getStringArray(R.array.event_categories)
            val indice = ValidacaoEvento.categorias.indexOf(evento.categoria)
            val preco = if (evento.valorCentavos == 0L) getString(R.string.event_free) else FormatoEvento.preco(evento.valorCentavos)
            card.tvCategoria.text = nomes.getOrElse(indice) { evento.categoria }
            card.tvPreco.text = preco
            card.tvPreco.setBackgroundResource(if (evento.valorCentavos == 0L) R.drawable.perto_pill_free else R.drawable.perto_pill)
            card.tvPreco.setTextColor(requireContext().getColor(if (evento.valorCentavos == 0L) R.color.perto_green else R.color.perto_accent))
            card.tvInfo.text = listOfNotNull(if (evento.realizado) getString(R.string.event_completed) else null, FormatoEvento.data(evento.inicioMillis), evento.endereco).joinToString("\n")
            card.tvDescricao.text = evento.descricao
            card.root.setOnClickListener {
                if (!alterando) startActivity(Intent(requireContext(), DetalhesEventoActivity::class.java)
                    .putExtra(DetalhesEventoActivity.EXTRA_EVENTO_ID, favorito.eventoId))
            }
        }
        item.btnRemover.setOnClickListener {
            if (!carregando) {
                val operacao = Interesses.alterar(favorito.eventoId, false)
                LembretesLocais.acompanhar(requireContext().applicationContext, operacao)
                observarAlteracao()
            }
        }
        botoes.add(item.btnRemover)
        tela.listaEventos.addView(item.root)
    }

    // Aguarda a remoção confirmada e só então atualiza a lista apresentada.
    private fun observarAlteracao() {
        val tela = _binding ?: return
        val operacao = Interesses.alteracaoPendente ?: return
        consulta++
        alterando = true
        atualizarCarregamento(true)
        tela.tvStatus.setText(R.string.loading)
        operacao.tarefa.addOnCompleteListener { tarefa ->
            if (_binding !== tela || operacao.uid != Eventos.usuarioId || Interesses.alteracaoPendente !== operacao) return@addOnCompleteListener
            Interesses.alteracaoPendente = null
            alterando = false
            atualizarCarregamento(false)
            if (tarefa.isSuccessful) carregar()
            else tela.tvStatus.setText(mensagemErroInteresse(tarefa.exception, R.string.interest_save_error))
        }
    }

    // Bloqueia novas operações durante consultas e alterações em andamento.
    private fun atualizarCarregamento(ativo: Boolean) {
        val tela = _binding ?: return
        carregando = ativo
        tela.btnAtualizar.isEnabled = !ativo
        tela.btnMais.isEnabled = !ativo
        tela.btnMais.visibility = if (temMais) View.VISIBLE else View.GONE
        botoes.forEach { it.isEnabled = !ativo }
    }

    // Descarta respostas antigas e libera referências à interface destruída.
    override fun onDestroyView() {
        consulta++
        botoes.clear()
        _binding = null
        super.onDestroyView()
    }
}
