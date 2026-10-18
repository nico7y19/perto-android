package br.com.vizinhas.perto.model

import com.google.firebase.firestore.DocumentSnapshot

data class PaginaEventos(
    val eventos: List<Evento>,
    val ultimoDocumento: DocumentSnapshot?,
    val temMais: Boolean
)
