package com.bracket.scoring

import com.bracket.models.*

object Scorer {

    /**
     * Score all player brackets against a tournament structure that includes actual results.
     * Returns a list of [PlayerScore] sorted descending by traditional score.
     */
    fun scoreAll(
        brackets: List<PlayerBracket>,
        structure: TournamentStructure
    ): List<PlayerScore> {
        return brackets.map { bracket -> score(bracket, structure) }
            .sortedByDescending { it.totalTraditional }
    }

    /**
     * Score one player bracket against actual results.
     *
     * Traditional scoring: correct pick in round → [TournamentRound.traditionalPts]
     * Custom scoring:      correct pick in round → winner_seed × [TournamentRound.customMultiplier]
     */
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

        // Max possible: add points for all correct picks in games not yet played
        var maxAddTraditional = 0
        var maxAddCustom = 0
        for (round in TournamentRound.ordered) {
            val picksInRound = bracket.picks.filter { it.round == round.name }
            for (pick in picksInRound) {
                val game = gameMap[pick.gameId] ?: continue
                if (!game.isComplete) {
                    // Assume the picked team wins for max possible
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

    // ─── Display Helpers ──────────────────────────────────────────────────────

    fun printLeaderboard(scores: List<PlayerScore>, sortByCustom: Boolean = false) {
        val sorted = if (sortByCustom) scores.sortedByDescending { it.totalCustom }
                     else              scores.sortedByDescending { it.totalTraditional }

        val scoreFn: (PlayerScore) -> Int = if (sortByCustom) { s -> s.totalCustom } else { s -> s.totalTraditional }
        val roundPtsFn: (RoundScore) -> Int = if (sortByCustom) { rs -> rs.customPoints } else { rs -> rs.traditionalPoints }
        val maxFn: (PlayerScore) -> Int = if (sortByCustom) { s -> s.maxPossibleCustom } else { s -> s.maxPossibleTraditional }
        val label = if (sortByCustom) "CUSTOM  " else "TRAD    "

        println()
        // Header
        println("╔══════════════════════════════════════════════════════════════════════════════╗")
        println("║              NCAA BRACKET LEADERBOARD                                       ║")
        println("╠══════════════════════════════════════════════════════════════════════════════╣")
        println("║  #  Name                   R64   R32   S16    E8    FF    CH  │ ${label}  Max ║")
        println("╠══════════════════════════════════════════════════════════════════════════════╣")

        sorted.forEachIndexed { idx, s ->
            val rank   = (idx + 1).toString().padStart(2)
            val name   = s.playerName.padEnd(22).take(22)
            val rounds = TournamentRound.ordered.joinToString("  ") { round ->
                val rs = s.roundBreakdown.find { it.round == round }
                roundPtsFn(rs ?: RoundScore(round, 0, 0, 0, 0)).toString().padStart(4)
            }
            val total  = scoreFn(s).toString().padStart(4)
            val max    = maxFn(s).toString().padStart(4)
            println("║ $rank  $name  $rounds  │  $total  $max ║")
        }

        println("╠══════════════════════════════════════════════════════════════════════════════╣")
        // Round completion status footer
        val anyScore = sorted.firstOrNull()
        if (anyScore != null) {
            val roundStatus = TournamentRound.ordered.joinToString("  ") { round ->
                val rs = anyScore.roundBreakdown.find { it.round == round }
                val done = rs?.gamesWithResults ?: 0
                val total = round.gameCount
                "$done/$total".padStart(4)
            }
            println("║     Games decided:         $roundStatus  │              ║")
        }
        println("╚══════════════════════════════════════════════════════════════════════════════╝")
        println()
    }

    fun printDetailedScore(score: PlayerScore) {
        println()
        println("┌─── ${score.playerName} ─────────────────────────────────────────────────────")
        println("│ Total: ${score.totalTraditional} pts traditional  |  ${score.totalCustom} pts custom  |  ${score.totalCorrect}/${score.totalGamesWithResults} correct")
        println("│ Max possible: ${score.maxPossibleTraditional} traditional  |  ${score.maxPossibleCustom} custom")
        println("├─────────────────────────────────────────────────────────────────────────────")
        println("│  Round              Done    Correct   Trad Pts   Custom Pts")
        println("│  ──────────────────────────────────────────────────────────")
        for (rs in score.roundBreakdown) {
            val roundName = rs.round.displayName.padEnd(16)
            val done      = "${rs.gamesWithResults}/${rs.round.gameCount}".padStart(5)
            val correct   = "${rs.correct}/${rs.gamesWithResults}".padStart(7)
            val trad      = rs.traditionalPoints.toString().padStart(9)
            val custom    = rs.customPoints.toString().padStart(10)
            println("│  $roundName  $done  $correct  $trad  $custom")
        }
        println("│  ──────────────────────────────────────────────────────────")
        println("│  TOTAL                         ${score.totalCorrect}/${score.totalGamesWithResults}  ${score.totalTraditional.toString().padStart(9)}  ${score.totalCustom.toString().padStart(10)}")
        println("└─────────────────────────────────────────────────────────────────────────────")
        println()
    }
}
