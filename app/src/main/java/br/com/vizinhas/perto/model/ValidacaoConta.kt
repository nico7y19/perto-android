package br.com.vizinhas.perto.model

data class ErrosLogin(val emailInvalido: Boolean, val senhaAusente: Boolean)

object ValidacaoConta {
    // Verifica os dois campos sem alterar a senha nem aplicar políticas de criação ao login.
    fun errosLogin(email: String, senha: String): ErrosLogin =
        ErrosLogin(!emailValido(email.trim()), senha.isEmpty())

    // Aplica o tamanho mínimo local; políticas adicionais são verificadas pelo Firebase.
    fun senhaValida(senha: String): Boolean = senha.length >= 6

    // Confere o formato básico do e-mail antes de enviar ao Firebase.
    fun emailValido(email: String): Boolean =
        email.length <= 254 && Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email)

    // Mantém os limites do formulário alinhados às regras publicadas do Firestore.
    fun perfilValido(usuario: Usuario): Boolean =
        usuario.nome.length in 2..120 && emailValido(usuario.email) &&
            usuario.endereco.length <= 300 && usuario.tipo in listOf("usuario", "organizador") &&
            usuario.cnpj.length <= 18 && usuario.telefone.length <= 20 &&
            (usuario.tipo == "organizador" || (usuario.cnpj.isEmpty() && usuario.telefone.isEmpty()))
}
