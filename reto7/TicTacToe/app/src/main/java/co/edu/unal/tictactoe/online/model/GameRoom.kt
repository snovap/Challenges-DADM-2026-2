package co.edu.unal.tictactoe.online.model

import co.edu.unal.tictactoe.logic.TicTacToeGame

/** Ciclo de vida de una partida online. */
enum class GameStatus { WAITING, PLAYING, FINISHED }

/** Rol dentro de una sala. El anfitrión siempre juega con X y el invitado con O. */
enum class PlayerRole(val symbol: Char) {
    HOST('X'),
    GUEST('O');

    val opponent: PlayerRole
        get() = if (this == HOST) GUEST else HOST
}

/** Códigos de resultado. Los valores 0..3 coinciden con [TicTacToeGame.checkForWinner]. */
object GameResult {
    const val IN_PROGRESS = 0
    const val TIE = 1
    const val HOST_WINS = 2
    const val GUEST_WINS = 3
    const val ABANDONED = 4
}

/** Estado completo de una sala de juego, tal como se lee de la nube. */
data class GameRoom(
    val id: String,
    val hostId: String,
    val hostName: String,
    val guestId: String? = null,
    val guestName: String? = null,
    val board: String = EMPTY_BOARD,
    val turn: Char = PlayerRole.HOST.symbol,
    val status: GameStatus = GameStatus.WAITING,
    val result: Int = GameResult.IN_PROGRESS,
    val round: Int = 1,
    val hostOnline: Boolean = false,
    val guestOnline: Boolean = false,
    val createdAt: Long = 0L
) {
    /** Rol de [playerId] en esta sala, o null si no participa. */
    fun roleOf(playerId: String): PlayerRole? = when (playerId) {
        hostId -> PlayerRole.HOST
        guestId -> PlayerRole.GUEST
        else -> null
    }

    fun isOnline(role: PlayerRole): Boolean =
        if (role == PlayerRole.HOST) hostOnline else guestOnline

    fun nameOf(role: PlayerRole): String =
        if (role == PlayerRole.HOST) hostName else guestName.orEmpty()

    companion object {
        val EMPTY_BOARD: String =
            TicTacToeGame.OPEN_SPOT.toString().repeat(TicTacToeGame.BOARD_SIZE)

        /** Quién abre una ronda: el anfitrión en las impares, el invitado en las pares. */
        fun starterOfRound(round: Int): PlayerRole =
            if (round % 2 == 1) PlayerRole.HOST else PlayerRole.GUEST
    }
}

/** Evalúa un tablero serializado reutilizando la lógica del juego. Devuelve 0..3 como checkForWinner(). */
fun evaluateBoard(board: String): Int {
    val game = TicTacToeGame()
    game.setBoardState(board.toCharArray())
    return game.checkForWinner()
}