package br.com.vizinhas.perto.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidacaoContaTest {
    // Os dois campos devem ser verificados mesmo quando ambos estão incorretos.
    @Test
    fun identificaErrosDoLoginPorCampo() {
        assertEquals(ErrosLogin(true, true), ValidacaoConta.errosLogin("", ""))
        assertEquals(ErrosLogin(true, false), ValidacaoConta.errosLogin("email-incorreto", "123456"))
        assertEquals(ErrosLogin(false, true), ValidacaoConta.errosLogin("ana@exemplo.com", ""))
    }

    // O login aceita espaços externos no e-mail e deixa a verificação da senha ao Firebase.
    @Test
    fun naoConfundePoliticaDeCadastroComLogin() {
        assertEquals(ErrosLogin(false, false), ValidacaoConta.errosLogin("  ana@exemplo.com  ", "123"))
        assertEquals(ErrosLogin(false, false), ValidacaoConta.errosLogin("ana@exemplo.com", " "))
    }

    // Aceita o limite mínimo e rejeita senhas menores sem alterar espaços digitados.
    @Test
    fun validaTamanhoMinimoDaSenha() {
        assertFalse(ValidacaoConta.senhaValida("12345"))
        assertTrue(ValidacaoConta.senhaValida("123456"))
    }

    // Rejeita e-mails incompletos e espaços antes de consultar o serviço.
    @Test
    fun rejeitaEmailsInvalidos() {
        listOf("", "pessoa", "pessoa@", "@dominio.com", "pessoa @dominio.com").forEach {
            assertFalse(ValidacaoConta.emailValido(it))
        }
        assertTrue(ValidacaoConta.emailValido("pessoa+evento@dominio.com.br"))
    }

    // Garante os limites de nome e endereço aceitos pelas regras do perfil.
    @Test
    fun respeitaLimitesDoPerfil() {
        val usuario = Usuario(nome = "Ana", email = "ana@exemplo.com")
        assertTrue(ValidacaoConta.perfilValido(usuario))
        assertFalse(ValidacaoConta.perfilValido(usuario.copy(nome = "A")))
        assertTrue(ValidacaoConta.perfilValido(usuario.copy(nome = "a".repeat(120))))
        assertFalse(ValidacaoConta.perfilValido(usuario.copy(nome = "a".repeat(121))))
        assertTrue(ValidacaoConta.perfilValido(usuario.copy(endereco = "a".repeat(300))))
        assertFalse(ValidacaoConta.perfilValido(usuario.copy(endereco = "a".repeat(301))))
    }

    // Impede perfis comuns com dados exclusivos de organizador ou tipos desconhecidos.
    @Test
    fun restringeTipoEDadosDoOrganizador() {
        val usuario = Usuario(nome = "Ana", email = "ana@exemplo.com")
        assertFalse(ValidacaoConta.perfilValido(usuario.copy(tipo = "admin")))
        assertFalse(ValidacaoConta.perfilValido(usuario.copy(cnpj = "123")))
        assertFalse(ValidacaoConta.perfilValido(usuario.copy(telefone = "123")))
        assertTrue(ValidacaoConta.perfilValido(usuario.copy(tipo = "organizador")))
        assertFalse(ValidacaoConta.perfilValido(usuario.copy(tipo = "organizador", telefone = "1".repeat(21))))
        assertFalse(ValidacaoConta.perfilValido(usuario.copy(tipo = "organizador", cnpj = "1".repeat(19))))
    }
}
