package co.edu.unal.tictactoe.online.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.unal.tictactoe.online.model.GameResult
import co.edu.unal.tictactoe.online.model.GameRoom
import co.edu.unal.tictactoe.online.model.GameStatus
import co.edu.unal.tictactoe.online.model.PlayerRole
import co.edu.unal.tictactoe.ui.AppPalette
import co.edu.unal.tictactoe.ui.BoardCard

/** Marco común: fondo con gradiente, barra superior, botón de tema y snackbar de mensajes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineScaffold(
    title: String,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    onBack: (() -> Unit)? = null,
    snackbarMessage: String? = null,
    onSnackbarShown: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbar.showSnackbar(it)
            onSnackbarShown()
        }
    }
    val primary = AppPalette.textPrimary(dark)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (dark) AppPalette.darkGradient else AppPalette.lightGradient)
    ) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    title = { Text(title, color = primary, fontSize = 22.sp) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = primary)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                if (dark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                "Cambiar tema",
                                tint = primary
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content
            )
        }
    }
}

// ---------------------------- LOBBY ----------------------------

@Composable
fun LobbyScreen(
    state: OnlineUiState,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: (String) -> Unit,
    onRetry: () -> Unit,
    onMessageShown: () -> Unit
) {
    val primary = AppPalette.textPrimary(dark)
    val secondary = AppPalette.textSecondary(dark)

    OnlineScaffold("Juego online", dark, onToggleTheme, onBack, state.message, onMessageShown) {
        when {
            state.connecting -> {
                Spacer(Modifier.weight(1f))
                CircularProgressIndicator(color = AppPalette.accent)
                Spacer(Modifier.height(12.dp))
                Text("Conectando…", color = secondary)
                Spacer(Modifier.weight(1f))
            }

            state.playerId == null -> {
                Spacer(Modifier.weight(1f))
                Text("Sin conexión con el servidor", color = primary, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = AppPalette.accent)
                ) { Text("Reintentar") }
                Spacer(Modifier.weight(1f))
            }

            else -> {
                OutlinedTextField(
                    value = state.playerName,
                    onValueChange = onNameChange,
                    label = { Text("Tu nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = primary,
                        unfocusedTextColor = primary,
                        focusedBorderColor = AppPalette.accent,
                        unfocusedBorderColor = AppPalette.cardBorder(dark),
                        focusedLabelColor = AppPalette.accent,
                        unfocusedLabelColor = secondary,
                        cursorColor = AppPalette.accent
                    )
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onCreate,
                    enabled = state.playerName.isNotBlank() && !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AppPalette.accent)
                ) { Text("Crear juego nuevo") }

                Spacer(Modifier.height(20.dp))
                Text(
                    "Juegos disponibles",
                    color = primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(8.dp))

                if (state.waitingGames.isEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Text("No hay juegos disponibles. ¡Crea uno!", color = secondary)
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(state.waitingGames, key = { it.id }) { game ->
                            WaitingGameCard(game, dark, enabled = !state.busy, onJoin = { onJoin(game.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WaitingGameCard(game: GameRoom, dark: Boolean, enabled: Boolean, onJoin: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(shape)
            .background(AppPalette.cardBackground(dark))
            .border(1.dp, AppPalette.cardBorder(dark), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(game.hostName, color = AppPalette.textPrimary(dark), fontWeight = FontWeight.Bold)
            Text("Esperando oponente", color = AppPalette.textSecondary(dark), fontSize = 12.sp)
        }
        Button(
            onClick = onJoin,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = AppPalette.accent)
        ) { Text("Unirse") }
    }
}

// ---------------------------- PARTIDA ----------------------------

@Composable
fun OnlineGameScreen(
    state: OnlineUiState,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    onCellClick: (Int) -> Unit,
    onRematch: () -> Unit,
    onLeave: () -> Unit,
    onMessageShown: () -> Unit
) {
    val room = state.game
    val role = room?.let { r -> state.playerId?.let { r.roleOf(it) } }
    val primary = AppPalette.textPrimary(dark)
    val secondary = AppPalette.textSecondary(dark)
    var confirmLeave by remember { mutableStateOf(false) }

    // Una partida en curso pide confirmación antes de abandonar.
    fun requestLeave() {
        if (room?.status == GameStatus.PLAYING) confirmLeave = true else onLeave()
    }
    BackHandler { requestLeave() }

    val myTurn = room != null && role != null &&
            room.status == GameStatus.PLAYING && room.turn == role.symbol

    OnlineScaffold("Partida online", dark, onToggleTheme, { requestLeave() }, state.message, onMessageShown) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            BoardCard(
                board = (room?.board ?: GameRoom.EMPTY_BOARD).toList(),
                isDarkTheme = dark,
                interactionEnabled = myTurn,
                cellSize = 96.dp,
                onCellClicked = onCellClick
            )
            Spacer(Modifier.height(20.dp))

            if (room != null && role != null) {
                Text(
                    "RONDA ${room.round}  ·  Juegas con ${role.symbol}",
                    color = secondary, fontSize = 12.sp, letterSpacing = 1.sp
                )
            }
            Text(
                statusText(room, role),
                color = primary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold
            )
            if (room != null && role != null && room.status != GameStatus.WAITING) {
                Text(
                    "Oponente: ${room.nameOf(role.opponent)} (${role.opponent.symbol})",
                    color = secondary, fontSize = 14.sp
                )
            }
            if (room?.status == GameStatus.WAITING) {
                Spacer(Modifier.height(12.dp))
                CircularProgressIndicator(color = AppPalette.accent)
            }

            Spacer(Modifier.height(20.dp))
            if (room?.status == GameStatus.FINISHED && room.result != GameResult.ABANDONED) {
                Button(
                    onClick = onRematch,
                    colors = ButtonDefaults.buttonColors(containerColor = AppPalette.accent)
                ) { Text("Revancha") }
            }
            if (room?.status != GameStatus.PLAYING) {
                TextButton(onClick = onLeave) {
                    Text(
                        if (room?.status == GameStatus.WAITING) "Cancelar juego" else "Salir",
                        color = AppPalette.accent
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("¿Abandonar la partida?") },
            text = { Text("Tu oponente será avisado de que te fuiste.") },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; onLeave() }) { Text("Abandonar") }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Seguir jugando") } }
        )
    }
}

private fun statusText(room: GameRoom?, role: PlayerRole?): String {
    if (room == null || role == null) return "Cargando partida…"
    return when (room.status) {
        GameStatus.WAITING -> "Esperando a que alguien se una…"
        GameStatus.PLAYING -> when {
            !room.isOnline(role.opponent) -> "Tu oponente se desconectó…"
            room.turn == role.symbol -> "Tu turno"
            else -> "Turno de ${room.nameOf(role.opponent)}"
        }
        GameStatus.FINISHED -> {
            val iWon = (room.result == GameResult.HOST_WINS && role == PlayerRole.HOST) ||
                    (room.result == GameResult.GUEST_WINS && role == PlayerRole.GUEST)
            when {
                room.result == GameResult.TIE -> "¡Empate!"
                room.result == GameResult.ABANDONED -> "Tu oponente abandonó la partida"
                iWon -> "¡Ganaste!"
                else -> "Perdiste"
            }
        }
    }
}

