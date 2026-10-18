package br.com.vizinhas.perto.ui.principal.fragmentos

import android.os.Bundle
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import br.com.vizinhas.perto.ui.eventos.FormatoEvento
import br.com.vizinhas.perto.ui.eventos.mensagemErroEvento
import br.com.vizinhas.perto.R
import br.com.vizinhas.perto.databinding.FragmentRelatoriosBinding
import br.com.vizinhas.perto.model.Conta
import br.com.vizinhas.perto.model.Evento
import br.com.vizinhas.perto.model.Eventos
import br.com.vizinhas.perto.model.ResumoEventos
import com.google.firebase.firestore.DocumentSnapshot

class RelatoriosFragment : Fragment() {
    private var _binding: FragmentRelatoriosBinding? = null
    private val eventos = linkedMapOf<String, Evento>()
    private var cursor: DocumentSnapshot? = null
    private var temMais = false
    private var consulta = 0
    private var referencia = 0L
    private var carregando = false

    // Prepara os indicadores e permite atualizar ou continuar a consulta na própria aba.
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val tela = FragmentRelatoriosBinding.inflate(inflater, container, false)
        _binding = tela
        tela.atualizar.setOnClickListener { if (!carregando) carregar() }
        tela.mais.setOnClickListener { if (!carregando && temMais) carregarPagina(consulta) }
        return tela.root
    }

    // Reconsulta os dados ao abrir a aba ou retornar do segundo plano.
    override fun onResume() {
        super.onResume()
        carregar()
    }

    // Descarta respostas iniciadas antes de sair da tela.
    override fun onStop() {
        consulta++
        super.onStop()
    }

    // Confere o perfil de organizador e inicia uma nova leitura dos próprios eventos.
    private fun carregar() {
        val tela = _binding ?: return
        val numero = ++consulta
        val uid = Eventos.usuarioId
        referencia = System.currentTimeMillis()
        cursor = null
        temMais = false
        eventos.clear()
        tela.resumo.visibility = View.GONE
        ocupar(true)
        tela.status.setText(R.string.loading)
        Conta.carregarPerfil().addOnCompleteListener { tarefa ->
            if (_binding !== tela || numero != consulta || uid != Eventos.usuarioId) return@addOnCompleteListener
            if (!tarefa.isSuccessful || tarefa.result?.tipo != "organizador") {
                ocupar(false)
                tela.status.setText(mensagemErroEvento(tarefa.exception, R.string.report_error))
            } else carregarPagina(numero)
        }
    }

    // Acrescenta uma página sem perder os totais anteriores em caso de falha.
    private fun carregarPagina(numero: Int) {
        val tela = _binding ?: return
        val uid = Eventos.usuarioId
        ocupar(true)
        tela.status.setText(R.string.loading)
        Eventos.listarProprios(cursor, incluirCancelados = true).addOnCompleteListener { tarefa ->
            if (_binding !== tela || numero != consulta || uid != Eventos.usuarioId) return@addOnCompleteListener
            if (tarefa.isSuccessful) {
                val pagina = tarefa.result
                pagina.eventos.forEach { eventos[it.id] = it }
                cursor = pagina.ultimoDocumento
                temMais = pagina.temMais
                mostrar()
            } else tela.status.setText(mensagemErroEvento(tarefa.exception, R.string.report_error))
            ocupar(false)
        }
    }

    // Diferencia contagem parcial de consulta completa e apresenta as situações sem sobreposição.
    private fun mostrar() {
        val tela = _binding ?: return
        val resumo = ResumoEventos.calcular(eventos.values, referencia)
        tela.resumo.visibility = View.VISIBLE
        tela.resumo.text = getString(R.string.report_summary, resumo.total, resumo.agendados,
            resumo.iniciados, resumo.realizados, resumo.cancelados)
        tela.status.text = getString(if (temMais) R.string.report_partial else R.string.report_complete,
            FormatoEvento.data(referencia))
    }

    // Impede consultas simultâneas e mostra a continuação apenas quando há outra página.
    private fun ocupar(ativo: Boolean) {
        val tela = _binding ?: return
        carregando = ativo
        tela.atualizar.isEnabled = !ativo
        tela.mais.isEnabled = !ativo
        tela.mais.visibility = if (temMais) View.VISIBLE else View.GONE
    }

    // Libera a interface e impede respostas de consultas de uma aba já destruída.
    override fun onDestroyView() {
        consulta++
        _binding = null
        super.onDestroyView()
    }

}
