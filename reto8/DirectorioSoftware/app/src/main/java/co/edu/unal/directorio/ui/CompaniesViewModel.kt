package co.edu.unal.directorio.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.edu.unal.directorio.data.CompanyRepository
import co.edu.unal.directorio.data.SqliteCompanyRepository
import co.edu.unal.directorio.model.Classification
import co.edu.unal.directorio.model.Company
import co.edu.unal.directorio.model.CompanyForm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CompaniesUiState(
    val companies: List<Company> = emptyList(),
    val query: String = "",
    val classificationFilter: Classification? = null,
    val loading: Boolean = true,
    val message: String? = null
) {
    val isFiltering: Boolean get() = query.isNotBlank() || classificationFilter != null
}

/** Estado único de la lista + operaciones CRUD. Sobrevive a rotaciones. */
class CompaniesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CompanyRepository = SqliteCompanyRepository(application)

    private val _state = MutableStateFlow(CompaniesUiState())
    val state: StateFlow<CompaniesUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init { reload() }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        reload()
    }

    fun onFilterChange(classification: Classification?) {
        _state.update { it.copy(classificationFilter = classification) }
        reload()
    }

    suspend fun findCompany(id: Long): Company? = repository.getById(id)

    /** Crea (id = 0) o actualiza. La UI ya validó el formulario antes de llamar. */
    private var saveJob: Job? = null

    /** Crea (id = 0) o actualiza. Devuelve el id guardado para abrir el detalle. */
    fun save(id: Long, form: CompanyForm, onSaved: (Long) -> Unit) {
        if (saveJob?.isActive == true) return // evita duplicados por doble toque
        saveJob = viewModelScope.launch {
            try {
                val company = form.toCompany(id)
                val savedId = if (id == 0L) repository.insert(company) else { repository.update(company); id }
                reload()
                onSaved(savedId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage("No se pudo guardar la empresa.")
            }
        }
    }

    fun delete(id: Long, onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.delete(id)
                reload()
                onDeleted()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage("No se pudo eliminar la empresa.")
            }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private fun reload() {
        loadJob?.cancel() // evita resultados viejos si el usuario escribe rápido
        loadJob = viewModelScope.launch {
            val current = _state.value
            val list = repository.getAll(current.query, current.classificationFilter)
            _state.update { it.copy(companies = list, loading = false) }
        }
    }

    private fun showMessage(text: String) = _state.update { it.copy(message = text) }
}