package co.edu.unal.tictactoe.online.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.edu.unal.tictactoe.online.data.FirebaseOnlineGameRepository
import co.edu.unal.tictactoe.online.data.JoinResult
import co.edu.unal.tictactoe.online.data.OnlineGameRepository
import co.edu.unal.tictactoe.online.model.GameRoom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado único que consume la UI (lobby y partida). */
data class OnlineUiState(
    val playerId: String? = null,
    val playerName: String = "",
    val connecting: Boolean = true,
    val waitingGames: List<GameRoom> = emptyList(),
    val currentGameId: String? = null,
    val game: GameRoom? = null,
    val busy: Boolean = false,
    val message: String? = null
)

/**
 * Orquesta lobby y partida. Sobrevive a rotaciones; la UI solo observa [state].
 * Se acopla a la interfaz [OnlineGameRepository] (la implementación Firebase se crea aquí por simplicidad).
 */
class OnlineViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: OnlineGameRepository = FirebaseOnlineGameRepository()
    private val prefs = application.getSharedPreferences("ttt_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        OnlineUiState(playerName = prefs.getString(KEY_NAME, "").orEmpty())
    )
    val state: StateFlow<OnlineUiState> = _state.asStateFlow()

    private var lobbyJob: Job? = null
    private var gameJob: Job? = null
    private var presence: AutoCloseable? = null

    init { connect() }

    fun connect() {
        _state.update { it.copy(connecting = true, message = null) }
        viewModelScope.launch {
            try {
                val uid = repository.ensureSignedIn()
                _state.update { it.copy(playerId = uid, connecting = false) }
                startLobby()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(connecting = false) }
                fail("No se pudo conectar. Revisa tu internet.")
            }
        }
    }

    fun setPlayerName(name: String) {
        val clean = name.take(MAX_NAME)
        prefs.edit().putString(KEY_NAME, clean).apply()
        _state.update { it.copy(playerName = clean) }
    }

    fun createGame() = launchEnter { uid, name -> repository.createGame(uid, name) }

    fun joinGame(gameId: String) = launchEnter { uid, name ->
        if (repository.joinGame(gameId, uid, name) == JoinResult.JOINED) gameId else null
    }

    fun makeMove(cell: Int) {
        val s = _state.value
        val gameId = s.currentGameId ?: return
        val uid = s.playerId ?: return
        viewModelScope.launch {
            runCatching { repository.makeMove(gameId, uid, cell) }
                .onFailure { fail("No se pudo enviar la jugada.") }
        }
    }

    fun rematch() {
        val s = _state.value
        val gameId = s.currentGameId ?: return
        val uid = s.playerId ?: return
        viewModelScope.launch { runCatching { repository.requestRematch(gameId, uid) } }
    }

    fun leaveGame() {
        val s = _state.value
        val gameId = s.currentGameId
        val uid = s.playerId
        closeGame()
        if (gameId != null && uid != null) {
            viewModelScope.launch { runCatching { repository.leaveGame(gameId, uid) } }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    // ---------- Internos ----------

    private fun startLobby() {
        lobbyJob?.cancel()
        lobbyJob = viewModelScope.launch {
            repository.observeWaitingGames()
                .catch { fail("Error al cargar los juegos disponibles.") }
                .collect { games -> _state.update { it.copy(waitingGames = games) } }
        }
    }

    private fun launchEnter(action: suspend (uid: String, name: String) -> String?) {
        val s = _state.value
        val uid = s.playerId ?: return
        val name = s.playerName.trim()
        if (name.isEmpty()) return fail("Escribe tu nombre para jugar.")
        if (s.busy) return

        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val gameId = action(uid, name)
                if (gameId == null) fail("Ese juego ya no está disponible.") else openGame(gameId, uid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("No se pudo completar la acción. Intenta de nuevo.")
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun openGame(gameId: String, uid: String) {
        lobbyJob?.cancel()
        gameJob?.cancel()
        presence?.close()
        presence = null
        _state.update { it.copy(currentGameId = gameId, game = null) }

        var hadRoom = false
        gameJob = viewModelScope.launch {
            repository.observeGame(gameId)
                .catch { fail("Se perdió la conexión con la partida.") }
                .collect { room ->
                    if (room == null) {
                        if (hadRoom) closeGame("La partida ya no existe.")
                        return@collect
                    }
                    hadRoom = true
                    if (presence == null) {
                        room.roleOf(uid)?.let { presence = repository.trackPresence(gameId, it) }
                    }
                    _state.update { it.copy(game = room) }
                }
        }
    }

    private fun closeGame(message: String? = null) {
        gameJob?.cancel()
        gameJob = null
        presence?.close()
        presence = null
        _state.update { it.copy(currentGameId = null, game = null, message = message) }
        startLobby()
    }

    private fun fail(text: String) = _state.update { it.copy(message = text) }

    override fun onCleared() {
        val s = _state.value
        presence?.close()
        val gameId = s.currentGameId
        val uid = s.playerId
        if (gameId != null && uid != null) {
            // Salida "best effort" al cerrar la app: viewModelScope ya está cancelado.
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                runCatching { repository.leaveGame(gameId, uid) }
            }
        }
    }

    private companion object {
        const val KEY_NAME = "playerName"
        const val MAX_NAME = 20
    }
}
