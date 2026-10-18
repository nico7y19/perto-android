package br.com.vizinhas.perto.ui.eventos

import br.com.vizinhas.perto.R

object CapasEvento {
    // Resolve identificadores estáveis do banco para imagens incluídas no aplicativo.
    fun recurso(capa: String): Int = when (capa) {
        "gastronomia" -> R.drawable.capa_evento_gastronomia
        "esportes" -> R.drawable.capa_evento_esportes
        "cultura" -> R.drawable.capa_evento_cultura
        "comunidade" -> R.drawable.capa_evento_comunidade
        else -> R.drawable.capa_evento_musica
    }
}
