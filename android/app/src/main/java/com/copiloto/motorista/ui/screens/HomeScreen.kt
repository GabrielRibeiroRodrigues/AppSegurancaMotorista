package com.copiloto.motorista.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import com.copiloto.motorista.service.DashcamService
import com.copiloto.motorista.service.OverlayService
import com.copiloto.motorista.ui.MainViewModel
import com.copiloto.motorista.ui.PermissionUtils

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val profile by viewModel.profile.collectAsStateWithLifecycle()

    var canOverlay by remember { mutableStateOf(PermissionUtils.canDrawOverlays(context)) }
    var accessibilityOn by remember { mutableStateOf(PermissionUtils.isAccessibilityEnabled(context)) }
    var dashcamOn by remember { mutableStateOf(DashcamService.isRunning) }

    val dashcamPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val cameraOk = result[Manifest.permission.CAMERA] == true
        val audioOk = result[Manifest.permission.RECORD_AUDIO] == true
        if (cameraOk && audioOk) {
            DashcamService.start(context)
            dashcamOn = true
        }
    }

    // Re-check the special permissions whenever the user returns from Settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canOverlay = PermissionUtils.canDrawOverlays(context)
                accessibilityOn = PermissionUtils.isAccessibilityEnabled(context)
                dashcamOn = DashcamService.isRunning
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Copiloto para Motoristas",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Avalia a rentabilidade de cada corrida em tempo real e mostra um cartão flutuante com a recomendação.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PermissionCard(
            title = "Cartão flutuante",
            description = "Permite desenhar o cartão de rentabilidade sobre os apps de corrida.",
            granted = canOverlay,
            actionLabel = "Conceder",
            onAction = { context.startActivity(PermissionUtils.overlaySettingsIntent(context)) },
        )

        PermissionCard(
            title = "Leitura de corridas (Acessibilidade)",
            description = "Permite ler as ofertas exibidas pelos apps de corrida para calcular a rentabilidade.",
            granted = accessibilityOn,
            actionLabel = "Ativar",
            onAction = { context.startActivity(PermissionUtils.accessibilitySettingsIntent()) },
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Simulador de corrida", fontWeight = FontWeight.SemiBold)
                Text(
                    "Gera uma oferta fictícia e dispara o cartão flutuante e a voz exatamente como uma corrida real.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { viewModel.simulateRide() },
                    enabled = canOverlay,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Simular corrida")
                }
                if (!canOverlay) {
                    Text(
                        "Conceda a permissão do cartão flutuante para simular.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedButton(
                    onClick = { OverlayService.stop(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Fechar cartão")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dashcam de segurança", fontWeight = FontWeight.SemiBold)
                Text(
                    "Grava a viagem (vídeo e áudio) para a sua proteção, com uma notificação visível de que a gravação está ativa. Os arquivos ficam só no seu aparelho, com limite de 5 GB (os mais antigos são apagados automaticamente).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (dashcamOn) {
                    Button(
                        onClick = {
                            DashcamService.stop(context)
                            dashcamOn = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Parar gravação")
                    }
                } else {
                    Button(
                        onClick = {
                            if (hasDashcamPermissions(context)) {
                                DashcamService.start(context)
                                dashcamOn = true
                            } else {
                                dashcamPermissionLauncher.launch(dashcamPermissions())
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Gravar viagem")
                    }
                }
            }
        }

        Text(
            text = "Voz: " + if (profile.voiceEnabled) "ativada" else "desativada",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
    }
}

/** Camera + microphone must be granted before the dashcam can record. */
private fun hasDashcamPermissions(context: android.content.Context): Boolean {
    val camera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
    val audio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
    return camera == PackageManager.PERMISSION_GRANTED && audio == PackageManager.PERMISSION_GRANTED
}

/** Camera + microphone, plus notifications on Android 13+ so the "recording" banner shows. */
private fun dashcamPermissions(): Array<String> {
    val base = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        (base + Manifest.permission.POST_NOTIFICATIONS).toTypedArray()
    } else {
        base.toTypedArray()
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = null,
                tint = if (granted) Color(0xFF22C55E) else Color(0xFFF59E0B),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!granted) {
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
