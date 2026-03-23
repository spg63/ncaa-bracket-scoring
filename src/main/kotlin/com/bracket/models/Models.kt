package com.bracket.models

import kotlinx.serialization.Serializable

// ─── Tournament Rounds ────────────────────────────────────────────────────────

/**
 * The six rounds of the NCAA tournament.
 *
 * [traditionalPts]  : ESPN/CBS-style flat points for a correct pick in this round
 * [customMultiplier]: Sean's custom scoring — score = winner_seed × customMultiplier
 */
enum class TournamentRound(
    val displayName: String,
    val shortName: String,
    val traditionalPts: Int,
    val customMultiplier: Int
) {
    ROUND_64("Round of 64",  "R64",   1,  1),
    ROUND_32("Round of 32",  "R32",   2,  2),
    SWEET_16("Sweet 16",     "S16",   4,  4),
    ELITE_8 ("Elite 8",      "E8",    8,  8),
    FINAL_FOUR("Final Four", "FF",   16, 16),
    CHAMPIONSHIP("Championship", "CHAMP", 32, 32);

    val gameCount: Int get() = when (this) {
        ROUND_64     -> 32
        ROUND_32     -> 16
        SWEET_16     ->  8
        ELITE_8      ->  4
        FINAL_FOUR   ->  2
        CHAMPIONSHIP ->  1
    }

    companion object {
        val ordered = listOf(ROUND_64, ROUND_32, SWEET_16, ELITE_8, FINAL_FOUR, CHAMPIONSHIP)
    }
}

// ─── Core Tournament Types ────────────────────────────────────────────────────

@Serializable
data class Team(
    val name: String,       // Full name, e.g. "Duke Blue Devils"
    val shortName: String,  // Short name for display, e.g. "Duke"
    val seed: Int,
    val region: String      // "South", "East", "Midwest", "West", "FF" for final four games
) {
    override fun toString() = "(${seed}) $shortName"
}

/**
 * Represents a single game node in the bracket tree.
 *
 * gameId format examples:
 *   - "SOUTH_R1_G1"  → South region, Round of 64, game 1 (1-seed vs 16-seed)
 *   - "EAST_R2_G1"   → East region, Round of 32, game 1
 *   - "FF_G1"        → Final Four semifinal 1
 *   - "CHAMP"        → Championship game
 *
 * [sourceGame1Id] and [sourceGame2Id] are the gameIds whose winners become
 * team1/team2 in this game. Null for Round of 64 games (teams are fixed).
 */
@Serializable
data class BracketGame(
    val gameId: String,
    val round: String,              // TournamentRound.name
    val team1: Team,
    val team2: Team,
    val winner: Team? = null,       // null = not yet played
    val isComplete: Boolean = false,
    val sourceGame1Id: String? = null,
    val sourceGame2Id: String? = null
)

/**
 * The full tournament bracket — 63 games, sourced from ESPN/NCAA API and cached locally.
 */
@Serializable
data class TournamentStructure(
    val year: Int,
    val games: List<BracketGame>,
    val fetchedAt: String = ""
)

// ─── Player Bracket Types ─────────────────────────────────────────────────────

/** A single pick: in game [gameId], the player picked [pickedTeamName] (seed [pickedSeed]). */
@Serializable
data class PlayerPick(
    val gameId: String,
    val round: String,          // TournamentRound.name
    val pickedTeamName: String,
    val pickedSeed: Int
)

/** All 63 picks for one player, persisted to brackets/<playerName>.json */
@Serializable
data class PlayerBracket(
    val playerName: String,
    val year: Int,
    val picks: List<PlayerPick>,
    val enteredAt: String = ""
)

// ─── Scoring Types ────────────────────────────────────────────────────────────

data class RoundScore(
    val round: TournamentRound,
    val correct: Int,
    val gamesWithResults: Int,
    val traditionalPoints: Int,
    val customPoints: Int
)

data class PlayerScore(
    val playerName: String,
    val totalTraditional: Int,
    val totalCustom: Int,
    val totalCorrect: Int,
    val totalGamesWithResults: Int,
    val roundBreakdown: List<RoundScore>,
    val maxPossibleTraditional: Int,   // if all remaining unplayed picks are correct
    val maxPossibleCustom: Int
)
