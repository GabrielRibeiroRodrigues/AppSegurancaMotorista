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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.copiloto.motorista.data.local.RideHistoryEntity
import com.copiloto.motorista.data.model.RideClassification
import com.copiloto.motorista.data.model.RideSource
import com.copiloto.motorista.ui.MainViewModel
import com.copiloto.motorista.ui.UiFormat
import java.util.Calendar

@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val rides by viewModel.history.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val acceptedToday by viewModel.todayAcceptedProfit.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        GoalProgress(
            accepted = acceptedToday,
            goal = profile.dailyGoal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        )

        if (rides.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nenhuma corrida registrada ainda.\nUse o simulador para gerar uma.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { TodaySummary(rides) }
                items(rides, key = { it.id }) { ride -> RideRow(ride) }
            }
        }
    }
}

@Composable
private fun GoalProgress(accepted: Double, goal: Double, modifier: Modifier = Modifier) {
    val fraction = com.copiloto.motorista.engine.DailyGoal.progressFraction(accepted, goal)
    val percent = (fraction * 100).toInt()
    val reached = goal > 0 && accepted >= goal

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Meta diária", fontWeight = FontWeight.Bold)
                Text(
                    "${UiFormat.money(accepted)} / ${UiFormat.money(goal)} · $percent%",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (reached) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color = if (reached) Color(0xFF22C55E) else MaterialTheme.colorScheme.primary,
            )
            if (reached) {
                Text(
                    "Meta atingida! 🎉",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF22C55E),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun TodaySummary(rides: List<RideHistoryEntity>) {
    val today = rides.filter { isToday(it.createdAt) }
    val lucro = today.sumOf { it.netProfit }
    val total = today.size
    val green = today.count { it.classification == RideClassification.GREEN.name }
    val pctGreen = if (total > 0) green * 100 / total else 0

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Hoje",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                value = UiFormat.money(lucro),
                label = "Lucro líquido",
                color = if (lucro >= 0) Color(0xFF22C55E) else Color(0xFFEF4444),
            )
            StatCard(
                modifier = Modifier.weight(1f),
                value = total.toString(),
                label = "Corridas",
            )
            StatCard(
                modifier = Modifier.weight(1f),
                value = "$pctGreen%",
                label = "Verdes",
                color = Color(0xFF22C55E),
            )
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun isToday(epochMillis: Long): Boolean {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = epochMillis }
    return now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
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
