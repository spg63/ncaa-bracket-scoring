package com.bracket.scoring

import com.bracket.models.*

object Scorer {

    fun scoreAll(
        brackets: List<PlayerBracket>,
        structure: TournamentStructure
    ): List<PlayerScore> {
        return brackets.map { bracket -> score(bracket, structure) }
            .sortedByDescending { it.totalTraditional }
    }

    fun score(bracket: PlayerBracket, structure: TournamentStructure): PlayerScore {
        val gameMap = structure.games.associateBy { it.gameId }

        val roundBreakdown = TournamentRound.ordered.map { round ->
            val picksInRound = bracket.picks.filter { it.round == round.name }
            var correct = 0
            var gamesWithResults = 0
            var traditional = 0
            var custom = 0

            for (pick in picksInRound) {
                val game = gameMap[pick.gameId] ?: continue
                if (!game.isComplete || game.winner == null) continue

                gamesWithResults++
                if (pick.pickedTeamName.equals(game.winner.name, ignoreCase = true) ||
                    pick.pickedTeamName.equals(game.winner.shortName, ignoreCase = true)) {
                    correct++
                    traditional += round.traditionalPts
                    custom      += game.winner.seed * round.customMultiplier
                }
            }

            RoundScore(round, correct, gamesWithResults, traditional, custom)
        }

        var maxAddTraditional = 0
        var maxAddCustom = 0
        for (round in TournamentRound.ordered) {
            val picksInRound = bracket.picks.filter { it.round == round.name }
            for (pick in picksInRound) {
                val game = gameMap[pick.gameId] ?: continue
                if (!game.isComplete) {
                    maxAddTraditional += round.traditionalPts
                    maxAddCustom      += pick.pickedSeed * round.customMultiplier
                }
            }
        }

        val totalTraditional = roundBreakdown.sumOf { it.traditionalPoints }
        val totalCustom      = roundBreakdown.sumOf { it.customPoints }

        return PlayerScore(
            playerName             = bracket.playerName,
            totalTraditional       = totalTraditional,
            totalCustom            = totalCustom,
            totalCorrect           = roundBreakdown.sumOf { it.correct },
            totalGamesWithResults  = roundBreakdown.sumOf { it.gamesWithResults },
            roundBreakdown         = roundBreakdown,
            maxPossibleTraditional = totalTraditional + maxAddTraditional,
            maxPossibleCustom      = totalCustom + maxAddCustom
        )
    }
}
