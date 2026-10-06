package co.edu.unal.tictactoe.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import co.edu.unal.tictactoe.R
import co.edu.unal.tictactoe.logic.TicTacToeGame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------- Paleta de la app ----------
private object AppPalette {
    val accent = Color(0xFF8B5CF6)

    val darkGradient = Brush.verticalGradient(
        listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F3460))
    )
    val lightGradient = Brush.verticalGradient(
        listOf(Color(0xFFEDE7F6), Color(0xFFE3F2FD), Color(0xFFF3E5F5))
    )

    val xColor = Color(0xFF39D97A)
    val oColor = Color(0xFFE05252)

    fun textPrimary(dark: Boolean) = if (dark) Color.White else Color(0xFF1B1B2F)
    fun textSecondary(dark: Boolean) = if (dark) Color.White.copy(alpha = 0.6f) else Color(0xFF5C5C70)
    fun cardBackground(dark: Boolean) = if (dark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.65f)
    fun cardBorder(dark: Boolean) = if (dark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.8f)
}

private const val COMPUTER_MOVE_DELAY_MS = 700L

// --- Claves de SharedPreferences (equivalente a "ttt_prefs" del documento) ---
private const val PREFS_NAME = "ttt_prefs"
private const val PREF_HUMAN_WINS = "mHumanWins"
private const val PREF_COMPUTER_WINS = "mComputerWins"
private const val PREF_TIES = "mTies"
private const val PREF_DIFFICULTY = "mDifficulty"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicTacToeScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // SharedPreferences: persiste ENTRE reinicios de la app (equivalente al Paso "Saving Persistent Information")
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    fun persistScores(human: Int, ties: Int, computer: Int) {
        prefs.edit()
            .putInt(PREF_HUMAN_WINS, human)
            .putInt(PREF_TIES, ties)
            .putInt(PREF_COMPUTER_WINS, computer)
            .apply()
    }

    // --- rememberSaveable: sobrevive a la rotación (equivalente a onSaveInstanceState + Bundle) ---
    var boardString by rememberSaveable {
        mutableStateOf(List(TicTacToeGame.BOARD_SIZE) { TicTacToeGame.OPEN_SPOT }.joinToString(""))
    }
    var difficultyOrdinal by rememberSaveable {
        mutableStateOf(prefs.getInt(PREF_DIFFICULTY, TicTacToeGame.DifficultyLevel.EXPERT.ordinal))
    }
    var infoText by rememberSaveable { mutableStateOf("Tú empiezas.") }
    var gameOver by rememberSaveable { mutableStateOf(false) }
    var roundNumber by rememberSaveable { mutableStateOf(1) }
    var humanStartsNext by rememberSaveable { mutableStateOf(true) }
    var isComputerThinking by rememberSaveable { mutableStateOf(false) }
    var isDarkTheme by rememberSaveable { mutableStateOf(true) }

    // Puntajes: valor inicial viene de SharedPreferences (persiste entre reinicios),
    // y rememberSaveable además los mantiene estables durante la rotación.
    var humanScore by rememberSaveable { mutableStateOf(prefs.getInt(PREF_HUMAN_WINS, 0)) }
    var tieScore by rememberSaveable { mutableStateOf(prefs.getInt(PREF_TIES, 0)) }
    var computerScore by rememberSaveable { mutableStateOf(prefs.getInt(PREF_COMPUTER_WINS, 0)) }

    // El objeto de lógica se recrea en cada composición nueva (p. ej. tras rotar),
    // pero inmediatamente se le restaura el tablero y la dificultad ya guardados.
    val game = remember {
        TicTacToeGame().apply {
            setBoardState(boardString.toCharArray())
            difficultyLevel = TicTacToeGame.DifficultyLevel.values()[difficultyOrdinal]
        }
    }
    val difficulty = TicTacToeGame.DifficultyLevel.values()[difficultyOrdinal]

    var board by remember { mutableStateOf(List(TicTacToeGame.BOARD_SIZE) { game.getBoardOccupant(it) }) }

    var showMenu by remember { mutableStateOf(false) }
    var showDifficultyDialog by remember { mutableStateOf(false) }
    var showQuitDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val humanMediaPlayer = remember { MediaPlayer.create(context, R.raw.tap_human) }
    val computerMediaPlayer = remember { MediaPlayer.create(context, R.raw.tap_computer) }

    DisposableEffect(Unit) {
        onDispose {
            humanMediaPlayer?.release()
            computerMediaPlayer?.release()
        }
    }

    fun playSound(player: MediaPlayer?) {
        player?.apply {
            seekTo(0)
            start()
        }
    }

    fun refreshBoard() {
        val chars = List(TicTacToeGame.BOARD_SIZE) { game.getBoardOccupant(it) }
        board = chars
        boardString = chars.joinToString("") // se guarda automáticamente vía rememberSaveable
    }

    fun playComputerTurn(): Int {
        val move = game.getComputerMove()
        game.setMove(TicTacToeGame.COMPUTER_PLAYER, move)
        refreshBoard()
        return game.checkForWinner()
    }

    fun applyResult(winner: Int) {
        when (winner) {
            1 -> { infoText = "¡Empate!"; tieScore++; gameOver = true }
            2 -> { infoText = "¡Ganaste!"; humanScore++; gameOver = true }
            3 -> { infoText = "Ganó Android."; computerScore++; gameOver = true }
        }
        if (winner != 0) persistScores(humanScore, tieScore, computerScore)
    }

    /** Lógica real del turno de la IA, aislada para poder reanudarla si una rotación la interrumpió. */
    suspend fun performComputerTurn() {
        delay(COMPUTER_MOVE_DELAY_MS)
        val winner = playComputerTurn()
        playSound(computerMediaPlayer)
        isComputerThinking = false
        if (winner == 0) infoText = "Tu turno." else applyResult(winner)
    }

    fun scheduleComputerTurn() {
        isComputerThinking = true
        coroutineScope.launch { performComputerTurn() }
    }

    // --- Fix del "Extra Challenge 2": si la pantalla se recompuso (p. ej. por un giro)
    // mientras la IA estaba "pensando", la corrutina anterior se canceló junto con la
    // composición vieja. Aquí retomamos esa jugada pendiente en vez de dejar el juego
    // colgado esperando a Android para siempre.
    LaunchedEffect(Unit) {
        if (isComputerThinking && !gameOver) {
            performComputerTurn()
        }
    }

    fun startNewGame() {
        game.clearBoard()
        refreshBoard()
        gameOver = false
        isComputerThinking = false
        roundNumber++

        val humanStarts = humanStartsNext
        humanStartsNext = !humanStartsNext

        if (humanStarts) {
            infoText = "Tú empiezas."
        } else {
            infoText = "Android empieza..."
            scheduleComputerTurn()
        }
    }

    fun resetScores() {
        humanScore = 0
        tieScore = 0
        computerScore = 0
        persistScores(0, 0, 0)
    }

    fun onCellClicked(location: Int) {
        if (gameOver || isComputerThinking || board[location] != TicTacToeGame.OPEN_SPOT) return

        game.setMove(TicTacToeGame.HUMAN_PLAYER, location)
        refreshBoard()
        playSound(humanMediaPlayer)

        val winnerAfterHuman = game.checkForWinner()
        if (winnerAfterHuman != 0) {
            applyResult(winnerAfterHuman)
            return
        }

        infoText = "Turno de Android..."
        scheduleComputerTurn()
    }

    val textPrimary = AppPalette.textPrimary(isDarkTheme)
    val textSecondary = AppPalette.textSecondary(isDarkTheme)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDarkTheme) AppPalette.darkGradient else AppPalette.lightGradient)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AppPalette.cardBackground(isDarkTheme)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("T", color = textPrimary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Triqui", color = textPrimary, fontSize = 22.sp)
                        }
                    },
                    actions = {
                        IconButton(onClick = { isDarkTheme = !isDarkTheme }) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Cambiar tema",
                                tint = textPrimary
                            )
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menú", tint = textPrimary)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(
                                if (isDarkTheme) Color(0xFF1E1E3A) else Color.White
                            )
                        ) {
                            val menuTextColor = AppPalette.textPrimary(isDarkTheme)
                            val menuIconColor = AppPalette.textSecondary(isDarkTheme)

                            DropdownMenuItem(
                                text = { Text("Nueva partida", color = menuTextColor) },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = menuIconColor) },
                                onClick = { showMenu = false; startNewGame() }
                            )
                            DropdownMenuItem(
                                text = { Text("Dificultad", color = menuTextColor) },
                                leadingIcon = { Icon(Icons.Default.SportsEsports, contentDescription = null, tint = menuIconColor) },
                                onClick = { showMenu = false; showDifficultyDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Reiniciar puntuación", color = menuTextColor) },
                                leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null, tint = menuIconColor) },
                                onClick = { showMenu = false; resetScores() }
                            )
                            DropdownMenuItem(
                                text = { Text("Acerca de", color = menuTextColor) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = menuIconColor) },
                                onClick = { showMenu = false; showAboutDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Salir", color = menuTextColor) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = menuIconColor) },
                                onClick = { showMenu = false; showQuitDialog = true }
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                GameContent(
                    isLandscape = isLandscape,
                    board = board,
                    isDarkTheme = isDarkTheme,
                    interactionEnabled = !isComputerThinking && !gameOver,
                    onCellClicked = ::onCellClicked,
                    roundNumber = roundNumber,
                    infoText = infoText,
                    humanScore = humanScore,
                    tieScore = tieScore,
                    computerScore = computerScore,
                    difficulty = difficulty,
                    onDifficultyClick = { showDifficultyDialog = true },
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )
            }
        }
    }

    if (showDifficultyDialog) {
        DifficultyDialog(
            current = difficulty,
            isDarkTheme = isDarkTheme,
            onApply = { selected ->
                difficultyOrdinal = selected.ordinal
                game.difficultyLevel = selected
                prefs.edit().putInt(PREF_DIFFICULTY, selected.ordinal).apply()
                showDifficultyDialog = false
            },
            onDismiss = { showDifficultyDialog = false }
        )
    }

    if (showQuitDialog) {
        AlertDialog(
            onDismissRequest = { showQuitDialog = false },
            title = { Text("¿Seguro que quieres salir?") },
            confirmButton = { TextButton(onClick = { activity?.finish() }) { Text("Sí") } },
            dismissButton = { TextButton(onClick = { showQuitDialog = false }) { Text("No") } }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("Triqui") },
            text = { Text("Elige entre tres niveles de dificultad.\n¡No dejes que Android te gane!") },
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text("OK") } }
        )
    }
}

// ---------- Layout adaptativo: Column en portrait, Row en landscape ----------
// Equivalente conceptual de res/layout-land/main.xml, pero sin archivos XML duplicados.
@Composable
private fun GameContent(
    isLandscape: Boolean,
    board: List<Char>,
    isDarkTheme: Boolean,
    interactionEnabled: Boolean,
    onCellClicked: (Int) -> Unit,
    roundNumber: Int,
    infoText: String,
    humanScore: Int,
    tieScore: Int,
    computerScore: Int,
    difficulty: TicTacToeGame.DifficultyLevel,
    onDifficultyClick: () -> Unit,
    textPrimary: Color,
    textSecondary: Color
) {
    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            BoardCard(
                board = board,
                isDarkTheme = isDarkTheme,
                interactionEnabled = interactionEnabled,
                cellSize = 78.dp, // tablero más pequeño, como pide el documento (270x270 vs 300x300)
                onCellClicked = onCellClicked
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text("RONDA $roundNumber", color = textSecondary, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium)
                Text(infoText, color = textPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(16.dp))
                ScoreBoard(humanScore, tieScore, computerScore, isDarkTheme)
                Spacer(modifier = Modifier.height(12.dp))
                DifficultyChip(difficulty, isDarkTheme, onDifficultyClick)
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            BoardCard(board, isDarkTheme, interactionEnabled, cellSize = 96.dp, onCellClicked = onCellClicked)
            Spacer(modifier = Modifier.height(20.dp))
            Text("RONDA $roundNumber", color = textSecondary, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium)
            Text(infoText, color = textPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(20.dp))
            ScoreBoard(humanScore, tieScore, computerScore, isDarkTheme)
            Spacer(modifier = Modifier.height(16.dp))
            DifficultyChip(difficulty, isDarkTheme, onDifficultyClick)
        }
    }
}

@Composable
private fun BoardCard(
    board: List<Char>,
    isDarkTheme: Boolean,
    interactionEnabled: Boolean,
    cellSize: Dp,
    onCellClicked: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(AppPalette.cardBackground(isDarkTheme))
            .border(1.dp, AppPalette.cardBorder(isDarkTheme), RoundedCornerShape(28.dp))
            .padding(16.dp)
    ) {
        Column {
            for (row in 0..2) {
                Row {
                    for (col in 0..2) {
                        val location = row * 3 + col
                        Cell(
                            value = board[location],
                            isDarkTheme = isDarkTheme,
                            enabled = interactionEnabled,
                            size = cellSize,
                            onClick = { onCellClicked(location) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(value: Char, isDarkTheme: Boolean, enabled: Boolean, size: Dp, onClick: () -> Unit) {
    val lineColor = AppPalette.cardBorder(isDarkTheme)
    Box(
        modifier = Modifier
            .size(size)
            .border(0.6.dp, lineColor)
            .clickable(
                enabled = enabled && value == TicTacToeGame.OPEN_SPOT,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        val color = when (value) {
            TicTacToeGame.HUMAN_PLAYER -> AppPalette.xColor
            TicTacToeGame.COMPUTER_PLAYER -> AppPalette.oColor
            else -> Color.Transparent
        }
        Text(
            text = if (value == TicTacToeGame.OPEN_SPOT) "" else value.toString(),
            fontSize = (size.value * 0.48f).sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = if (value != TicTacToeGame.OPEN_SPOT)
                Modifier.shadow(elevation = 12.dp, spotColor = color) else Modifier
        )
    }
}

@Composable
private fun ScoreBoard(human: Int, ties: Int, computer: Int, isDarkTheme: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ScoreChip("Human", human, Icons.Default.Person, isDarkTheme)
        ScoreChip("Ties", ties, Icons.Default.Balance, isDarkTheme)
        ScoreChip("Android", computer, Icons.Default.SmartToy, isDarkTheme)
    }
}

@Composable
private fun ScoreChip(label: String, value: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, isDarkTheme: Boolean) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppPalette.cardBackground(isDarkTheme))
            .border(1.dp, AppPalette.cardBorder(isDarkTheme), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = AppPalette.textSecondary(isDarkTheme), modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, color = AppPalette.textSecondary(isDarkTheme), fontSize = 12.sp)
        }
        Text(value.toString(), color = AppPalette.textPrimary(isDarkTheme), fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DifficultyChip(difficulty: TicTacToeGame.DifficultyLevel, isDarkTheme: Boolean, onClick: () -> Unit) {
    val (icon, label) = difficultyIconAndLabel(difficulty)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(AppPalette.accent.copy(alpha = if (isDarkTheme) 0.25f else 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = AppPalette.accent, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = AppPalette.accent, fontWeight = FontWeight.Medium)
    }
}

private fun difficultyIconAndLabel(level: TicTacToeGame.DifficultyLevel) = when (level) {
    TicTacToeGame.DifficultyLevel.EASY -> Icons.Default.Spa to "Fácil"
    TicTacToeGame.DifficultyLevel.HARDER -> Icons.Default.LocalFireDepartment to "Difícil"
    TicTacToeGame.DifficultyLevel.EXPERT -> Icons.Default.Star to "Experto"
}

@Composable
private fun DifficultyDialog(
    current: TicTacToeGame.DifficultyLevel,
    isDarkTheme: Boolean,
    onApply: (TicTacToeGame.DifficultyLevel) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(current) }
    val options = listOf(
        TicTacToeGame.DifficultyLevel.EASY,
        TicTacToeGame.DifficultyLevel.HARDER,
        TicTacToeGame.DifficultyLevel.EXPERT
    )

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(if (isDarkTheme) Color(0xFF1E1E3A) else Color.White)
                .padding(24.dp)
        ) {
            Text(
                "Selecciona la dificultad",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AppPalette.textPrimary(isDarkTheme)
            )
            Text(
                "Elige el nivel de desafío de Android.",
                fontSize = 14.sp,
                color = AppPalette.textSecondary(isDarkTheme)
            )
            Spacer(modifier = Modifier.height(16.dp))

            options.forEach { level ->
                val (icon, label) = difficultyIconAndLabel(level)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = level == selected, onClick = { selected = level })
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = level == selected,
                        onClick = { selected = level },
                        colors = RadioButtonDefaults.colors(selectedColor = AppPalette.accent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(icon, contentDescription = null, tint = AppPalette.textSecondary(isDarkTheme), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(label, color = AppPalette.textPrimary(isDarkTheme))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar", color = AppPalette.accent)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onApply(selected) },
                    colors = ButtonDefaults.buttonColors(containerColor = AppPalette.accent),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Aplicar")
                }
            }
        }
    }
}