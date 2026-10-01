package com.copiloto.motorista.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.copiloto.motorista.data.model.DriverProfile
import com.copiloto.motorista.service.ParserDumpStore
import com.copiloto.motorista.service.RideAccessibilityService
import com.copiloto.motorista.ui.MainViewModel
import com.copiloto.motorista.ui.UiFormat

@Composable
fun SettingsScreen(viewModel: MainViewModel, onLogout: () -> Unit = {}) {
    val context = LocalContext.current
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    var dumpCount by remember { mutableStateOf(ParserDumpStore.count(context)) }
    var dumpRequested by remember { mutableStateOf(false) }

    var fuelPrice by remember { mutableStateOf("") }
    var kmPerLiter by remember { mutableStateOf("") }
    var maintenance by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var minimum by remember { mutableStateOf("") }
    var voice by remember { mutableStateOf(true) }
    var saved by remember { mutableStateOf(false) }

    // Populate the fields once the stored profile is loaded.
    LaunchedEffect(profile) {
        fuelPrice = profile.fuelPricePerLiter.toString()
        kmPerLiter = profile.kmPerLiter.toString()
        maintenance = profile.maintenanceCostPerKm.toString()
        target = profile.targetPerKm.toString()
        minimum = profile.minimumPerKm.toString()
        voice = profile.voiceEnabled
    }

    val previewCostPerKm = buildPreviewProfile(fuelPrice, kmPerLiter, maintenance).costPerKm

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Configurações do motorista",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        SectionTitle("Custos")
        NumberField("Preço do combustível (R$/litro)", fuelPrice) { fuelPrice = it; saved = false }
        NumberField("Consumo médio (km/litro)", kmPerLiter) { kmPerLiter = it; saved = false }
        NumberField("Manutenção (R$/km)", maintenance) { maintenance = it; saved = false }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Custo estimado por km", style = MaterialTheme.typography.labelMedium)
                Text(
                    UiFormat.money(previewCostPerKm),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        SectionTitle("Metas de rentabilidade (R$/km)")
        NumberField("Meta — verde a partir de", target) { target = it; saved = false }
        NumberField("Mínimo — vermelho abaixo de", minimum) { minimum = it; saved = false }

        SectionTitle("Voz")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Anunciar corridas por voz")
            Switch(checked = voice, onCheckedChange = { voice = it; saved = false })
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
                        voiceEnabled = voice,
                    ),
                )
                saved = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Salvar")
        }

        if (saved) {
            Text(
                "Configurações salvas.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        SectionTitle("Depuração do parser")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Quando a leitura de uma corrida falha, o app salva um \"dump\" da tela (texto) no armazenamento interno para ajudar a ajustar o parser.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Dumps salvos: $dumpCount",
                    style = MaterialTheme.typography.labelMedium,
                )
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
                        "Abra o app de corrida: a próxima tela será salva como dump.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                OutlinedButton(
                    onClick = {
                        ParserDumpStore.clear(context)
                        dumpCount = 0
                        dumpRequested = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Limpar dumps")
                }
            }
        }

        SectionTitle("Conta")
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sair da conta")
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
