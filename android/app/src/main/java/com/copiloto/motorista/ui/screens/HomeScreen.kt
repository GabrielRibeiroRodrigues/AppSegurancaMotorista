package com.copiloto.motorista.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.copiloto.motorista.data.model.RideClassification
import com.copiloto.motorista.data.repository.startOfToday
import com.copiloto.motorista.service.DashcamService
import com.copiloto.motorista.service.OverlayService
import com.copiloto.motorista.ui.MainViewModel
import com.copiloto.motorista.ui.PermissionUtils
import com.copiloto.motorista.ui.UiFormat
import com.copiloto.motorista.ui.components.SectionLabel
import com.copiloto.motorista.ui.components.SettingRow
import com.copiloto.motorista.ui.components.StatTile
import com.copiloto.motorista.ui.components.StatusPill
import com.copiloto.motorista.ui.theme.StatusDanger
import com.copiloto.motorista.ui.theme.StatusPositive
import com.copiloto.motorista.ui.theme.StatusWarning

@Composable
fun HomeScreen(viewModel: MainViewModel, onMessage: (String) -> Unit = {}) {
    val context = LocalContext.current
    val history by viewModel.history.collectAsStateWithLifecycle()

    var canOverlay by remember { mutableStateOf(PermissionUtils.canDrawOverlays(context)) }
    var accessibilityOn by remember { mutableStateOf(PermissionUtils.isAccessibilityEnabled(context)) }
    var ignoringBattery by remember { mutableStateOf(PermissionUtils.isIgnoringBatteryOptimizations(context)) }
    var dashcamOn by remember { mutableStateOf(DashcamService.isRunning) }

    val dashcamPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val cameraOk = result[Manifest.permission.CAMERA] == true
        val audioOk = result[Manifest.permission.RECORD_AUDIO] == true
        if (cameraOk && audioOk) {
            DashcamService.start(context)
            dashcamOn = true
            onMessage("Gravação iniciada")
        } else {
            onMessage("Permissões de câmera/microfone necessárias")
        }
    }

    // Re-check the special permissions whenever the user returns from Settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canOverlay = PermissionUtils.canDrawOverlays(context)
                accessibilityOn = PermissionUtils.isAccessibilityEnabled(context)
                ignoringBattery = PermissionUtils.isIgnoringBatteryOptimizations(context)
                dashcamOn = DashcamService.isRunning
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val ready = canOverlay && accessibilityOn
    val todayRides = history.filter { it.createdAt >= startOfToday() }
    val todayProfit = todayRides.sumOf { it.netProfit }
    val todayCount = todayRides.size
    val greenPct = if (todayCount > 0) {
        todayRides.count { it.classification == RideClassification.GREEN.name } * 100 / todayCount
    } else {
        0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Copiloto",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Assistente do motorista",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(
                text = if (ready) "Pronto" else "Configurar",
                color = if (ready) StatusPositive else StatusWarning,
            )
        }

        // Hero / primary action
        HeroCard(
            ready = ready,
            canSimulate = canOverlay,
            onSimulate = {
                viewModel.simulateRide()
                onMessage("Corrida simulada")
            },
            onCloseCard = { OverlayService.stop(context) },
        )

        // Today's stats
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                modifier = Modifier.weight(1f),
                value = UiFormat.money(todayProfit),
                label = "Lucro hoje",
                valueColor = if (todayProfit >= 0) StatusPositive else StatusDanger,
            )
            StatTile(modifier = Modifier.weight(1f), value = todayCount.toString(), label = "Corridas")
            StatTile(
                modifier = Modifier.weight(1f),
                value = "$greenPct%",
                label = "Verdes",
                valueColor = StatusPositive,
            )
        }

        // Dashcam
        SectionLabel("Segurança")
        DashcamCard(
            recording = dashcamOn,
            onToggle = {
                if (dashcamOn) {
                    DashcamService.stop(context)
                    dashcamOn = false
                    onMessage("Gravação encerrada")
                } else if (hasDashcamPermissions(context)) {
                    DashcamService.start(context)
                    dashcamOn = true
                    onMessage("Gravação iniciada")
                } else {
                    dashcamPermissionLauncher.launch(dashcamPermissions())
                }
            },
        )

        // Configuration (compact)
        SectionLabel("Configuração")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                SettingRow(
                    icon = Icons.Filled.Layers,
                    title = "Cartão flutuante",
                    subtitle = "Desenha a recomendação sobre os apps",
                    granted = canOverlay,
                    actionLabel = "Conceder",
                    onAction = { context.startActivity(PermissionUtils.overlaySettingsIntent(context)) },
                )
                SettingRow(
                    icon = Icons.Filled.Accessibility,
                    title = "Leitura de corridas",
                    subtitle = "Lê as ofertas na tela (Acessibilidade)",
                    granted = accessibilityOn,
                    actionLabel = "Ativar",
                    onAction = { context.startActivity(PermissionUtils.accessibilitySettingsIntent()) },
                )
                SettingRow(
                    icon = Icons.Filled.BatteryFull,
                    title = "Bateria",
                    subtitle = "Evita o app ser encerrado em segundo plano",
                    granted = ignoringBattery,
                    actionLabel = "Ajustar",
                    onAction = {
                        context.startActivity(PermissionUtils.ignoreBatteryOptimizationIntent(context))
                    },
                )
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun HeroCard(
    ready: Boolean,
    canSimulate: Boolean,
    onSimulate: () -> Unit,
    onCloseCard: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.DirectionsCar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (ready) "Tudo pronto para rodar" else "Quase lá",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        if (ready) {
                            "O Copiloto avalia cada corrida automaticamente."
                        } else {
                            "Conceda as permissões abaixo para começar."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
            Button(
                onClick = onSimulate,
                enabled = canSimulate,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Simular corrida")
            }
            if (!canSimulate) {
                Text(
                    "Conceda o cartão flutuante para simular.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            } else {
                TextButton(onClick = onCloseCard, modifier = Modifier.fillMaxWidth()) {
                    Text("Fechar cartão aberto")
                }
            }
        }
    }
}

@Composable
private fun DashcamCard(recording: Boolean, onToggle: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val tint = if (recording) StatusDanger else MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Videocam, contentDescription = null, tint = tint)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Dashcam", fontWeight = FontWeight.SemiBold)
                Text(
                    if (recording) "Gravando vídeo e áudio" else "Grava a viagem (até 5 GB)",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (recording) StatusDanger else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (recording) {
                OutlinedButton(
                    onClick = onToggle,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDanger),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Parar")
                }
            } else {
                Button(onClick = onToggle) {
                    Text("Gravar")
                }
            }
        }
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
