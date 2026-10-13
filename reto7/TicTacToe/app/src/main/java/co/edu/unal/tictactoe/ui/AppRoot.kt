package co.edu.unal.tictactoe.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.unal.tictactoe.online.ui.LobbyScreen
import co.edu.unal.tictactoe.online.ui.OnlineGameScreen
import co.edu.unal.tictactoe.online.ui.OnlineScaffold
import co.edu.unal.tictactoe.online.ui.OnlineViewModel

private enum class Route { HOME, VS_ANDROID, ONLINE }

/** Raíz de navegación por estado: Inicio → (vs Android | Lobby → Partida online). */
@Composable
fun AppRoot(viewModel: OnlineViewModel = viewModel()) {
    var route by rememberSaveable { mutableStateOf(Route.HOME) }
    var dark by rememberSaveable { mutableStateOf(true) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (route) {
        Route.HOME -> HomeScreen(
            dark = dark,
            onToggleTheme = { dark = !dark },
            onVsAndroid = { route = Route.VS_ANDROID },
            onOnline = { route = Route.ONLINE }
        )

        Route.VS_ANDROID -> {
            BackHandler { route = Route.HOME }
            TicTacToeScreen()
        }

        Route.ONLINE -> if (state.currentGameId == null) {
            BackHandler { route = Route.HOME }
            LobbyScreen(
                state = state,
                dark = dark,
                onToggleTheme = { dark = !dark },
                onBack = { route = Route.HOME },
                onNameChange = viewModel::setPlayerName,
                onCreate = viewModel::createGame,
                onJoin = viewModel::joinGame,
                onRetry = viewModel::connect,
                onMessageShown = viewModel::dismissMessage
            )
        } else {
            OnlineGameScreen(
                state = state,
                dark = dark,
                onToggleTheme = { dark = !dark },
                onCellClick = viewModel::makeMove,
                onRematch = viewModel::rematch,
                onLeave = viewModel::leaveGame,
                onMessageShown = viewModel::dismissMessage
            )
        }
    }
}

@Composable
private fun HomeScreen(
    dark: Boolean,
    onToggleTheme: () -> Unit,
    onVsAndroid: () -> Unit,
    onOnline: () -> Unit
) {
    OnlineScaffold(title = "Triqui", dark = dark, onToggleTheme = onToggleTheme) {
        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.weight(1f))
        Text(
            "¿Cómo quieres jugar?",
            color = AppPalette.textPrimary(dark),
            fontSize = 24.sp
        )
        Spacer(androidx.compose.ui.Modifier.height(24.dp))
        Button(
            onClick = onOnline,
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AppPalette.accent)
        ) { Text("Jugar online") }
        Spacer(androidx.compose.ui.Modifier.height(12.dp))
        OutlinedButton(
            onClick = onVsAndroid,
            modifier = androidx.compose.ui.Modifier.fillMaxWidth()
        ) { Text("Jugar contra Android", color = AppPalette.accent) }
        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.weight(1f))
    }
}
