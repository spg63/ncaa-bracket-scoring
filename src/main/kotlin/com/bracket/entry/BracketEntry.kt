package com.bracket.entry

import com.bracket.models.*
import com.bracket.storage.Storage
import java.time.LocalDateTime

/**
 * Handles the CLI flow for entering or updating a player bracket.
 *
 * Entry flow:
 *  1. Show Round of 64 matchups (32 games, teams from tournament structure)
 *  2. Show Round of 32 matchups (16 games, derived from player's R64 picks)
 *  3. Continue through all 6 rounds
 *
 * In each round, a matchup looks like:
 *
 *   Game 3 — Sweet 16 | South Region
 *   ┌─────────────────────────────┐
 *   │ 1) (1) Duke Blue Devils     │
 *   │ 2) (5) Michigan             │
 *   └─────────────────────────────┘
 *   Your pick [1/2]:
 *
 * Entering 'q' at any time aborts and discards the partial bracket.
 * Entering 's' saves progress and exits (partial bracket saved).
 */
object BracketEntry {

    fun enterBracket(playerName: String, structure: TournamentStructure): PlayerBracket? {
        println()
        println("┌─────────────────────────────────────────────────────┐")
        println("│   BRACKET ENTRY — $playerName")
        println("│   ${structure.games.count { it.round == TournamentRound.ROUND_64.name }} games in Round of 64, 63 total picks")
        println("│   Enter 1 or 2 to pick. 's' = save & exit. 'q' = quit without saving.")
        println("└─────────────────────────────────────────────────────┘")

        val picks = mutableListOf<PlayerPick>()

        // Build a mutable map of gameId → picked team (for resolving future round matchups)
        val pickedWinners = mutableMapOf<String, Team>()

        // Also build a map of the actual game structure for lookup
        val gameMap = structure.games.associateBy { it.gameId }

        for (round in TournamentRound.ordered) {
            val roundGames = structure.games.filter { it.round == round.name }
            if (roundGames.isEmpty()) {
                println("  ⚠ No games found for ${round.displayName} — skipping.")
                continue
            }

            println()
            println("  ═══ ${round.displayName.uppercase()} (${round.gameCount} picks) ═══")

            for ((gameIdx, game) in roundGames.withIndex()) {
                // For rounds after R64, fill in teams from the player's earlier picks.
                // The tournament structure may have TBD teams for unplayed games,
                // so we substitute in what the player picked.
                val team1 = resolveTeam(game.team1, game.sourceGame1Id, pickedWinners)
                val team2 = resolveTeam(game.team2, game.sourceGame2Id, pickedWinners)

                val gameLabel = buildGameLabel(round, gameIdx + 1, game.gameId)

                println()
                println("  Game ${gameIdx + 1} — $gameLabel")
                println("  ┌────────────────────────────────────────┐")
                println("  │  1)  $team1")
                println("  │  2)  $team2")
                println("  └────────────────────────────────────────┘")
                print("  Your pick [1/2] (s=save, q=quit): ")

                when (val input = readln().trim().lowercase()) {
                    "q" -> {
                        println("  Aborting — no bracket saved for $playerName.")
                        return null
                    }
                    "s" -> {
                        println("  Saving partial bracket (${picks.size} picks so far)...")
                        return finalize(playerName, structure.year, picks)
                    }
                    "1", "2" -> {
                        val picked = if (input == "1") team1 else team2
                        picks += PlayerPick(
                            gameId          = game.gameId,
                            round           = round.name,
                            pickedTeamName  = picked.name,
                            pickedSeed      = picked.seed
                        )
                        pickedWinners[game.gameId] = picked
                        println("  → Picked: $picked")
                    }
                    else -> {
                        println("  Invalid input — please enter 1 or 2. Re-entering this game.")
                        // Redo this game by decrementing conceptually — we use continue with label trick
                        // Actually easier: re-prompt inline
                        var valid = false
                        while (!valid) {
                            print("  Your pick [1/2]: ")
                            val retry = readln().trim()
                            if (retry == "1" || retry == "2") {
                                val picked = if (retry == "1") team1 else team2
                                picks += PlayerPick(
                                    gameId         = game.gameId,
                                    round          = round.name,
                                    pickedTeamName = picked.name,
                                    pickedSeed     = picked.seed
                                )
                                pickedWinners[game.gameId] = picked
                                println("  → Picked: $picked")
                                valid = true
                            } else {
                                println("  Please enter 1 or 2.")
                            }
                        }
                    }
                }
            }

            println()
            println("  ✓ ${round.displayName} complete.")
        }

        println()
        println("  ✓ All 63 picks entered for $playerName!")
        return finalize(playerName, structure.year, picks)
    }

    /**
     * Review an existing bracket and optionally update individual picks.
     * Walks through all rounds showing what was picked; user can enter 1/2 to change or
     * press Enter to keep the existing pick.
     */
    fun reviewBracket(existing: PlayerBracket, structure: TournamentStructure): PlayerBracket {
        println()
        println("  Reviewing bracket for ${existing.playerName}. Press Enter to keep a pick, or 1/2 to change it.")

        val pickMap = existing.picks.associateBy { it.gameId }.toMutableMap()
        val pickedWinners = mutableMapOf<String, Team>()

        // Pre-populate winners from existing picks for resolving matchups
        for (pick in existing.picks) {
            val game = structure.games.find { it.gameId == pick.gameId } ?: continue
            val team = if (game.team1.name == pick.pickedTeamName ||
                           game.team1.shortName == pick.pickedTeamName) game.team1 else game.team2
            pickedWinners[pick.gameId] = team
        }

        // Reset and re-enter picks so the chain stays consistent if anything changes
        val newPickMap = mutableMapOf<String, PlayerPick>()
        val newWinners = mutableMapOf<String, Team>()

        for (round in TournamentRound.ordered) {
            val roundGames = structure.games.filter { it.round == round.name }
            println()
            println("  ═══ ${round.displayName.uppercase()} ═══")

            for ((gameIdx, game) in roundGames.withIndex()) {
                val team1 = resolveTeam(game.team1, game.sourceGame1Id, newWinners)
                val team2 = resolveTeam(game.team2, game.sourceGame2Id, newWinners)
                val existing = pickMap[game.gameId]
                val existingLabel = existing?.pickedTeamName ?: "(no pick)"
                val gameLabel = buildGameLabel(round, gameIdx + 1, game.gameId)

                println()
                println("  Game ${gameIdx + 1} — $gameLabel  [current: $existingLabel]")
                println("  │  1)  $team1")
                println("  │  2)  $team2")
                print("  Change pick? [Enter=keep, 1, 2]: ")

                val input = readln().trim()
                val picked = when (input) {
                    "1" -> team1
                    "2" -> team2
                    else -> {
                        // Keep existing if valid, else default to team1
                        if (existing?.pickedTeamName == team1.name ||
                            existing?.pickedTeamName == team1.shortName) team1
                        else if (existing?.pickedTeamName == team2.name ||
                                 existing?.pickedTeamName == team2.shortName) team2
                        else team1.also { println("  (previous pick not found in current matchup — defaulting to team1)") }
                    }
                }

                newPickMap[game.gameId] = PlayerPick(game.gameId, round.name, picked.name, picked.seed)
                newWinners[game.gameId] = picked
                if (input == "1" || input == "2") println("  → Changed to: $picked")
            }
        }

        return finalize(existing.playerName, existing.year, newPickMap.values.toList())
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * For games after Round 1, the team slots may be "TBD" in the structure.
     * Look up who the player picked in the source game and substitute that in.
     */
    private fun resolveTeam(
        structureTeam: Team,
        sourceGameId: String?,
        pickedWinners: Map<String, Team>
    ): Team {
        // Always prefer the player's own pick from the source game — this ensures
        // later rounds show who the player predicted would advance, not the actual
        // ESPN results (which may differ from their bracket).
        if (sourceGameId != null) {
            pickedWinners[sourceGameId]?.let { return it }
        }
        // Fall back to the structure team (used for R64 where sourceGameId is null)
        return structureTeam
    }

    private fun buildGameLabel(round: TournamentRound, gameNum: Int, gameId: String): String {
        // Try to extract region from gameId, e.g. "SOUTH_R64_G1"
        val region = when {
            gameId.contains("SOUTH",   ignoreCase = true) -> "South Region"
            gameId.contains("EAST",    ignoreCase = true) -> "East Region"
            gameId.contains("MIDWEST", ignoreCase = true) -> "Midwest Region"
            gameId.contains("WEST",    ignoreCase = true) -> "West Region"
            gameId.startsWith("FF")                       -> "Final Four"
            gameId == "CHAMP"                             -> "Championship"
            else -> ""
        }
        return if (region.isNotEmpty()) "${round.displayName} | $region" else round.displayName
    }

    private fun finalize(playerName: String, year: Int, picks: List<PlayerPick>): PlayerBracket {
        val bracket = PlayerBracket(
            playerName = playerName,
            year       = year,
            picks      = picks,
            enteredAt  = LocalDateTime.now().toString()
        )
        Storage.saveBracket(bracket)
        return bracket
    }
}
