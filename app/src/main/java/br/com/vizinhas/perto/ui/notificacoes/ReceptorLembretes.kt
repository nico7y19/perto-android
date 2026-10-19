package br.com.vizinhas.perto.ui.notificacoes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReceptorLembretes : BroadcastReceiver() {
    // Entrega o alarme ou restaura os horários usando somente os dados locais.
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            LembretesLocais.ACAO -> LembretesLocais.notificar(context, intent)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> LembretesLocais.reagendar(context)
        }
    }
}
