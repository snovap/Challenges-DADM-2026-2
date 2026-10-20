package co.edu.unal.directorio.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.unal.directorio.model.Company
import kotlinx.coroutines.launch

private enum class Screen { LIST, DETAIL, FORM }

/** Preferencias visuales (modo y paleta) que se recuerdan entre sesiones. */
private class ThemeSettings(context: Context) {
    private val prefs = context.getSharedPreferences("directorio_prefs", Context.MODE_PRIVATE)
    var dark: Boolean
        get() = prefs.getBoolean("dark", true)
        set(value) = prefs.edit().putBoolean("dark", value).apply()
    var formal: Boolean
        get() = prefs.getBoolean("formal", false)
        set(value) = prefs.edit().putBoolean("formal", value).apply()
}

/** Raíz de la app: tema, navegación por estado (lista → detalle → formulario) y mensajes. */
@Composable
fun AppRoot(viewModel: CompaniesViewModel = viewModel()) {
    val context = LocalContext.current
    val settings = remember { ThemeSettings(context) }
    var dark by rememberSaveable { mutableStateOf(settings.dark) }
    var formal by rememberSaveable { mutableStateOf(settings.formal) }
    var screen by rememberSaveable { mutableStateOf(Screen.LIST) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }

    val state by viewModel.state.collectAsState()

    // La empresa seleccionada se lee de la base por id; se recarga cuando la lista cambia.
    val loaded by produceState<Company?>(initialValue = null, selectedId, state.companies) {
        value = selectedId?.let { viewModel.findCompany(it) }
    }
    val current = loaded?.takeIf { it.id == selectedId }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { text -> scope.launch { snackbar.showSnackbar(text) } }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    fun goList() { selectedId = null; screen = Screen.LIST }

    BackHandler(enabled = screen != Screen.LIST) {
        if (screen == Screen.FORM && selectedId != null) screen = Screen.DETAIL else goList()
    }

    val toggleTheme = { dark = !dark; settings.dark = dark }
    val togglePalette = { formal = !formal; settings.formal = formal }

    AppTheme(dark = dark, style = if (formal) PaletteStyle.FORMAL else PaletteStyle.VIOLET) {
        Box(Modifier.fillMaxSize()) {
            when (screen) {
                Screen.LIST -> CompanyListScreen(
                    state = state,
                    dark = dark,
                    onToggleTheme = toggleTheme,
                    onTogglePalette = togglePalette,
                    onQueryChange = viewModel::onQueryChange,
                    onFilterChange = viewModel::onFilterChange,
                    onOpen = { selectedId = it.id; screen = Screen.DETAIL },
                    onAdd = { selectedId = null; screen = Screen.FORM },
                    onContactError = showMessage
                )

                Screen.DETAIL -> CompanyDetailScreen(
                    company = current,
                    dark = dark,
                    onBack = { goList() },
                    onToggleTheme = toggleTheme,
                    onTogglePalette = togglePalette,
                    onEdit = { screen = Screen.FORM },
                    onDelete = { selectedId?.let { id -> viewModel.delete(id) { goList() } } },
                    onContactError = showMessage
                )

                Screen.FORM -> {
                    val editing = selectedId != null
                    if (editing && current == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = LocalAppColors.current.accent)
                        }
                    } else {
                        key(selectedId) {
                            CompanyFormScreen(
                                initial = current,
                                dark = dark,
                                onBack = { if (editing) screen = Screen.DETAIL else goList() },
                                onToggleTheme = toggleTheme,
                                onTogglePalette = togglePalette,
                                onSave = { form ->
                                    viewModel.save(selectedId ?: 0L, form) { savedId ->
                                        selectedId = savedId
                                        screen = Screen.DETAIL
                                    }
                                }
                            )
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(bottom = if (screen == Screen.LIST) 96.dp else 16.dp)
            )
        }
    }
}