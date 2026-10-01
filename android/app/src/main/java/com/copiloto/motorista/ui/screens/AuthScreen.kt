package com.copiloto.motorista.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.copiloto.motorista.ui.AuthViewModel

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    var isRegister by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Copiloto para Motoristas",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            if (isRegister) "Crie sua conta" else "Entre na sua conta",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it; viewModel.clearError() },
            label = { Text("Usuário") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (isRegister) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; viewModel.clearError() },
                label = { Text("E-mail (opcional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; viewModel.clearError() },
            label = { Text("Senha") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = {
                if (isRegister) viewModel.register(username, email, password)
                else viewModel.login(username, password)
            },
            enabled = !loading && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(16.dp)
                        .padding(end = 4.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text(if (isRegister) "Cadastrar" else "Entrar")
        }

        TextButton(
            onClick = { isRegister = !isRegister; viewModel.clearError() },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                if (isRegister) "Já tenho conta — entrar" else "Não tem conta? Cadastre-se",
            )
        }
    }
}
