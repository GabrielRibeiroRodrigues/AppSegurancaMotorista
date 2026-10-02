package com.copiloto.motorista.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background

/**
 * LGPD consent gate shown before anything else. Explains, in plain Portuguese,
 * what sensitive data the app uses and why, and requires explicit acceptance.
 */
@Composable
fun ConsentScreen(onAccept: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.PrivacyTip,
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(9.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.size(12.dp))
            Column {
                Text("Privacidade", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Antes de começar, entenda e autorize o uso dos seus dados.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            "O Copiloto usa, só para as funções que você ativar:",
            style = MaterialTheme.typography.bodyMedium,
        )

        Item("Leitura das corridas", "Lê o texto das ofertas nos apps que você escolher, para calcular a rentabilidade. Nada é lido fora desses apps.")
        Item("Localização", "Enviada junto de um alerta de proteção, para a central saber onde você está.")
        Item("Câmera e microfone", "No modo proteção, transmitem vídeo e áudio ao vivo para a central durante um alerta. O dashcam grava com aviso visível.")
        Item("Conta e histórico", "Guardamos seu histórico de corridas e configurações, ligados à sua conta, para sincronizar entre aparelhos.")
        // (todos os itens usam o mesmo estilo; ícone por item é opcional)

        Text(
            "Você controla cada função nos Ajustes e pode desativá-las quando quiser. " +
                "Ao continuar, você declara ter mais de 18 anos e concorda com o tratamento " +
                "desses dados para as finalidades acima (LGPD, Lei 13.709/2018).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.size(4.dp))
        Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
            Text("Li e concordo")
        }
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun Item(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
