package com.bracket.models

import kotlinx.serialization.Serializable

// ─── Tournament Rounds ────────────────────────────────────────────────────────

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
    val name: String,
    val shortName: String,
    val seed: Int,
    val region: String
) {
    override fun toString() = "(${seed}) $shortName"
}

@Serializable
data class BracketGame(
    val gameId: String,
    val round: String,
    val team1: Team,
    val team2: Team,
    val winner: Team? = null,
    val isComplete: Boolean = false,
    val sourceGame1Id: String? = null,
    val sourceGame2Id: String? = null
)

@Serializable
data class TournamentStructure(
    val year: Int,
    val games: List<BracketGame>,
    val fetchedAt: String = ""
)

// ─── Player Bracket Types ─────────────────────────────────────────────────────

@Serializable
data class PlayerPick(
    val gameId: String,
    val round: String,
    val pickedTeamName: String,
    val pickedSeed: Int
)

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
    val maxPossibleTraditional: Int,
    val maxPossibleCustom: Int
)
