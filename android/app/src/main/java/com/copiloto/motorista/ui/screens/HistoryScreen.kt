package com.copiloto.motorista.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.copiloto.motorista.data.model.RideClassification
import com.copiloto.motorista.data.model.RideSource
import com.copiloto.motorista.data.repository.startOfToday
import com.copiloto.motorista.engine.DailyGoal
import com.copiloto.motorista.ui.MainViewModel
import com.copiloto.motorista.ui.UiFormat
import com.copiloto.motorista.ui.components.SectionLabel
import com.copiloto.motorista.ui.components.StatTile
import com.copiloto.motorista.ui.theme.StatusDanger
import com.copiloto.motorista.ui.theme.StatusPositive

@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val rides by viewModel.history.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val acceptedToday by viewModel.todayAcceptedProfit.collectAsStateWithLifecycle()

    val today = rides.filter { it.createdAt >= startOfToday() }
    val lucro = today.sumOf { it.netProfit }
    val total = today.size
    val greenPct = if (total > 0) {
        today.count { it.classification == RideClassification.GREEN.name } * 100 / total
    } else {
        0
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                "Histórico",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        item { GoalProgress(accepted = acceptedToday, goal = profile.dailyGoal) }
        item {
            SectionLabel("Hoje")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    modifier = Modifier.weight(1f),
                    value = UiFormat.money(lucro),
                    label = "Lucro",
                    valueColor = if (lucro >= 0) StatusPositive else StatusDanger,
                )
                StatTile(modifier = Modifier.weight(1f), value = total.toString(), label = "Corridas")
                StatTile(
                    modifier = Modifier.weight(1f),
                    value = "$greenPct%",
                    label = "Verdes",
                    valueColor = StatusPositive,
                )
            }
        }

        item { SectionLabel("Corridas") }

        if (rides.isEmpty()) {
            item { EmptyHistory() }
        } else {
            items(rides, key = { it.id }) { ride -> RideRow(ride) }
        }
    }
}

@Composable
private fun EmptyHistory() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(30.dp),
            )
        }
        Text(
            "Nenhuma corrida ainda",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Use o simulador na tela inicial para gerar uma.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GoalProgress(accepted: Double, goal: Double) {
    val fraction = DailyGoal.progressFraction(accepted, goal)
    val percent = (fraction * 100).toInt()
    val reached = goal > 0 && accepted >= goal
    val accent = if (reached) StatusPositive else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Meta diária",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "$percent%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50)),
                color = accent,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f),
            )
            Text(
                if (reached) {
                    "Meta atingida! 🎉 ${UiFormat.money(accepted)}"
                } else {
                    "${UiFormat.money(accepted)} de ${UiFormat.money(goal)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun RideRow(ride: RideHistoryEntity) {
    val color = UiFormat.classificationColor(ride.classification)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SourceAvatar(ride.source)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(sourceName(ride.source), fontWeight = FontWeight.SemiBold)
                    ClassificationChip(ride.classification, color)
                }
                Text(
                    "${round1(ride.distanceKm)}km · ${ride.timeMinutes}min · ${UiFormat.money(ride.grossPerKm)}/km",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    UiFormat.dateTime(ride.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    UiFormat.money(ride.netProfit),
                    style = MaterialTheme.typography.titleMedium,
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
private fun SourceAvatar(sourceRaw: String) {
    val initial = sourceName(sourceRaw).take(1).uppercase()
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initial,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun ClassificationChip(classification: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
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
