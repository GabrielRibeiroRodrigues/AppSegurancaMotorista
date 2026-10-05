package com.copiloto.motorista.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.copiloto.motorista.ui.screens.AuthScreen
import com.copiloto.motorista.ui.screens.ConsentScreen
import com.copiloto.motorista.ui.screens.HistoryScreen
import com.copiloto.motorista.ui.screens.HomeScreen
import com.copiloto.motorista.ui.screens.OnboardingScreen
import com.copiloto.motorista.ui.screens.PanicButtonScreen
import com.copiloto.motorista.ui.screens.SettingsScreen
import com.copiloto.motorista.ui.theme.CopilotoTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CopilotoTheme {
                CopilotoRoot()
            }
        }
    }
}

@Composable
private fun CopilotoRoot() {
    val authViewModel: AuthViewModel = viewModel()
    val loggedIn by authViewModel.isLoggedIn.collectAsStateWithLifecycle()
    val consent by authViewModel.consentAccepted.collectAsStateWithLifecycle()

    // LGPD: nothing runs before the driver accepts the data-use terms.
    when (consent) {
        null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }
        false -> {
            ConsentScreen(onAccept = { authViewModel.acceptConsent() })
            return
        }
        else -> Unit
    }

    when (loggedIn) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        false -> AuthScreen(authViewModel)
        true -> CopilotoApp(onLogout = { authViewModel.logout() })
    }
}

private enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Copiloto", Icons.Filled.DirectionsCar),
    HISTORY("history", "Histórico", Icons.Filled.History),
    SETTINGS("settings", "Ajustes", Icons.Filled.Settings),
}

@Composable
private fun CopilotoApp(onLogout: () -> Unit) {
    val viewModel: MainViewModel = viewModel()
    val onboardingDone by viewModel.onboardingDone.collectAsStateWithLifecycle()

    when (onboardingDone) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        false -> OnboardingScreen(onFinish = { viewModel.markOnboardingDone() })
        true -> MainScaffold(viewModel, onLogout)
    }
}

@Composable
private fun MainScaffold(viewModel: MainViewModel, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val onMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val backStack by navController.currentBackStackEntryAsState()
            val current = backStack?.destination
            NavigationBar {
                Destination.entries.forEach { destination ->
                    val selected = current?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.HOME.route) { HomeScreen(viewModel, onMessage = onMessage) }
            composable(Destination.HISTORY.route) { HistoryScreen(viewModel) }
            composable(Destination.SETTINGS.route) {
                SettingsScreen(
                    viewModel,
                    onLogout = onLogout,
                    onMessage = onMessage,
                    onOpenPanicButton = { navController.navigate("panic_button") },
                )
            }
            composable("panic_button") {
                PanicButtonScreen(
                    viewModel = viewModel(),
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
