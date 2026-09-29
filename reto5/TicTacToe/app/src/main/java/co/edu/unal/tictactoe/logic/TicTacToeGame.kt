package co.edu.unal.tictactoe.logic

import kotlin.random.Random

/**
 * Lógica pura del juego de Triqui (Tic-Tac-Toe), sin ninguna dependencia de Android.
 * Adaptado del TicTacToeConsole.java original de Frank McCown (Harding University).
 */
class TicTacToeGame {

    companion object {
        const val HUMAN_PLAYER = 'X'
        const val COMPUTER_PLAYER = 'O'
        const val OPEN_SPOT = ' '
        const val BOARD_SIZE = 9
    }

    /** Niveles de dificultad del computador (equivalente al enum del documento original). */
    enum class DifficultyLevel { EASY, HARDER, EXPERT }

    // Nivel de dificultad actual. Es un 'var' público, así que no necesitamos
    // getter/setter explícitos como en Java: Kotlin los genera automáticamente.
    var difficultyLevel: DifficultyLevel = DifficultyLevel.EXPERT

    private val mBoard = CharArray(BOARD_SIZE) { OPEN_SPOT }
    private val random = Random(System.currentTimeMillis())

    fun clearBoard() {
        for (i in mBoard.indices) mBoard[i] = OPEN_SPOT
    }

    fun setMove(player: Char, location: Int) {
        if (location in mBoard.indices && mBoard[location] == OPEN_SPOT) {
            mBoard[location] = player
        }
    }

    fun getBoardOccupant(location: Int): Char = mBoard[location]

    /**
     * Calcula la jugada del computador según el nivel de dificultad activo:
     * - EASY: siempre aleatoria.
     * - HARDER: gana si puede, si no, aleatoria.
     * - EXPERT: gana si puede, si no bloquea, si no, aleatoria.
     */
    fun getComputerMove(): Int {
        return when (difficultyLevel) {
            DifficultyLevel.EASY ->
                getRandomMove()
            DifficultyLevel.HARDER ->
                getWinningMove() ?: getRandomMove()
            DifficultyLevel.EXPERT ->
                getWinningMove() ?: getBlockingMove() ?: getRandomMove()
        }
    }

    /** Retorna la jugada que le da la victoria inmediata al computador, o null si no existe. */
    private fun getWinningMove(): Int? = winningMove(COMPUTER_PLAYER)

    /** Retorna la jugada que bloquea una victoria inminente del humano, o null si no existe. */
    private fun getBlockingMove(): Int? = winningMove(HUMAN_PLAYER)

    /** Retorna una jugada aleatoria entre las casillas libres. */
    private fun getRandomMove(): Int {
        val emptySpots = mBoard.indices.filter { mBoard[it] == OPEN_SPOT }
        return emptySpots[random.nextInt(emptySpots.size)]
    }

    /**
     * Busca una línea donde 'player' tiene 2 fichas y la tercera está libre.
     * Retorna esa posición libre, o null si no existe tal línea.
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

    fun checkForWinner(): Int {
        for (line in WINNING_LINES) {
            val (a, b, c) = line
            if (mBoard[a] != OPEN_SPOT && mBoard[a] == mBoard[b] && mBoard[b] == mBoard[c]) {
                return if (mBoard[a] == HUMAN_PLAYER) 2 else 3
            }
        }
        if (mBoard.none { it == OPEN_SPOT }) return 1
        return 0
    }

    private val WINNING_LINES = listOf(
        listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
        listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
        listOf(0, 4, 8), listOf(2, 4, 6)
    )
}