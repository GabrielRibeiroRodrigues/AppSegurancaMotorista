package com.copiloto.motorista.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.copiloto.motorista.data.model.DriverProfile
import com.copiloto.motorista.data.model.MonitoredApp
import com.copiloto.motorista.service.ParserDumpStore
import com.copiloto.motorista.service.RideAccessibilityService
import com.copiloto.motorista.ui.MainViewModel
import com.copiloto.motorista.ui.UiFormat
import com.copiloto.motorista.ui.components.SectionLabel
import com.copiloto.motorista.ui.theme.StatusDanger

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onLogout: () -> Unit = {},
    onMessage: (String) -> Unit = {},
    onOpenPanicButton: () -> Unit = {},
) {
    val context = LocalContext.current
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    var dumpCount by remember { mutableStateOf(ParserDumpStore.count(context)) }
    var dumpRequested by remember { mutableStateOf(false) }

    var fuelPrice by remember { mutableStateOf("") }
    var kmPerLiter by remember { mutableStateOf("") }
    var maintenance by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var minimum by remember { mutableStateOf("") }
    var targetHour by remember { mutableStateOf("") }
    var dailyGoal by remember { mutableStateOf("") }
    var voice by remember { mutableStateOf(true) }

    val blacklist by viewModel.blacklist.collectAsStateWithLifecycle()
    var newKeyword by remember { mutableStateOf("") }

    val monitoredApps by viewModel.monitoredApps.collectAsStateWithLifecycle()
    var showAppPicker by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<MonitoredApp>?>(null) }

    val triggerPhrase by viewModel.triggerPhrase.collectAsStateWithLifecycle()
    var phraseField by remember { mutableStateOf("") }
    LaunchedEffect(triggerPhrase) { if (phraseField.isEmpty()) phraseField = triggerPhrase }

    // Populate the fields once the stored profile is loaded.
    LaunchedEffect(profile) {
        fuelPrice = profile.fuelPricePerLiter.toString()
        kmPerLiter = profile.kmPerLiter.toString()
        maintenance = profile.maintenanceCostPerKm.toString()
        target = profile.targetPerKm.toString()
        minimum = profile.minimumPerKm.toString()
        targetHour = profile.targetPerHour.toString()
        dailyGoal = profile.dailyGoal.toString()
        voice = profile.voiceEnabled
    }

    val previewCostPerKm = buildPreviewProfile(fuelPrice, kmPerLiter, maintenance).costPerKm

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Group("Custos do veículo") {
            NumberField("Combustível (R$/litro)", fuelPrice) { fuelPrice = it }
            NumberField("Consumo (km/litro)", kmPerLiter) { kmPerLiter = it }
            NumberField("Manutenção (R$/km)", maintenance) { maintenance = it }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Custo estimado por km", style = MaterialTheme.typography.bodyMedium)
                Text(
                    UiFormat.money(previewCostPerKm),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Group("Metas") {
            NumberField("Verde a partir de (R$/km)", target) { target = it }
            NumberField("Vermelho abaixo de (R$/km)", minimum) { minimum = it }
            NumberField("Mínimo por hora p/ verde (R$/h)", targetHour) { targetHour = it }
            NumberField("Meta de ganho diário (R$)", dailyGoal) { dailyGoal = it }
        }

        Group("Voz") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Anunciar corridas por voz", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Fala a classificação e o lucro de cada corrida.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = voice, onCheckedChange = { voice = it })
            }
        }

        Button(
            onClick = {
                viewModel.saveProfile(
                    DriverProfile(
                        fuelPricePerLiter = fuelPrice.toDoubleFlexible(),
                        kmPerLiter = kmPerLiter.toDoubleFlexible().coerceAtLeast(0.1),
                        maintenanceCostPerKm = maintenance.toDoubleFlexible(),
                        targetPerKm = target.toDoubleFlexible(),
                        minimumPerKm = minimum.toDoubleFlexible(),
                        targetPerHour = targetHour.toDoubleFlexible(),
                        dailyGoal = dailyGoal.toDoubleFlexible(),
                        voiceEnabled = voice,
                    ),
                )
                onMessage("Configurações salvas")
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Salvar alterações")
        }

        Group("Botão de pânico (ESP32)") {
            Text(
                "Pareie o botão físico instalado no veículo para acionar um alerta sem pegar o celular.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenPanicButton, modifier = Modifier.fillMaxWidth()) {
                Text("Configurar botão de pânico")
            }
        }

        Group("Proteção") {
            Text(
                "Frase que, dita em voz alta com o modo proteção ligado, dispara um alerta silencioso para a central.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = phraseField,
                onValueChange = { phraseField = it },
                label = { Text("Frase-gatilho") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    viewModel.setTriggerPhrase(phraseField)
                    onMessage("Frase-gatilho salva")
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Salvar frase")
            }
        }

        Group("Apps monitorados") {
            Text(
                "O Copiloto já lê Uber, 99 e inDrive. Adicione outros apps de corrida ou entrega (ex: Uby Muzambinho) para ler as ofertas deles também.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Built-ins are always on.
            listOf("Uber", "99", "inDrive").forEach { name ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, modifier = Modifier.weight(1f))
                    Text(
                        "Padrão",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (monitoredApps.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                monitoredApps.forEach { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { viewModel.removeMonitoredApp(app.packageName) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remover ${app.label}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    installedApps = null
                    showAppPicker = true
                    viewModel.loadInstalledApps { installedApps = it }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                Text("Adicionar app")
            }
        }

        Group("Zonas de risco") {
            Text(
                "Se o destino contiver uma destas palavras, a corrida é marcada como área de risco e avisada por voz — ignorando o cálculo de lucro.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = newKeyword,
                    onValueChange = { newKeyword = it },
                    label = { Text("Ex: Complexo") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val keyword = newKeyword.trim()
                        if (keyword.isNotEmpty()) {
                            viewModel.addKeyword(keyword)
                            newKeyword = ""
                            onMessage("Zona de risco adicionada")
                        }
                    },
                ) {
                    Text("Adicionar")
                }
            }
            if (blacklist.isEmpty()) {
                Text(
                    "Nenhuma zona cadastrada.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                blacklist.forEach { keyword ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(keyword, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.removeKeyword(keyword) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remover $keyword",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Group("Depuração do parser") {
            Text(
                "Quando a leitura de uma corrida falha, o app salva um \"dump\" da tela para ajudar a ajustar o parser.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Dumps salvos: $dumpCount", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(
                onClick = {
                    RideAccessibilityService.requestDump()
                    dumpRequested = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Gerar dump da próxima tela")
            }
            if (dumpRequested) {
                Text(
                    "Abra o app de corrida: a próxima tela será salva.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            OutlinedButton(
                onClick = {
                    ParserDumpStore.clear(context)
                    dumpCount = 0
                    dumpRequested = false
                    onMessage("Dumps apagados")
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Limpar dumps")
            }
        }

        SectionLabel("Conta")
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                contentColor = StatusDanger,
            ),
        ) {
            Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
            Text("Sair da conta")
        }
    }

    if (showAppPicker) {
        val alreadyMonitored = monitoredApps.map { it.packageName }.toSet()
        AppPickerDialog(
            apps = installedApps,
            excluded = alreadyMonitored,
            onPick = { app ->
                viewModel.addMonitoredApp(app)
                showAppPicker = false
                onMessage("${app.label} adicionado")
            },
            onDismiss = { showAppPicker = false },
        )
    }
}

/** Dialog listing installed apps so the driver can add one to the monitored list. */
@Composable
private fun AppPickerDialog(
    apps: List<MonitoredApp>?,
    excluded: Set<String>,
    onPick: (MonitoredApp) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
        title = { Text("Escolha um app") },
        text = {
            when {
                apps == null -> Text("Carregando apps instalados…")
                else -> {
                    val options = apps.filter { it.packageName !in excluded }
                    if (options.isEmpty()) {
                        Text("Nenhum app disponível para adicionar.")
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            options.forEach { app ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPick(app) }
                                        .padding(vertical = 10.dp),
                                ) {
                                    Text(app.label, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        app.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        },
    )
}

/** A labelled card grouping related settings. */
@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column {
        SectionLabel(title)
        androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun buildPreviewProfile(fuel: String, km: String, maint: String) = DriverProfile(
    fuelPricePerLiter = fuel.toDoubleFlexible(),
    kmPerLiter = km.toDoubleFlexible().coerceAtLeast(0.1),
    maintenanceCostPerKm = maint.toDoubleFlexible(),
)

private fun String.toDoubleFlexible(): Double =
    trim().replace(',', '.').toDoubleOrNull() ?: 0.0
