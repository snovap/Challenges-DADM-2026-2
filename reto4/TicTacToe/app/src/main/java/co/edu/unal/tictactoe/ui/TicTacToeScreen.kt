package co.edu.unal.tictactoe.ui

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.unal.tictactoe.logic.TicTacToeGame

// ---------- Paleta de la app (no depende del Theme.kt generado por defecto) ----------
private object AppPalette {
    val accent = Color(0xFF8B5CF6) // morado/violeta de acento

    // Fondo con gradiente - modo oscuro
    val darkGradient = Brush.verticalGradient(
        listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F3460))
    )
    // Fondo con gradiente - modo claro
    val lightGradient = Brush.verticalGradient(
        listOf(Color(0xFFEDE7F6), Color(0xFFE3F2FD), Color(0xFFF3E5F5))
    )

    val xColor = Color(0xFF39D97A) // verde de la X
    val oColor = Color(0xFFE05252) // rojo/coral de la O

    // Colores de texto y tarjetas según el modo
    fun textPrimary(dark: Boolean) = if (dark) Color.White else Color(0xFF1B1B2F)
    fun textSecondary(dark: Boolean) = if (dark) Color.White.copy(alpha = 0.6f) else Color(0xFF5C5C70)
    fun cardBackground(dark: Boolean) = if (dark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.65f)
    fun cardBorder(dark: Boolean) = if (dark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.8f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicTacToeScreen() {
    val game = remember { TicTacToeGame() }
    val context = LocalContext.current
    val activity = context as? Activity

    var board by remember { mutableStateOf(List(9) { TicTacToeGame.OPEN_SPOT }) }
    var infoText by remember { mutableStateOf("Tú empiezas.") }
    var gameOver by remember { mutableStateOf(false) }
    var roundNumber by remember { mutableStateOf(1) }

    var humanScore by remember { mutableStateOf(0) }
    var tieScore by remember { mutableStateOf(0) }
    var computerScore by remember { mutableStateOf(0) }
    var humanStartsNext by remember { mutableStateOf(true) }

    var difficulty by remember { mutableStateOf(game.difficultyLevel) }

    var isDarkTheme by remember { mutableStateOf(true) } // el mockup arranca en modo oscuro

    var showMenu by remember { mutableStateOf(false) }
    var showDifficultyDialog by remember { mutableStateOf(false) }
    var showQuitDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    fun refreshBoard() {
        board = List(9) { game.getBoardOccupant(it) }
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
    }

    fun startNewGame() {
        game.clearBoard()
        refreshBoard()
        gameOver = false
        roundNumber++

        val humanStarts = humanStartsNext
        humanStartsNext = !humanStartsNext

        if (humanStarts) {
            infoText = "Tú empiezas."
        } else {
            infoText = "Android empieza..."
            val winner = playComputerTurn()
            if (winner == 0) infoText = "Tu turno." else applyResult(winner)
        }
    }

    fun onCellClicked(location: Int) {
        if (gameOver || board[location] != TicTacToeGame.OPEN_SPOT) return

        game.setMove(TicTacToeGame.HUMAN_PLAYER, location)
        refreshBoard()

        var winner = game.checkForWinner()
        if (winner == 0) {
            infoText = "Turno de Android..."
            winner = playComputerTurn()
        }

        if (winner == 0) infoText = "Tu turno." else applyResult(winner)
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
                        // Botón de modo claro/oscuro
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                BoardCard(
                    board = board,
                    isDarkTheme = isDarkTheme,
                    onCellClicked = ::onCellClicked
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "RONDA $roundNumber",
                    color = textSecondary,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = infoText,
                    color = textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(20.dp))

                ScoreBoard(
                    human = humanScore,
                    ties = tieScore,
                    computer = computerScore,
                    isDarkTheme = isDarkTheme
                )

                Spacer(modifier = Modifier.height(16.dp))

                DifficultyChip(difficulty = difficulty, isDarkTheme = isDarkTheme) {
                    showDifficultyDialog = true
                }
            }
        }
    }

    if (showDifficultyDialog) {
        DifficultyDialog(
            current = difficulty,
            isDarkTheme = isDarkTheme,
            onApply = { selected ->
                difficulty = selected
                game.difficultyLevel = selected
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

// ---------- Tablero dentro de una tarjeta con estilo "glass" ----------
@Composable
private fun BoardCard(board: List<Char>, isDarkTheme: Boolean, onCellClicked: (Int) -> Unit) {
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
                            onClick = { onCellClicked(location) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(value: Char, isDarkTheme: Boolean, onClick: () -> Unit) {
    val lineColor = AppPalette.cardBorder(isDarkTheme)
    Box(
        modifier = Modifier
            .size(96.dp)
            .border(0.6.dp, lineColor)
            .clickable(enabled = value == TicTacToeGame.OPEN_SPOT, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val color = when (value) {
            TicTacToeGame.HUMAN_PLAYER -> AppPalette.xColor
            TicTacToeGame.COMPUTER_PLAYER -> AppPalette.oColor
            else -> Color.Transparent
        }
        Text(
            text = if (value == TicTacToeGame.OPEN_SPOT) "" else value.toString(),
            fontSize = 46.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = if (value != TicTacToeGame.OPEN_SPOT)
                Modifier.shadow(elevation = 12.dp, spotColor = color) else Modifier
        )
    }
}

// ---------- Marcador con chips e íconos ----------
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

// ---------- Chip de dificultad actual ----------
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

// ---------- Diálogo de dificultad rediseñado ----------
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