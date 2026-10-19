package br.com.vizinhas.perto.ui.notificacoes

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import br.com.vizinhas.perto.R
import br.com.vizinhas.perto.model.AlteracaoInteresse
import br.com.vizinhas.perto.model.Evento
import br.com.vizinhas.perto.model.Eventos
import br.com.vizinhas.perto.model.Interesses
import br.com.vizinhas.perto.model.RegraLembrete
import br.com.vizinhas.perto.ui.eventos.DetalhesEventoActivity
import br.com.vizinhas.perto.ui.eventos.FormatoEvento
import br.com.vizinhas.perto.ui.principal.PrincipalActivity
import com.google.firebase.firestore.DocumentSnapshot
import org.json.JSONObject

object LembretesLocais {
    const val ACAO = "br.com.vizinhas.perto.LEMBRETE"
    private const val CANAL = "eventos_favoritos"
    private var sincronizando: String? = null
    private var geracao = 0
    private var versao = 0

    // Acessa apenas o armazenamento do aplicativo, sem guardar referÃªncias Ã s telas.
    private fun preferencias(contexto: Context) = contexto.applicationContext.getSharedPreferences("lembretes_locais", Context.MODE_PRIVATE)

    // Recupera os eventos conhecidos; dados locais invÃ¡lidos sÃ£o tratados como uma lista vazia.
    private fun registros(contexto: Context): JSONObject = try {
        JSONObject(preferencias(contexto).getString("eventos", "{}").orEmpty())
    } catch (_: org.json.JSONException) { JSONObject() }

    // Cria o canal configurÃ¡vel pelo usuÃ¡rio nas opÃ§Ãµes do Android.
    fun criarCanal(contexto: Context) {
        val canal = NotificationChannel(CANAL, contexto.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
        canal.description = contexto.getString(R.string.reminder_description)
        contexto.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    // Confere permissÃ£o do Android e bloqueios tanto do aplicativo quanto do canal.
    fun permitidas(contexto: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val canal = contexto.getSystemService(NotificationManager::class.java).getNotificationChannel(CANAL)
        return NotificationManagerCompat.from(contexto).areNotificationsEnabled() && canal?.importance != NotificationManager.IMPORTANCE_NONE
    }

    // MantÃ©m a preferÃªncia separada dos bloqueios de permissÃ£o do sistema.
    fun ativados(contexto: Context): Boolean = preferencias(contexto).getBoolean("ativo", true)

    // Cancela os avisos da conta anterior ao sair ou trocar de usuÃ¡rio.
    fun observarConta(contexto: Context, uid: String) {
        val prefs = preferencias(contexto)
        if (prefs.getString("dono", "") == uid) return
        cancelarAgendamentos(contexto)
        prefs.edit().clear().putString("dono", uid).apply()
        geracao++
        sincronizando = null
    }

    // Ativa ou desativa os lembretes sem apagar os favoritos do Firestore.
    fun definirAtivos(contexto: Context, ativo: Boolean) {
        preferencias(contexto).edit().putBoolean("ativo", ativo).apply()
        if (ativo) reagendar(contexto) else cancelarAgendamentos(contexto)
    }

    // Identifica cada alarme pela conta e pelo evento, evitando colisÃµes entre identificadores.
    private fun alarme(contexto: Context, uid: String, id: String, criar: Boolean): PendingIntent? {
        val intent = Intent(contexto, ReceptorLembretes::class.java).setAction(ACAO)
            .setData(Uri.Builder().scheme("perto-lembrete").authority(uid).appendPath(id).build())
            .putExtra("uid", uid).putExtra("evento", id)
        return PendingIntent.getBroadcast(contexto, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or (if (criar) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE))
    }

    // Agenda um Ãºnico aviso aproximado sem exigir autorizaÃ§Ã£o especial para alarmes exatos.
    private fun agendar(contexto: Context, id: String, registro: JSONObject) {
        val uid = preferencias(contexto).getString("dono", "").orEmpty()
        if (uid.isBlank() || uid != Eventos.usuarioId || !ativados(contexto) || !permitidas(contexto) || registro.optBoolean("entregue")) return
        val horario = RegraLembrete.horario(registro.optLong("inicio"), System.currentTimeMillis()) ?: return
        val intent = alarme(contexto, uid, id, true) ?: return
        contexto.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, horario, intent)
    }

    // Atualiza o horÃ¡rio conhecido e preserva a marcaÃ§Ã£o de um lembrete jÃ¡ entregue para a mesma data.
    fun registrar(contexto: Context, evento: Evento) {
        if (evento.id.isBlank() || Eventos.usuarioId.isBlank()) return
        observarConta(contexto, Eventos.usuarioId)
        if (RegraLembrete.horario(evento.inicioMillis, System.currentTimeMillis(), evento.cancelado, evento.realizado) == null) {
            cancelar(contexto, evento.id)
            return
        }
        val todos = registros(contexto)
        val anterior = todos.optJSONObject(evento.id)
        val registro = JSONObject().put("nome", evento.nome).put("inicio", evento.inicioMillis)
            .put("entregue", anterior?.optLong("inicio") == evento.inicioMillis && (anterior?.optBoolean("entregue") == true))
        todos.put(evento.id, registro)
        preferencias(contexto).edit().putString("eventos", todos.toString()).apply()
        agendar(contexto, evento.id, registro)
    }

    // Remove o alarme, a notificaÃ§Ã£o visÃ­vel e o registro local de um favorito removido.
    fun cancelar(contexto: Context, id: String) {
        val uid = preferencias(contexto).getString("dono", "").orEmpty()
        alarme(contexto, uid, id, false)?.let {
            contexto.getSystemService(AlarmManager::class.java).cancel(it)
            it.cancel()
        }
        contexto.getSystemService(NotificationManager::class.java).cancel("$uid/$id", 0)
        val todos = registros(contexto)
        todos.remove(id)
        preferencias(contexto).edit().putString("eventos", todos.toString()).apply()
    }

    // Suspende todos os avisos locais, inclusive os que jÃ¡ estavam na barra de notificaÃ§Ãµes.
    private fun cancelarAgendamentos(contexto: Context) {
        val uid = preferencias(contexto).getString("dono", "").orEmpty()
        registros(contexto).keys().forEach { id ->
            alarme(contexto, uid, id, false)?.let {
                contexto.getSystemService(AlarmManager::class.java).cancel(it)
                it.cancel()
            }
        }
        contexto.getSystemService(NotificationManager::class.java).cancelAll()
    }

    // Restaura os alarmes conhecidos apÃ³s reiniciar o aparelho, atualizar o app ou autorizar notificaÃ§Ãµes.
    fun reagendar(contexto: Context) {
        criarCanal(contexto)
        val todos = registros(contexto)
        todos.keys().forEach { id -> todos.optJSONObject(id)?.let { agendar(contexto, id, it) } }
    }

    // Acompanha a confirmaÃ§Ã£o do interesse mesmo se a Activity for destruÃ­da durante a gravaÃ§Ã£o.
    fun acompanhar(contexto: Context, operacao: AlteracaoInteresse, evento: Evento? = null) {
        val app = contexto.applicationContext
        operacao.tarefa.addOnSuccessListener { marcado ->
            if (operacao.uid != Eventos.usuarioId) return@addOnSuccessListener
            versao++
            if (marcado && evento != null) registrar(app, evento) else if (!marcado) cancelar(app, operacao.eventoId)
        }
    }

    // LÃª todas as pÃ¡ginas de favoritos e sÃ³ elimina registros antigos apÃ³s uma consulta completa.
    fun sincronizar(contexto: Context) {
        val app = contexto.applicationContext
        val uid = Eventos.usuarioId
        if (uid.isBlank() || sincronizando == uid) return
        sincronizando = uid
        val numero = geracao
        val versaoInicial = versao
        val eventos = linkedMapOf<String, Evento>()
        // Continua a consulta sem expor listas parciais como se fossem o conjunto completo.
        fun pagina(cursor: DocumentSnapshot?) {
            Interesses.listar(cursor).addOnCompleteListener { tarefa ->
                if (uid != Eventos.usuarioId || numero != geracao) return@addOnCompleteListener
                if (!tarefa.isSuccessful) { sincronizando = null; return@addOnCompleteListener }
                val resultado = tarefa.result
                resultado.favoritos.forEach { favorito -> favorito.evento?.let { eventos[it.id] = it } }
                if (resultado.temMais) pagina(resultado.ultimoDocumento)
                else {
                    sincronizando = null
                    if (versaoInicial != versao) { sincronizar(app); return@addOnCompleteListener }
                    registros(app).keys().asSequence().toList().filter { it !in eventos }.forEach { cancelar(app, it) }
                    eventos.values.forEach { registrar(app, it) }
                }
            }
        }
        pagina(null)
    }

    // Publica um aviso local apenas para a conta atual e enquanto o evento ainda for futuro.
    fun notificar(contexto: Context, intent: Intent) {
        val uid = intent.getStringExtra("uid").orEmpty()
        val id = intent.getStringExtra("evento").orEmpty()
        if (uid.isBlank() || uid != Eventos.usuarioId || uid != preferencias(contexto).getString("dono", "") || !ativados(contexto) || !permitidas(contexto)) return
        val todos = registros(contexto)
        val registro = todos.optJSONObject(id) ?: return
        if (registro.optBoolean("entregue")) return
        if (System.currentTimeMillis() < registro.optLong("inicio") - 3_600_000L) {
            agendar(contexto, id, registro)
            return
        }
        if (registro.optLong("inicio") <= System.currentTimeMillis()) { cancelar(contexto, id); return }
        val detalhes = Intent(contexto, DetalhesEventoActivity::class.java)
            .setData(Uri.Builder().scheme("perto-evento").authority(uid).appendPath(id).build())
            .putExtra(DetalhesEventoActivity.EXTRA_EVENTO_ID, id)
        val abrir = TaskStackBuilder.create(contexto).addNextIntent(Intent(contexto, PrincipalActivity::class.java))
            .addNextIntent(detalhes).getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val aviso = NotificationCompat.Builder(contexto, CANAL).setSmallIcon(R.drawable.ic_lembrete)
            .setContentTitle(registro.optString("nome"))
            .setContentText(contexto.getString(R.string.reminder_message, FormatoEvento.data(registro.optLong("inicio"))))
            .setContentIntent(abrir).setAutoCancel(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        try {
            contexto.getSystemService(NotificationManager::class.java).notify("$uid/$id", 0, aviso)
            registro.put("entregue", true)
            preferencias(contexto).edit().putString("eventos", todos.toString()).apply()
        } catch (_: SecurityException) { /* A permissÃ£o pode ser revogada entre a verificaÃ§Ã£o e o envio. */ }
    }
}
