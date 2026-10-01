package com.copiloto.motorista.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.copiloto.motorista.data.local.RideHistoryEntity
import com.copiloto.motorista.data.model.RideSource
import com.copiloto.motorista.ui.MainViewModel
import com.copiloto.motorista.ui.UiFormat

@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val rides by viewModel.history.collectAsStateWithLifecycle()

    if (rides.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Nenhuma corrida registrada ainda.\nUse o simulador para gerar uma.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(rides, key = { it.id }) { ride -> RideRow(ride) }
    }
}

@Composable
private fun RideRow(ride: RideHistoryEntity) {
    val color = UiFormat.classificationColor(ride.classification)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(sourceName(ride.source), fontWeight = FontWeight.SemiBold)
                    ClassificationChip(ride.classification, color)
                }
                Text(
                    UiFormat.dateTime(ride.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${UiFormat.money(ride.grossPrice)} · ${round1(ride.distanceKm)}km · ${ride.timeMinutes}min · ${UiFormat.money(ride.grossPerKm)}/km",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    UiFormat.money(ride.netProfit),
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                Text(
                    "lucro",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ClassificationChip(classification: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            UiFormat.classificationLabel(classification),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun sourceName(raw: String): String =
    runCatching { RideSource.valueOf(raw).displayName }.getOrDefault(raw)

private fun round1(value: Double): String {
    val r = (value * 10).toInt() / 10.0
    return r.toString().replace('.', ',')
}
