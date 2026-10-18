package br.com.vizinhas.perto.ui.eventos

import br.com.vizinhas.perto.R
import com.google.firebase.firestore.FirebaseFirestoreException

// Distingue a permissão dos interesses das regras de acesso aos eventos.
fun mensagemErroInteresse(erro: Exception?, padrao: Int): Int =
    if (erro is FirebaseFirestoreException && erro.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
        R.string.interest_permission else mensagemErroEvento(erro, padrao)
