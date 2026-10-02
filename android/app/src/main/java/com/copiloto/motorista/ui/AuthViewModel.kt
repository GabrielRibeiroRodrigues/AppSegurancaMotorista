package com.copiloto.motorista.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.copiloto.motorista.CopilotoApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as CopilotoApp).container
    private val authRepository = container.authRepository

    /** null while the stored state is loading, then true/false. */
    val isLoggedIn: StateFlow<Boolean?> = authRepository.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** LGPD consent gate: null while loading, then whether the driver accepted. */
    val consentAccepted: StateFlow<Boolean?> = container.consentStore.accepted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun acceptConsent() {
        viewModelScope.launch { container.consentStore.accept() }
    }

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun login(username: String, password: String) = submit {
        authRepository.login(username.trim(), password)
    }

    fun register(username: String, email: String, password: String) = submit {
        authRepository.register(username.trim(), password, email)
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    fun clearError() {
        _error.value = null
    }

    private fun submit(block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = block()
            if (result.isFailure) {
                _error.value = humanize(result.exceptionOrNull())
            }
            _loading.value = false
        }
    }

    private fun humanize(error: Throwable?): String = when {
        error is HttpException && error.code() == 401 -> "Usuário ou senha inválidos."
        error is HttpException && error.code() == 400 ->
            "Não foi possível cadastrar. O usuário pode já existir ou a senha é muito fraca."
        else -> "Falha de conexão. Verifique sua internet e o endereço do servidor."
    }
}
