package br.com.vizinhas.perto.model

data class Usuario(
    val nome: String = "",
    val email: String = "",
    val tipo: String = "usuario",
    val endereco: String = "",
    val cnpj: String = "",
    val telefone: String = ""
)
