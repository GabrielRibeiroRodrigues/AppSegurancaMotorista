package com.copiloto.motorista.ui.screens

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.copiloto.motorista.ui.PermissionUtils
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pages = listOf(
        OnboardingPage(
            icon = Icons.Filled.DirectionsCar,
            title = "Bem-vindo ao Copiloto",
            description = "O Copiloto avalia cada corrida em tempo real e mostra se vale a pena, por voz e num cartão flutuante. Para isso, precisamos liberar 3 permissões — vamos juntos.",
        ),
        OnboardingPage(
            icon = Icons.Filled.Layers,
            title = "Cartão flutuante",
            description = "Permite desenhar o cartão de rentabilidade por cima dos apps de corrida, sem atrapalhar o uso deles.",
            actionLabel = "Conceder",
            onAction = { context.startActivity(PermissionUtils.overlaySettingsIntent(context)) },
        ),
        OnboardingPage(
            icon = Icons.Filled.Accessibility,
            title = "Leitura de corridas",
            description = "Usa a Acessibilidade para ler a oferta na tela e calcular a rentabilidade. Nada é coletado fora dos apps de corrida selecionados.",
            actionLabel = "Ativar",
            onAction = { context.startActivity(PermissionUtils.accessibilitySettingsIntent()) },
        ),
        OnboardingPage(
            icon = Icons.Filled.BatteryFull,
            title = "Bateria",
            description = "Impede o Android de encerrar o app em segundo plano, para ele seguir funcionando durante toda a jornada.",
            actionLabel = "Desativar otimização",
            onAction = { context.startActivity(PermissionUtils.ignoreBatteryOptimizationIntent(context)) },
        ),
    )

    val pagerState = rememberPagerState { pages.size }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
    ) {
        TextButton(
            onClick = onFinish,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text("Pular")
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { index ->
            PageContent(pages[index])
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(pages.size) { i ->
                val selected = i == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (selected) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        ),
                )
            }
        }

        val isLast = pagerState.currentPage == pages.lastIndex
        Button(
            onClick = {
                if (isLast) onFinish()
                else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isLast) "Começar a usar" else "Próximo")
        }
    }
}

@Composable
private fun PageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            page.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            page.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (page.actionLabel != null && page.onAction != null) {
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = page.onAction) {
                Text(page.actionLabel)
            }
        }
    }
}
