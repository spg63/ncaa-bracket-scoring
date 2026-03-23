package com.bracket

import com.bracket.entry.BracketEntry
import com.bracket.models.TournamentRound
import com.bracket.models.TournamentStructure
import com.bracket.results.ResultsFetcher
import com.bracket.scoring.Scorer
import com.bracket.storage.Storage

// ─── Default family pool ──────────────────────────────────────────────────────
// Edit this list freely — these are just the default player names.
// Any bracket JSON already in the brackets/ directory will be detected automatically.
val DEFAULT_PLAYERS = listOf(
    "Chelsey",
    "Damian",
    "Karen",
    "Joe",
    "Sean",
    "AI"
)

fun main() {
    println()
    println("╔══════════════════════════════════════════════════╗")
    println("║         NCAA BRACKET SCORER — Family Pool        ║")
    println("╚══════════════════════════════════════════════════╝")

    var structure = Storage.loadCachedTournamentStructure()
    if (structure != null) {
        println("  Loaded cached tournament structure (${structure.year}, fetched ${structure.fetchedAt.take(16)})")
        println("  Tip: Use option 5 to refresh from the web.")
    }

    mainLoop(structure)
}

fun mainLoop(initialStructure: TournamentStructure?) {
    var structure = initialStructure

    while (true) {
        println()
        println("  ── Main Menu ─────────────────────────────────────")
        println("  1  Enter / update a bracket")
        println("  2  Score all brackets (leaderboard)")
        println("  3  Detailed score for one player")
        println("  4  View picks for a player")
        println("  5  Refresh tournament data from the web")
        println("  6  Show scoring rules")
        println("  0  Exit")
        println("  ──────────────────────────────────────────────────")
        print("  Choice: ")

        when (readln().trim()) {
            "1" -> structure = menuEnterBracket(structure)
            "2" -> menuLeaderboard(structure)
            "3" -> menuDetailedScore(structure)
            "4" -> menuViewPicks(structure)
            "5" -> structure = menuRefresh()
            "6" -> menuShowRules()
            "0" -> {
                println("  Goodbye!")
                return
            }
            else -> println("  Invalid choice.")
        }
    }
}

// ─── Menu Handlers ────────────────────────────────────────────────────────────

fun menuEnterBracket(structure: TournamentStructure?): TournamentStructure? {
    // Make sure we have tournament structure before entering a bracket
    val s = ensureStructure(structure) ?: return structure

    println()
    println("  Which player? Known players:")
    val players = Storage.listBracketPlayers().toMutableList()
    DEFAULT_PLAYERS.forEach { if (!players.contains(it)) players.add(it) }
    players.forEachIndexed { i, p ->
        val status = if (Storage.bracketExists(p)) "✓ bracket on file" else "— no bracket yet"
        println("  ${i + 1})  $p  [$status]")
    }
    println("  Or type a name directly.")
    print("  Player [number or name]: ")

    val input = readln().trim()
    val playerName = input.toIntOrNull()
        ?.let { players.getOrNull(it - 1) }
        ?: input.ifBlank { null }
        ?: run { println("  Cancelled."); return s }

    val existing = Storage.loadBracket(playerName)
    if (existing != null) {
        println()
        println("  Found existing bracket for $playerName (entered ${existing.enteredAt.take(16)}).")
        println("  1) Review and update picks  2) Replace entirely  3) Cancel")
        print("  Choice: ")
        when (readln().trim()) {
            "1" -> BracketEntry.reviewBracket(existing, s)
            "2" -> BracketEntry.enterBracket(playerName, s)
            else -> println("  Cancelled.")
        }
    } else {
        BracketEntry.enterBracket(playerName, s)
    }

    return s
}

fun menuLeaderboard(structure: TournamentStructure?) {
    val s = ensureStructure(structure) ?: return
    val brackets = Storage.loadAllBrackets()
    if (brackets.isEmpty()) {
        println("  No brackets found. Enter some first (option 1).")
        return
    }

    println()
    println("  Sort by:  1) Traditional (ESPN-style)  2) Custom (seed × multiplier)")
    print("  Choice [1]: ")
    val byCustom = readln().trim() == "2"

    val scores = Scorer.scoreAll(brackets, s)
    Scorer.printLeaderboard(scores, sortByCustom = byCustom)
}

fun menuDetailedScore(structure: TournamentStructure?) {
    val s = ensureStructure(structure) ?: return
    val player = pickPlayer() ?: return
    val bracket = Storage.loadBracket(player) ?: run {
        println("  No bracket found for $player.")
        return
    }
    val score = Scorer.score(bracket, s)
    Scorer.printDetailedScore(score)
}

fun menuViewPicks(structure: TournamentStructure?) {
    val player = pickPlayer() ?: return
    val bracket = Storage.loadBracket(player) ?: run {
        println("  No bracket found for $player.")
        return
    }
    val s = structure

    println()
    println("  Bracket picks for ${bracket.playerName} (entered ${bracket.enteredAt.take(16)}):")

    for (round in TournamentRound.ordered) {
        val roundPicks = bracket.picks.filter { it.round == round.name }
        if (roundPicks.isEmpty()) continue
        println()
        println("  ── ${round.displayName} ─────────────────────────")
        roundPicks.forEach { pick ->
            val actualResult = s?.games?.find { it.gameId == pick.gameId }
            val resultStr = when {
                actualResult == null -> ""
                !actualResult.isComplete -> "  [not yet played]"
                actualResult.winner?.name == pick.pickedTeamName ||
                actualResult.winner?.shortName == pick.pickedTeamName -> "  ✓"
                else -> "  ✗  (actual: ${actualResult.winner})"
            }
            println("  ${pick.gameId.padEnd(15)} → (${pick.pickedSeed}) ${pick.pickedTeamName}$resultStr")
        }
    }
}

fun menuRefresh(): TournamentStructure? {
    println()
    println("  Fetching latest data from ESPN / NCAA...")
    val s = ResultsFetcher.fetchTournamentStructure()
    if (s != null) {
        Storage.saveTournamentStructure(s)
        val complete = s.games.count { it.isComplete }
        val total    = s.games.size
        println("  ✓ Tournament data refreshed: $complete/$total games complete.")
    } else {
        println("  ✗ Could not fetch data. Check your internet connection.")
        println("  The app will continue using any locally cached data.")
    }
    return s ?: Storage.loadCachedTournamentStructure()
}

fun menuShowRules() {
    println()
    println("  ┌─── Scoring Rules ─────────────────────────────────────────────────┐")
    println("  │                                                                    │")
    println("  │  TRADITIONAL (ESPN/CBS style):                                    │")
    println("  │    Correct pick = flat points for that round                      │")
    println("  │    Round of 64 → 10 pts   Round of 32 → 20 pts                   │")
    println("  │    Sweet 16    → 40 pts   Elite 8     → 80 pts                   │")
    println("  │    Final Four  → 160 pts  Championship → 320 pts                 │")
    println("  │                                                                    │")
    println("  │  CUSTOM (seed × multiplier):                                      │")
    println("  │    Correct pick = winner's seed × round multiplier                │")
    println("  │    Multipliers double each round: 1, 2, 4, 8, 16, 32             │")
    println("  │                                                                    │")
    println("  │    Examples:                                                       │")
    println("  │      1-seed wins R64:   1 × 1  =   1 pt                          │")
    println("  │     16-seed wins R64:  16 × 1  =  16 pts  (huge upset bonus!)    │")
    println("  │      1-seed wins CHAMP: 1 × 32 =  32 pts                         │")
    println("  │     12-seed wins S16:  12 × 4  =  48 pts                         │")
    println("  │     10-seed wins E8:   10 × 8  =  80 pts                         │")
    println("  │                                                                    │")
    println("  │  Custom scoring heavily rewards correctly predicting upsets.      │")
    println("  │  A 15-seed Cinderella run to the Final Four would score:          │")
    println("  │    R64: 15 + R32: 30 + S16: 60 + E8: 120 + FF: 240 = 465 pts!   │")
    println("  │                                                                    │")
    println("  └────────────────────────────────────────────────────────────────────┘")
    println()
}

// ─── Utilities ────────────────────────────────────────────────────────────────

fun ensureStructure(structure: TournamentStructure?): TournamentStructure? {
    if (structure != null) return structure
    println()
    println("  No tournament data loaded. Fetching from web...")
    return menuRefresh()
}

fun pickPlayer(): String? {
    val players = Storage.listBracketPlayers()
    if (players.isEmpty()) {
        println("  No brackets on file. Enter some first (option 1).")
        return null
    }
    println()
    println("  Select player:")
    players.forEachIndexed { i, p -> println("  ${i + 1})  $p") }
    print("  Choice [number or name]: ")
    val input = readln().trim()
    return input.toIntOrNull()?.let { players.getOrNull(it - 1) }
        ?: input.ifBlank { null }
        ?: run { println("  Cancelled."); null }
}
