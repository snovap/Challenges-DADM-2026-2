package co.edu.unal.tictactoe.logic

import kotlin.random.Random

/**
 * Lógica pura del juego de Triqui (Tic-Tac-Toe), sin ninguna dependencia de Android.
 * Adaptado del TicTacToeConsole.java original de Frank McCown (Harding University),
 * manteniendo fielmente el mismo algoritmo de decisión del computador.
 */
class TicTacToeGame {

    companion object {
        const val HUMAN_PLAYER = 'X'
        const val COMPUTER_PLAYER = 'O'
        const val OPEN_SPOT = ' '
        const val BOARD_SIZE = 9
    }

    // Representación interna del tablero (posiciones 0-8)
    private val mBoard = CharArray(BOARD_SIZE) { OPEN_SPOT }
    private val random = Random(System.currentTimeMillis())

    /** Limpia el tablero dejando todas las posiciones en OPEN_SPOT. */
    fun clearBoard() {
        for (i in mBoard.indices) mBoard[i] = OPEN_SPOT
    }

    /**
     * Coloca la ficha de 'player' en 'location', solo si esa posición
     * está libre. Si no lo está, el tablero no se modifica.
     */
    fun setMove(player: Char, location: Int) {
        if (location in mBoard.indices && mBoard[location] == OPEN_SPOT) {
            mBoard[location] = player
        }
    }

    /** Permite a la UI leer qué hay en una posición del tablero (solo lectura). */
    fun getBoardOccupant(location: Int): Char = mBoard[location]

    /**
     * Calcula la mejor jugada para el computador, replicando el algoritmo original:
     * 1. Si hay una jugada que le da la victoria inmediata, la toma.
     * 2. Si no, si hay una jugada que bloquea una victoria inminente del humano, la toma.
     * 3. Si no, elige una casilla vacía al azar.
     *
     * @return La posición (0-8) elegida. Debes llamar a setMove() para aplicarla.
     */
    fun getComputerMove(): Int {
        // 1. ¿Hay una jugada que gane el juego para el computador?
        winningMove(COMPUTER_PLAYER)?.let { return it }

        // 2. ¿Hay una jugada que bloquee una victoria del humano?
        winningMove(HUMAN_PLAYER)?.let { return it }

        // 3. Jugada aleatoria entre las casillas libres
        val emptySpots = mBoard.indices.filter { mBoard[it] == OPEN_SPOT }
        return emptySpots[random.nextInt(emptySpots.size)]
    }

    /**
     * Busca si existe una línea (fila, columna o diagonal) donde 'player'
     * ya tiene 2 fichas y la tercera posición está libre. Si existe,
     * retorna esa posición libre (la jugada ganadora/bloqueadora).
     */
    private fun winningMove(player: Char): Int? {
        for (line in WINNING_LINES) {
            val values = line.map { mBoard[it] }
            val playerCount = values.count { it == player }
            val openCount = values.count { it == OPEN_SPOT }
            if (playerCount == 2 && openCount == 1) {
                return line[values.indexOf(OPEN_SPOT)]
            }
        }
        return null
    }

    /**
     * Revisa el tablero y determina el estado del juego.
     * @return 0 si no hay ganador aún y quedan casillas libres,
     *         1 si es empate,
     *         2 si ganó el humano (X),
     *         3 si ganó el computador (O).
     */
    fun checkForWinner(): Int {
        for (line in WINNING_LINES) {
            val (a, b, c) = line
            if (mBoard[a] != OPEN_SPOT && mBoard[a] == mBoard[b] && mBoard[b] == mBoard[c]) {
                return if (mBoard[a] == HUMAN_PLAYER) 2 else 3
            }
        }
        if (mBoard.none { it == OPEN_SPOT }) return 1 // empate: no quedan espacios
        return 0 // el juego sigue
    }

    // Las 8 combinaciones ganadoras posibles: 3 filas, 3 columnas, 2 diagonales
    private val WINNING_LINES = listOf(
        listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), // filas
        listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), // columnas
        listOf(0, 4, 8), listOf(2, 4, 6)                   // diagonales
    )
}