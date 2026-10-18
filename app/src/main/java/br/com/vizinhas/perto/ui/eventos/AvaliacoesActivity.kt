package br.com.vizinhas.perto.ui.eventos

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import br.com.vizinhas.perto.R
import br.com.vizinhas.perto.databinding.ActivityAvaliacoesBinding
import br.com.vizinhas.perto.model.Avaliacao
import br.com.vizinhas.perto.model.Avaliacoes
import br.com.vizinhas.perto.model.Conta
import br.com.vizinhas.perto.model.Eventos
import br.com.vizinhas.perto.model.GravacaoAvaliacao
import br.com.vizinhas.perto.model.ValidacaoAvaliacao
import com.google.firebase.firestore.DocumentSnapshot

class AvaliacoesActivity : AppCompatActivity() {
    private lateinit var tela: ActivityAvaliacoesBinding
    private var consulta = 0
    private var consultaLista = 0
    private var preenchido = false
    private var podeAvaliar = false
    private var salvando = false
    private var carregandoLista = false
    private var cursor: DocumentSnapshot? = null
    private var temMais = false
    private val eventoId get() = intent.getStringExtra(DetalhesEventoActivity.EXTRA_EVENTO_ID).orEmpty()

    // Prepara o formulário, a lista de avaliações e os espaços das barras e do teclado.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tela = ActivityAvaliacoesBinding.inflate(layoutInflater)
        setContentView(tela.root)
        ViewCompat.setOnApplyWindowInsetsListener(tela.root) { view, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }
        preenchido = savedInstanceState?.getBoolean("preenchido") == true
        tela.nota.adapter = ArrayAdapter.createFromResource(this, R.array.review_scores, R.layout.spinner_perto)
        tela.voltar.setOnClickListener { finish() }
        tela.tentar.setOnClickListener { carregar() }
        tela.salvar.setOnClickListener { salvar() }
        tela.mais.setOnClickListener { if (!carregandoLista && temMais) listar() }
    }

    // Revalida o acesso e reconecta operações ao retornar à tela.
    override fun onStart() {
        super.onStart()
        carregar()
    }

    // Invalida respostas de consultas anteriores ao deixar a tela.
    override fun onStop() {
        consulta++
        consultaLista++
        super.onStop()
    }

    // Abre o formulário e a consulta em eventos existentes não cancelados, independentemente da data.
    private fun carregar() {
        val numero = ++consulta
        val uid = Eventos.usuarioId
        salvando = false
        tela.formulario.visibility = View.GONE
        tela.painelLista.visibility = View.GONE
        tela.tentar.visibility = View.GONE
        tela.status.setText(R.string.loading)
        Eventos.buscar(eventoId).onSuccessTask { evento ->
            check(evento != null && !evento.cancelado)
            Conta.carregarPerfil().continueWith { tarefa ->
                val usuario = tarefa.getResult(Exception::class.java) ?: throw IllegalStateException()
                ValidacaoAvaliacao.podeAvaliar(evento, usuario, uid)
            }
        }.addOnCompleteListener(this) { tarefa ->
            if (numero != consulta || uid != Eventos.usuarioId) return@addOnCompleteListener
            if (!tarefa.isSuccessful) {
                tela.status.setText(mensagemErroEvento(tarefa.exception, R.string.review_unavailable))
                tela.tentar.visibility = View.VISIBLE
                return@addOnCompleteListener
            }
            podeAvaliar = tarefa.result
            tela.status.setText(if (podeAvaliar) R.string.review_hint else R.string.review_read_only)
            tela.formulario.visibility = if (podeAvaliar) View.VISIBLE else View.GONE
            tela.painelLista.visibility = View.VISIBLE
            reiniciarLista()
            val operacao = Avaliacoes.gravacaoPendente
            if (operacao != null && operacao.uid == uid && operacao.eventoId == eventoId) observar(operacao)
            else if (podeAvaliar) carregarMinha(numero, uid)
        }
    }

    // Recupera a avaliação anterior sem substituir uma edição restaurada pelo Android.
    private fun carregarMinha(numero: Int, uid: String) {
        ocupar(true)
        Avaliacoes.minha(eventoId).addOnCompleteListener(this) { tarefa ->
            if (numero != consulta || uid != Eventos.usuarioId) return@addOnCompleteListener
            if (!tarefa.isSuccessful) {
                tela.status.setText(mensagemErroEvento(tarefa.exception, R.string.review_error))
                tela.tentar.visibility = View.VISIBLE
                return@addOnCompleteListener
            }
            if (!preenchido) {
                val avaliacao = tarefa.result
                tela.nota.setSelection(avaliacao?.nota?.toInt()?.coerceIn(0, 5) ?: 0)
                tela.comentario.setText(avaliacao?.comentario.orEmpty())
                preenchido = true
            }
            ocupar(false)
        }
    }

    // Valida a nota e inicia a gravação da própria avaliação.
    private fun salvar() {
        if (!podeAvaliar || salvando) return
        val avaliacao = Avaliacao(tela.nota.selectedItemPosition.toLong(), tela.comentario.text.toString().trim())
        if (!ValidacaoAvaliacao.valida(avaliacao)) {
            tela.status.setText(R.string.review_invalid)
            return
        }
        observar(Avaliacoes.salvar(eventoId, avaliacao))
    }

    // Retorna aos detalhes após a confirmação para exibir a avaliação salva na lista atualizada.
    private fun observar(operacao: GravacaoAvaliacao) {
        val numero = consulta
        ocupar(true)
        tela.status.setText(R.string.loading)
        operacao.tarefa.addOnCompleteListener(this) { tarefa ->
            if (numero != consulta || operacao.uid != Eventos.usuarioId) return@addOnCompleteListener
            if (Avaliacoes.gravacaoPendente === operacao) Avaliacoes.gravacaoPendente = null
            ocupar(false)
            tela.status.setText(if (tarefa.isSuccessful) R.string.review_saved
                else mensagemErroEvento(tarefa.exception, R.string.review_error))
            if (tarefa.isSuccessful) {
                Toast.makeText(this, R.string.review_saved, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    // Bloqueia os campos enquanto a avaliação é consultada ou gravada.
    private fun ocupar(ativo: Boolean) {
        salvando = ativo
        listOf(tela.nota, tela.comentario, tela.salvar).forEach { it.isEnabled = !ativo && podeAvaliar }
    }

    // Descarta páginas antigas antes de consultar as avaliações novamente.
    private fun reiniciarLista() {
        consultaLista++
        cursor = null
        temMais = false
        tela.lista.removeAllViews()
        listar()
    }

    // Acrescenta uma página e oferece nova tentativa se a consulta falhar.
    private fun listar() {
        val numero = consulta
        val numeroLista = consultaLista
        val uid = Eventos.usuarioId
        carregandoLista = true
        tela.mais.isEnabled = false
        tela.statusLista.setText(R.string.loading)
        Avaliacoes.listar(eventoId, cursor).addOnCompleteListener(this) { tarefa ->
            if (numero != consulta || numeroLista != consultaLista || uid != Eventos.usuarioId) return@addOnCompleteListener
            carregandoLista = false
            tela.mais.isEnabled = true
            if (tarefa.isSuccessful) {
                val pagina = tarefa.result
                cursor = pagina.cursor
                temMais = pagina.temMais
                pagina.avaliacoes.forEach { avaliacao ->
                    val item = TextView(this)
                    item.setTextColor(getColor(R.color.white))
                    item.text = getString(R.string.review_item, avaliacao.nota, avaliacao.comentario)
                    val margem = (16 * resources.displayMetrics.density).toInt()
                    item.setPadding(0, margem, 0, margem)
                    tela.lista.addView(item)
                }
                tela.statusLista.setText(if (tela.lista.childCount == 0) R.string.review_empty else R.string.review_list)
            } else {
                tela.statusLista.setText(mensagemErroEvento(tarefa.exception, R.string.review_error))
                tela.tentar.visibility = View.VISIBLE
            }
            tela.mais.visibility = if (temMais) View.VISIBLE else View.GONE
        }
    }

    // Preserva a edição local ao recriar a Activity.
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("preenchido", preenchido)
        super.onSaveInstanceState(outState)
    }
}
