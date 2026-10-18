package br.com.vizinhas.perto.model

import com.google.firebase.firestore.Exclude

data class Evento(
    val nome: String = "",
    val descricao: String = "",
    val categoria: String = "",
    val endereco: String = "",
    val inicioMillis: Long = 0,
    val valorCentavos: Long = 0,
    val capa: String = "",
    val organizadorId: String = "",
    val cancelado: Boolean = false,
    @get:Exclude val id: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val realizado: Boolean = false
)
