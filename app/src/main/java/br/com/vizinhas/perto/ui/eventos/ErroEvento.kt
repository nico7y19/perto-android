package br.com.vizinhas.perto.ui.eventos

import br.com.vizinhas.perto.R
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.concurrent.TimeoutException

// Explica falhas de eventos sem reutilizar mensagens específicas de perfis.
fun mensagemErroEvento(erro: Exception?, padrao: Int): Int = when {
    erro is TimeoutException || erro is FirebaseNetworkException -> R.string.event_no_connection
    erro is FirebaseFirestoreException && erro.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> R.string.event_error_permission
    erro is FirebaseFirestoreException && erro.code == FirebaseFirestoreException.Code.UNAVAILABLE -> R.string.event_no_connection
    else -> padrao
}
