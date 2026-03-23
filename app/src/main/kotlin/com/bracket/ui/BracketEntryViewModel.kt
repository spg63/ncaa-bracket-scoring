package com.bracket.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bracket.models.*
import com.bracket.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

data class EntryState(
    val playerName: String = "",
    val pickedWinners: Map<String, Team> = emptyMap(),
    val picks: List<PlayerPick> = emptyList(),
    val isSaving: Boolean = false,
    val savedBracket: PlayerBracket? = null
) {
    val pickCount get() = picks.size
}

class BracketEntryViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx get() = getApplication<Application>()

    private val _state = MutableStateFlow(EntryState())
    val state = _state.asStateFlow()

    var structure: TournamentStructure? = null
        private set

    fun startEntry(
        playerName: String,
        tournamentStructure: TournamentStructure,
        existingBracket: PlayerBracket? = null
    ) {
        structure = tournamentStructure

        val initialWinners = mutableMapOf<String, Team>()
        val initialPicks   = mutableListOf<PlayerPick>()

        if (existingBracket != null) {
            // Build a name → Team lookup from all teams in the structure so we can
            // correctly resolve picks even when a player picked an upset (the picked
            // team may not appear in that game's team1/team2 in the live structure).
            val teamByName = tournamentStructure.games
                .flatMap { listOf(it.team1, it.team2) }
                .associateBy { it.name }
                .plus(
                    tournamentStructure.games
                        .flatMap { listOf(it.team1, it.team2) }
                        .associateBy { it.shortName }
                )

            for (pick in existingBracket.picks) {
                val team = teamByName[pick.pickedTeamName]
                    ?: Team(pick.pickedTeamName, pick.pickedTeamName, pick.pickedSeed, "")
                initialWinners[pick.gameId] = team
                initialPicks += pick
            }
        }

        _state.value = EntryState(
            playerName    = playerName,
            pickedWinners = initialWinners,
            picks         = initialPicks
        )
    }

    /** teamIndex: 0 = team1, 1 = team2 (as resolved in the UI) */
    fun pick(gameId: String, teamIndex: Int) {
        val s   = _state.value
        val str = structure ?: return
        val game = str.games.find { it.gameId == gameId } ?: return

        val team1  = resolveTeam(game.team1, game.sourceGame1Id, s.pickedWinners)
        val team2  = resolveTeam(game.team2, game.sourceGame2Id, s.pickedWinners)
        val picked = if (teamIndex == 0) team1 else team2

        // No-op if same pick
        if (s.pickedWinners[gameId]?.name == picked.name) return

        // Invalidate all games that transitively depend on this one
        val downstream = findDownstream(gameId, str.games)

        val newWinners = s.pickedWinners
            .filterKeys { it !in downstream && it != gameId }
            .plus(gameId to picked)

        val newPicks = s.picks
            .filter { it.gameId !in downstream && it.gameId != gameId }
            .plus(PlayerPick(gameId, game.round, picked.name, picked.seed))

        _state.update { it.copy(pickedWinners = newWinners, picks = newPicks) }
    }

    fun save() {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val bracket = PlayerBracket(
                playerName = s.playerName,
                year       = structure?.year ?: LocalDateTime.now().year,
                picks      = s.picks,
                enteredAt  = LocalDateTime.now().toString()
            )
            withContext(Dispatchers.IO) { Storage.saveBracket(ctx, bracket) }
            _state.update { it.copy(isSaving = false, savedBracket = bracket) }
        }
    }

    fun reset() {
        _state.value = EntryState()
        structure    = null
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun findDownstream(gameId: String, allGames: List<BracketGame>): Set<String> {
        val result = mutableSetOf<String>()
        val queue  = ArrayDeque<String>()
        queue.add(gameId)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            allGames
                .filter { it.sourceGame1Id == id || it.sourceGame2Id == id }
                .forEach { dep -> if (result.add(dep.gameId)) queue.add(dep.gameId) }
        }
        return result
    }

    fun resolveTeam(
        structureTeam: Team,
        sourceGameId: String?,
        pickedWinners: Map<String, Team>
    ): Team {
        if (sourceGameId != null) pickedWinners[sourceGameId]?.let { return it }
        return structureTeam
    }
}
