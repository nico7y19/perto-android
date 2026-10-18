package br.com.vizinhas.perto.ui

import br.com.vizinhas.perto.R
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.concurrent.TimeoutException

// Converte falhas do serviço em mensagens compreensíveis sem expor detalhes internos.
fun mensagemErroConta(erro: Exception?): Int = when {
    erro is FirebaseNetworkException || erro is TimeoutException -> R.string.error_connection
    erro is FirebaseTooManyRequestsException -> R.string.error_too_many_requests
    erro is FirebaseFirestoreException -> when (erro.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> R.string.error_permission
        FirebaseFirestoreException.Code.UNAVAILABLE -> R.string.error_connection
        else -> R.string.error_profile
    }
    erro is FirebaseAuthException -> when (erro.errorCode) {
        "ERROR_EMAIL_ALREADY_IN_USE" -> R.string.error_email_exists
        "ERROR_WEAK_PASSWORD" -> R.string.error_password_policy
        "ERROR_USER_DISABLED" -> R.string.error_disabled
        "ERROR_INVALID_EMAIL" -> R.string.error_email
        "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND", "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS" -> R.string.error_credentials
        else -> R.string.error_auth
    }
    else -> R.string.error_auth
}
