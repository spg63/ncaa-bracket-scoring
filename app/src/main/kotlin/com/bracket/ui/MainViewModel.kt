package com.bracket.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bracket.models.PlayerBracket
import com.bracket.models.PlayerScore
import com.bracket.models.TournamentStructure
import com.bracket.results.ResultsFetcher
import com.bracket.scoring.Scorer
import com.bracket.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ScoringMode { TRADITIONAL, CUSTOM }

data class AppState(
    val structure: TournamentStructure? = null,
    val brackets: List<PlayerBracket> = emptyList(),
    val scores: List<PlayerScore> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val scoringMode: ScoringMode = ScoringMode.TRADITIONAL
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx get() = getApplication<Application>()

    private val _state = MutableStateFlow(AppState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, successMessage = null) }
            val fetched = ResultsFetcher.fetchTournamentStructure()
            if (fetched != null) {
                withContext(Dispatchers.IO) { Storage.saveTournamentStructure(ctx, fetched) }
            }
            val structure = fetched
                ?: withContext(Dispatchers.IO) { Storage.loadCachedTournamentStructure(ctx) }
                ?: _state.value.structure
            val brackets  = withContext(Dispatchers.IO) { Storage.loadAllBrackets(ctx) }
            val scores    = if (structure != null) Scorer.scoreAll(brackets, structure) else emptyList()
            _state.update {
                it.copy(
                    isLoading      = false,
                    structure      = structure,
                    brackets       = brackets,
                    scores         = scores,
                    error          = if (fetched == null) "Could not fetch data. Using cached results." else null,
                    successMessage = if (fetched != null) "Scores updated." else null
                )
            }
        }
    }

    fun reloadBrackets() {
        viewModelScope.launch {
            val brackets = withContext(Dispatchers.IO) { Storage.loadAllBrackets(ctx) }
            val scores   = _state.value.structure?.let { Scorer.scoreAll(brackets, it) } ?: emptyList()
            _state.update { it.copy(brackets = brackets, scores = scores) }
        }
    }

    fun setScoringMode(mode: ScoringMode) {
        val sorted = when (mode) {
            ScoringMode.TRADITIONAL -> _state.value.scores.sortedByDescending { it.totalTraditional }
            ScoringMode.CUSTOM      -> _state.value.scores.sortedByDescending { it.totalCustom }
        }
        _state.update { it.copy(scoringMode = mode, scores = sorted) }
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearSuccessMessage() = _state.update { it.copy(successMessage = null) }
}
