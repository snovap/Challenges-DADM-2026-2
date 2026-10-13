package co.edu.unal.tictactoe.online.data

import co.edu.unal.tictactoe.logic.TicTacToeGame
import co.edu.unal.tictactoe.online.model.GameResult
import co.edu.unal.tictactoe.online.model.GameRoom
import co.edu.unal.tictactoe.online.model.GameStatus
import co.edu.unal.tictactoe.online.model.PlayerRole
import co.edu.unal.tictactoe.online.model.evaluateBoard
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Nombres de los campos en Realtime Database. Centralizados para evitar errores de tipeo. */
private object F {
    const val GAMES = "games"
    const val HOST_ID = "hostId"
    const val HOST_NAME = "hostName"
    const val GUEST_ID = "guestId"
    const val GUEST_NAME = "guestName"
    const val BOARD = "board"
    const val TURN = "turn"
    const val STATUS = "status"
    const val RESULT = "result"
    const val ROUND = "round"
    const val HOST_ONLINE = "hostOnline"
    const val GUEST_ONLINE = "guestOnline"
    const val CREATED_AT = "createdAt"
}

class FirebaseOnlineGameRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    database: FirebaseDatabase = FirebaseDatabase.getInstance()
) : OnlineGameRepository {

    private val gamesRef: DatabaseReference = database.getReference(F.GAMES)
    private val connectedRef: DatabaseReference = database.getReference(".info/connected")

    override suspend fun ensureSignedIn(): String {
        auth.currentUser?.let { return it.uid }
        val result = auth.signInAnonymously().awaitTask()
        return checkNotNull(result.user?.uid) { "La autenticación anónima no devolvió usuario" }
    }

    override fun observeWaitingGames(): Flow<List<GameRoom>> = callbackFlow {
        val query = gamesRef.orderByChild(F.STATUS).equalTo(GameStatus.WAITING.name)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val rooms = snapshot.children
                    .mapNotNull { it.toGameRoom() }
                    .filter { it.hostOnline } // oculta anfitriones desconectados
                    .sortedByDescending { it.createdAt }
                trySend(rooms)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    override fun observeGame(gameId: String): Flow<GameRoom?> = callbackFlow {
        val ref = gamesRef.child(gameId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.toGameRoom())
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun createGame(hostId: String, hostName: String): String {
        val gameRef = gamesRef.push()
        val gameId = checkNotNull(gameRef.key) { "Firebase no generó la clave del juego" }
        val payload = mapOf<String, Any>(
            F.HOST_ID to hostId,
            F.HOST_NAME to hostName,
            F.BOARD to GameRoom.EMPTY_BOARD,
            F.TURN to PlayerRole.HOST.symbol.toString(),
            F.STATUS to GameStatus.WAITING.name,
            F.RESULT to GameResult.IN_PROGRESS,
            F.ROUND to 1,
            F.HOST_ONLINE to true,
            F.GUEST_ONLINE to false,
            F.CREATED_AT to ServerValue.TIMESTAMP
        )
        gameRef.setValue(payload).awaitTask()
        return gameId
    }

    override suspend fun joinGame(gameId: String, guestId: String, guestName: String): JoinResult {
        val joined = transact(gameId) { data ->
            val canJoin = data.string(F.STATUS) == GameStatus.WAITING.name &&
                    data.string(F.GUEST_ID) == null &&
                    data.string(F.HOST_ID) != guestId
            if (!canJoin) return@transact false

            data.child(F.GUEST_ID).value = guestId
            data.child(F.GUEST_NAME).value = guestName
            data.child(F.GUEST_ONLINE).value = true
            data.child(F.STATUS).value = GameStatus.PLAYING.name
            true
        }
        return if (joined) JoinResult.JOINED else JoinResult.NOT_AVAILABLE
    }

    override suspend fun makeMove(gameId: String, playerId: String, cell: Int): MoveResult {
        if (cell !in 0 until TicTacToeGame.BOARD_SIZE) return MoveResult.REJECTED

        val applied = transact(gameId) { data ->
            if (data.string(F.STATUS) != GameStatus.PLAYING.name) return@transact false

            val role = data.roleOf(playerId) ?: return@transact false
            if (data.string(F.TURN)?.firstOrNull() != role.symbol) return@transact false

            val board = data.string(F.BOARD)
            val cellIsFree = board != null &&
                    board.length == TicTacToeGame.BOARD_SIZE &&
                    board[cell] == TicTacToeGame.OPEN_SPOT
            if (board == null || !cellIsFree) return@transact false

            val newBoard = board.replaceRange(cell, cell + 1, role.symbol.toString())
            val outcome = evaluateBoard(newBoard)

            data.child(F.BOARD).value = newBoard
            if (outcome == GameResult.IN_PROGRESS) {
                data.child(F.TURN).value = role.opponent.symbol.toString()
            } else {
                data.child(F.STATUS).value = GameStatus.FINISHED.name
                data.child(F.RESULT).value = outcome
            }
            true
        }
        return if (applied) MoveResult.ACCEPTED else MoveResult.REJECTED
    }

    override suspend fun requestRematch(gameId: String, playerId: String): Boolean =
        transact(gameId) { data ->
            val isRematchable = data.string(F.STATUS) == GameStatus.FINISHED.name &&
                    data.int(F.RESULT) != GameResult.ABANDONED &&
                    data.roleOf(playerId) != null
            if (!isRematchable) return@transact false

            val nextRound = data.int(F.ROUND, 1) + 1
            data.child(F.BOARD).value = GameRoom.EMPTY_BOARD
            data.child(F.TURN).value = GameRoom.starterOfRound(nextRound).symbol.toString()
            data.child(F.STATUS).value = GameStatus.PLAYING.name
            data.child(F.RESULT).value = GameResult.IN_PROGRESS
            data.child(F.ROUND).value = nextRound
            true
        }

    override suspend fun leaveGame(gameId: String, playerId: String) {
        transact(gameId) { data ->
            val role = data.roleOf(playerId) ?: return@transact false
            val status = data.string(F.STATUS)

            // El anfitrión cancela su propio juego en espera: se elimina la sala.
            if (status == GameStatus.WAITING.name && role == PlayerRole.HOST) {
                data.value = null
                return@transact true
            }

            data.child(role.onlineKey).value = false
            if (status == GameStatus.PLAYING.name) {
                data.child(F.STATUS).value = GameStatus.FINISHED.name
                data.child(F.RESULT).value = GameResult.ABANDONED
            }
            // El último en salir elimina la sala.
            if (!data.bool(role.opponent.onlineKey)) data.value = null
            true
        }
    }

    override fun trackPresence(gameId: String, role: PlayerRole): AutoCloseable {
        val onlineRef = gamesRef.child(gameId).child(role.onlineKey)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.value == true) {
                    // Primero se programa la desconexión y luego se marca presencia.
                    onlineRef.onDisconnect().setValue(false)
                    onlineRef.setValue(true)
                }
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        connectedRef.addValueEventListener(listener)
        return AutoCloseable {
            connectedRef.removeEventListener(listener)
            onlineRef.onDisconnect().cancel()
        }
    }

    /**
     * Ejecuta una transacción sobre la sala. [mutate] devuelve true si aplicó cambios y false para abortar.
     *
     * Reglas para [mutate]: Firebase puede ejecutarla varias veces, así que debe **validar primero y
     * modificar después**, sin efectos secundarios fuera de [MutableData].
     */
    private suspend fun transact(gameId: String, mutate: (MutableData) -> Boolean): Boolean =
        suspendCancellableCoroutine { continuation ->
            gamesRef.child(gameId).runTransaction(object : Transaction.Handler {
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    // Caché local vacía: el SDK reintenta con el dato real del servidor.
                    if (currentData.value == null) return Transaction.success(currentData)
                    return if (mutate(currentData)) {
                        Transaction.success(currentData)
                    } else {
                        Transaction.abort()
                    }
                }

                override fun onComplete(
                    error: DatabaseError?,
                    committed: Boolean,
                    currentData: DataSnapshot?
                ) {
                    if (error != null) {
                        continuation.resumeWithException(error.toException())
                    } else {
                        continuation.resume(committed && currentData?.value != null)
                    }
                }
            })
        }
}

// ---------- Mapeo y utilidades privadas de la capa de datos ----------

private val PlayerRole.onlineKey: String
    get() = if (this == PlayerRole.HOST) F.HOST_ONLINE else F.GUEST_ONLINE

private fun DataSnapshot.string(key: String): String? = child(key).value as? String
private fun DataSnapshot.int(key: String, default: Int = 0): Int =
    (child(key).value as? Number)?.toInt() ?: default
private fun DataSnapshot.long(key: String): Long = (child(key).value as? Number)?.toLong() ?: 0L
private fun DataSnapshot.bool(key: String): Boolean = child(key).value as? Boolean ?: false

private fun MutableData.string(key: String): String? = child(key).value as? String
private fun MutableData.int(key: String, default: Int = 0): Int =
    (child(key).value as? Number)?.toInt() ?: default
private fun MutableData.bool(key: String): Boolean = child(key).value as? Boolean ?: false

private fun MutableData.roleOf(playerId: String): PlayerRole? = when (playerId) {
    string(F.HOST_ID) -> PlayerRole.HOST
    string(F.GUEST_ID) -> PlayerRole.GUEST
    else -> null
}

/** Convierte un nodo de Firebase en [GameRoom]. Devuelve null si el nodo no existe o está incompleto. */
private fun DataSnapshot.toGameRoom(): GameRoom? {
    val id = key ?: return null
    val hostId = string(F.HOST_ID) ?: return null
    val status = string(F.STATUS)
        ?.let { name -> GameStatus.entries.firstOrNull { it.name == name } }
        ?: return null

    return GameRoom(
        id = id,
        hostId = hostId,
        hostName = string(F.HOST_NAME).orEmpty(),
        guestId = string(F.GUEST_ID),
        guestName = string(F.GUEST_NAME),
        board = string(F.BOARD) ?: GameRoom.EMPTY_BOARD,
        turn = string(F.TURN)?.firstOrNull() ?: PlayerRole.HOST.symbol,
        status = status,
        result = int(F.RESULT),
        round = int(F.ROUND, 1),
        hostOnline = bool(F.HOST_ONLINE),
        guestOnline = bool(F.GUEST_ONLINE),
        createdAt = long(F.CREATED_AT)
    )
}