package co.edu.unal.tictactoe.online.data

import co.edu.unal.tictactoe.online.model.GameRoom
import co.edu.unal.tictactoe.online.model.PlayerRole
import kotlinx.coroutines.flow.Flow

enum class JoinResult { JOINED, NOT_AVAILABLE }

enum class MoveResult { ACCEPTED, REJECTED }

/** Contrato de acceso a las partidas online. La UI depende de esta interfaz, no de Firebase. */
interface OnlineGameRepository {

    /** Garantiza una sesión (anónima) y devuelve el uid del jugador. */
    suspend fun ensureSignedIn(): String

    /** Juegos esperando oponente, en vivo, del más reciente al más antiguo. */
    fun observeWaitingGames(): Flow<List<GameRoom>>

    /** Observa una sala. Emite null cuando la sala deja de existir. */
    fun observeGame(gameId: String): Flow<GameRoom?>

    /** Crea una sala en espera y devuelve su id. */
    suspend fun createGame(hostId: String, hostName: String): String

    suspend fun joinGame(gameId: String, guestId: String, guestName: String): JoinResult

    suspend fun makeMove(gameId: String, playerId: String, cell: Int): MoveResult

    /** Reinicia una partida terminada alternando quién empieza. Devuelve true si se aplicó. */
    suspend fun requestRematch(gameId: String, playerId: String): Boolean

    /** Abandona la sala. El último jugador en salir la elimina. */
    suspend fun leaveGame(gameId: String, playerId: String)

    /** Marca al jugador como conectado mientras no se cierre el resultado. Cerrarlo libera los listeners. */
    fun trackPresence(gameId: String, role: PlayerRole): AutoCloseable
}