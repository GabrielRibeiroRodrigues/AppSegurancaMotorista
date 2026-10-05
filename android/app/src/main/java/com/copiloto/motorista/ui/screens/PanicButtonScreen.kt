package com.copiloto.motorista.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.copiloto.motorista.bluetooth.BleConnectionState
import com.copiloto.motorista.ui.PanicButtonViewModel
import com.copiloto.motorista.ui.components.SectionLabel

@Composable
fun PanicButtonScreen(viewModel: PanicButtonViewModel, onBack: () -> Unit) {
    val state by viewModel.connectionState.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val scanResults by viewModel.scanResults.collectAsStateWithLifecycle()

    var permissionsGranted by remember { mutableStateOf(viewModel.hasScanPermission() && viewModel.hasConnectPermission()) }
    var scanning by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var lastTestReceived by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        permissionsGranted = result.values.all { it }
        if (permissionsGranted) {
            scanning = true
            viewModel.startScan()
        }
    }

    fun requestOrScan() {
        if (viewModel.hasScanPermission() && viewModel.hasConnectPermission()) {
            permissionsGranted = true
            scanning = true
            viewModel.startScan()
        } else {
            permissionLauncher.launch(blePermissions())
        }
    }

    // Collect physical presses while testing.
    LaunchedEffect(testing) {
        viewModel.setTestMode(testing)
        if (testing) {
            lastTestReceived = false
            viewModel.panicEvents.collect { lastTestReceived = true }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Text(
                "Botão de pânico",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        Text(
            "Conecte o botão físico instalado no veículo (ESP32) por Bluetooth. " +
                "Com ele, você aciona um alerta sem precisar pegar o celular.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when {
            !viewModel.isBleSupported() -> InfoCard("Este aparelho não suporta Bluetooth LE, necessário para o botão.")
            !viewModel.isBluetoothOn() -> InfoCard("Ligue o Bluetooth do celular para usar o botão de pânico.")
        }

        if (config.isPaired) {
            StatusCard(
                state = state,
                deviceName = config.deviceName ?: config.deviceAddress ?: "Botão",
                enabled = config.enabled,
                onToggle = viewModel::setEnabled,
            )

            // On-screen test: press the physical button and confirm it is received.
            SectionLabel("Testar")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (testing)
                            "Pressione o botão físico agora. Neste modo, nenhum alerta real é enviado."
                        else
                            "Entre no modo de teste e pressione o botão para confirmar que ele está funcionando.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (testing && lastTestReceived) {
                        Text(
                            "✓ Acionamento recebido!",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    OutlinedButton(
                        onClick = { testing = !testing; if (!testing) lastTestReceived = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (testing) "Encerrar teste" else "Testar botão")
                    }
                }
            }

            OutlinedButton(onClick = viewModel::unpair, modifier = Modifier.fillMaxWidth()) {
                Text("Esquecer este dispositivo")
            }
        } else {
            SectionLabel("Parear")
            Button(onClick = { requestOrScan() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(if (scanning) "Procurando…" else "Procurar botão")
            }

            if (scanning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Deixe o botão ligado e por perto.", style = MaterialTheme.typography.labelMedium)
                }
            }

            scanResults.forEach { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scanning = false
                            viewModel.pairAndArm(device)
                        },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(device.name ?: "Dispositivo BLE", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${device.address}  ·  ${device.rssi} dBm",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (scanning && scanResults.isEmpty()) {
                Text(
                    "Nenhum botão encontrado ainda…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    state: BleConnectionState,
    deviceName: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val (label, color) = when (state) {
        BleConnectionState.CONNECTED -> "Conectado e pronto" to MaterialTheme.colorScheme.primary
        BleConnectionState.CONNECTING -> "Conectando…" to MaterialTheme.colorScheme.tertiary
        BleConnectionState.BLUETOOTH_OFF -> "Bluetooth desligado" to MaterialTheme.colorScheme.error
        BleConnectionState.NO_PERMISSION -> "Falta permissão de Bluetooth" to MaterialTheme.colorScheme.error
        BleConnectionState.UNSUPPORTED -> "Sem suporte a BLE" to MaterialTheme.colorScheme.error
        BleConnectionState.DISCONNECTED -> "Desconectado" to MaterialTheme.colorScheme.error
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(deviceName, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(label, color = color, fontWeight = FontWeight.Medium)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Monitorar botão", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

/** BLE runtime permissions by SDK level. */
private fun blePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
