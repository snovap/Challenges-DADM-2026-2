package co.edu.unal.tictactoe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.unal.tictactoe.logic.TicTacToeGame

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicTacToeScreen() {
    val game = remember { TicTacToeGame() }

    var board by remember { mutableStateOf(List(9) { TicTacToeGame.OPEN_SPOT }) }
    var infoText by remember { mutableStateOf("Tú empiezas.") }
    var gameOver by remember { mutableStateOf(false) }

    // --- Nuevo para la Fase 7 ---
    var humanScore by remember { mutableStateOf(0) }
    var tieScore by remember { mutableStateOf(0) }
    var computerScore by remember { mutableStateOf(0) }
    var humanStartsNext by remember { mutableStateOf(true) } // quién arranca la SIGUIENTE partida

    fun refreshBoard() {
        board = List(9) { game.getBoardOccupant(it) }
    }

    /** Ejecuta una jugada del computador y actualiza tablero + resultado. Devuelve el código de ganador. */
    fun playComputerTurn(): Int {
        val move = game.getComputerMove()
        game.setMove(TicTacToeGame.COMPUTER_PLAYER, move)
        refreshBoard()
        return game.checkForWinner()
    }

    /** Centraliza qué pasa cuando el juego termina: actualiza el texto y el marcador. */
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

        val humanStarts = humanStartsNext
        humanStartsNext = !humanStartsNext // alterna para la próxima partida

        if (humanStarts) {
            infoText = "Tú empiezas."
        } else {
            infoText = "Android empieza..."
            val winner = playComputerTurn() // primera jugada de la IA, apenas empieza la partida
            if (winner == 0) {
                infoText = "Tu turno."
            } else {
                applyResult(winner) // caso extremo: no pasa en la primera jugada, pero queda cubierto
            }
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

        if (winner == 0) {
            infoText = "Tu turno."
        } else {
            applyResult(winner)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Triqui") },
                actions = {
                    TextButton(onClick = { startNewGame() }) {
                        Text("Nueva partida")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Board(board = board, onCellClicked = ::onCellClicked)
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = infoText, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(16.dp))
            ScoreBoard(human = humanScore, ties = tieScore, computer = computerScore)
        }
    }
}

@Composable
private fun Board(board: List<Char>, onCellClicked: (Int) -> Unit) {
    Column {
        for (row in 0..2) {
            Row {
                for (col in 0..2) {
                    val location = row * 3 + col
                    Cell(value = board[location], onClick = { onCellClicked(location) })
                }
            }
        }
    }
}

@Composable
private fun Cell(value: Char, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(100.dp)
            .border(1.dp, Color.Black)
            .background(Color(0xFFF0F0F0))
            .clickable(enabled = value == TicTacToeGame.OPEN_SPOT, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val color = when (value) {
            TicTacToeGame.HUMAN_PLAYER -> Color(0xFF00C800)
            TicTacToeGame.COMPUTER_PLAYER -> Color(0xFFC80000)
            else -> Color.Transparent
        }
        Text(
            text = if (value == TicTacToeGame.OPEN_SPOT) "" else value.toString(),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun ScoreBoard(human: Int, ties: Int, computer: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(text = "Human: $human", fontWeight = FontWeight.Bold)
        Text(text = "Ties: $ties", fontWeight = FontWeight.Bold)
        Text(text = "Android: $computer", fontWeight = FontWeight.Bold)
    }
}