package com.bracket.results

import android.util.Log
import com.bracket.models.BracketGame
import com.bracket.models.Team
import com.bracket.models.TournamentRound
import com.bracket.models.TournamentStructure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime

/**
 * Fetches live NCAA tournament bracket data from ESPN's scoreboard API.
 * All network calls run on Dispatchers.IO — callers should be in a coroutine scope.
 */
object ResultsFetcher {

    private const val TAG = "ResultsFetcher"
    private val json = Json { ignoreUnknownKeys = true }
    private val CURRENT_YEAR = LocalDateTime.now().year
    private val REGION_ORDER = listOf("South", "East", "Midwest", "West")

    // ─── Public API ───────────────────────────────────────────────────────────

    suspend fun fetchTournamentStructure(): TournamentStructure? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Fetching live tournament data from ESPN...")
        val espn = tryFetchESPN()
        if (espn != null) {
            Log.d(TAG, "ESPN data loaded (${espn.games.size} games).")
            return@withContext espn
        }

        Log.w(TAG, "ESPN fetch failed, trying NCAA API...")
        val ncaa = tryFetchNCAA()
        if (ncaa != null) {
            Log.d(TAG, "NCAA data loaded (${ncaa.games.size} games).")
            return@withContext ncaa
        }

        Log.e(TAG, "Both sources failed.")
        null
    }

    // ─── ESPN Scoreboard Fetch ────────────────────────────────────────────────

    private fun tryFetchESPN(): TournamentStructure? = runCatching {
        val url = "https://site.api.espn.com/apis/site/v2/sports/basketball/mens-college-basketball/scoreboard" +
                  "?groups=100&limit=100&dates=${CURRENT_YEAR}0301-${CURRENT_YEAR}0410"
        val body = get(url) ?: return null
        parseESPNScoreboard(json.parseToJsonElement(body).jsonObject)
    }.getOrElse {
        Log.e(TAG, "ESPN error: ${it.message}")
        null
    }

    private fun parseESPNScoreboard(root: JsonObject): TournamentStructure? {
        val events = root["events"]?.jsonArray ?: return null

        data class RawGame(
            val eventId: String,
            val round: TournamentRound,
            val region: String,
            val team1: Team,
            val team2: Team,
            val isComplete: Boolean,
            val winner: Team?
        )

        val rawGames = mutableListOf<RawGame>()

        for (event in events) {
            val eventObj = event.jsonObject
            val eventId  = eventObj["id"]?.jsonPrimitive?.content ?: continue
            val comp     = eventObj["competitions"]?.jsonArray?.firstOrNull()?.jsonObject ?: continue
            val headline = comp["notes"]?.jsonArray
                ?.firstOrNull()?.jsonObject?.get("headline")?.jsonPrimitive?.content ?: ""

            if (headline.contains("First Four", ignoreCase = true)) continue
            val roundEnum = headlineToRound(headline) ?: continue
            val region    = extractRegion(headline)

            val competitors = comp["competitors"]?.jsonArray ?: continue
            if (competitors.size < 2) continue
            val c1 = competitors[0].jsonObject
            val c2 = competitors[1].jsonObject

            fun parseTeam(c: JsonObject): Team? {
                val teamObj = c["team"]?.jsonObject ?: return null
                val seed    = c["curatedRank"]?.jsonObject?.get("current")?.jsonPrimitive?.intOrNull ?: 0
                return Team(
                    name      = teamObj["displayName"]?.jsonPrimitive?.content ?: "TBD",
                    shortName = teamObj["shortDisplayName"]?.jsonPrimitive?.content
                                    ?: teamObj["displayName"]?.jsonPrimitive?.content ?: "TBD",
                    seed      = seed,
                    region    = region
                )
            }

            val team1 = parseTeam(c1) ?: continue
            val team2 = parseTeam(c2) ?: continue

            val isComplete = comp["status"]?.jsonObject
                ?.get("type")?.jsonObject
                ?.get("completed")?.jsonPrimitive?.booleanOrNull ?: false

            val winner: Team? = when {
                !isComplete -> null
                c1["winner"]?.jsonPrimitive?.booleanOrNull == true -> team1
                c2["winner"]?.jsonPrimitive?.booleanOrNull == true -> team2
                else -> null
            }

            rawGames += RawGame(eventId, roundEnum, region, team1, team2, isComplete, winner)
        }

        if (rawGames.isEmpty()) return null

        val games = mutableListOf<BracketGame>()

        val regionalRounds = listOf(
            TournamentRound.ROUND_64,
            TournamentRound.ROUND_32,
            TournamentRound.SWEET_16,
            TournamentRound.ELITE_8
        )

        val regionRoundGameIds = mutableMapOf<Pair<String, TournamentRound>, List<String>>()

        for (round in regionalRounds) {
            for (region in REGION_ORDER) {
                val candidates = rawGames.filter { it.round == round && it.region == region }
                val slotsPerRegion = round.gameCount / REGION_ORDER.size
                val slots = arrayOfNulls<RawGame>(slotsPerRegion)
                val unplaced = mutableListOf<RawGame>()
                for (g in candidates) {
                    val pos = bracketPosition(round, g.team1.seed, g.team2.seed)
                    if (pos in 0 until slotsPerRegion && slots[pos] == null) slots[pos] = g
                    else unplaced += g
                }
                val unplacedIter = unplaced.sortedBy { it.eventId }.iterator()
                slots.indices.forEach { i -> if (slots[i] == null) slots[i] = unplacedIter.takeIf { it.hasNext() }?.next() }
                val group = slots.filterNotNull()

                val gameIds = group.mapIndexed { idx, _ -> regionGameId(region, round, idx + 1) }
                regionRoundGameIds[Pair(region, round)] = gameIds

                val prevRound = regionalRounds.getOrNull(regionalRounds.indexOf(round) - 1)

                group.forEachIndexed { idx, rg ->
                    val gameId  = gameIds[idx]
                    val gameNum = idx + 1
                    val (src1, src2) = if (prevRound != null) {
                        val prevIds = regionRoundGameIds[Pair(region, prevRound)] ?: emptyList()
                        Pair(prevIds.getOrNull(2 * gameNum - 2), prevIds.getOrNull(2 * gameNum - 1))
                    } else {
                        Pair(null, null)
                    }
                    games += BracketGame(
                        gameId        = gameId,
                        round         = round.name,
                        team1         = rg.team1,
                        team2         = rg.team2,
                        winner        = rg.winner,
                        isComplete    = rg.isComplete,
                        sourceGame1Id = src1,
                        sourceGame2Id = src2
                    )
                }
            }
        }

        // ── Final Four ──
        fun e8GameId(region: String) =
            regionRoundGameIds[Pair(region, TournamentRound.ELITE_8)]?.firstOrNull()

        val ffPairings = listOf(Pair("South", "East"), Pair("Midwest", "West"))
        val ffRaw = rawGames.filter { it.round == TournamentRound.FINAL_FOUR }.sortedBy { it.eventId }

        ffRaw.forEachIndexed { idx, rg ->
            val (reg1, reg2) = ffPairings[idx]
            games += BracketGame(
                gameId        = "FF_G${idx + 1}",
                round         = TournamentRound.FINAL_FOUR.name,
                team1         = rg.team1,
                team2         = rg.team2,
                winner        = rg.winner,
                isComplete    = rg.isComplete,
                sourceGame1Id = e8GameId(reg1),
                sourceGame2Id = e8GameId(reg2)
            )
        }

        // ── Championship ──
        val champRaw = rawGames.firstOrNull { it.round == TournamentRound.CHAMPIONSHIP }
        if (champRaw != null) {
            games += BracketGame(
                gameId        = "CHAMP",
                round         = TournamentRound.CHAMPIONSHIP.name,
                team1         = champRaw.team1,
                team2         = champRaw.team2,
                winner        = champRaw.winner,
                isComplete    = champRaw.isComplete,
                sourceGame1Id = "FF_G1",
                sourceGame2Id = "FF_G2"
            )
        }

        return TournamentStructure(
            year      = CURRENT_YEAR,
            games     = games,
            fetchedAt = LocalDateTime.now().toString()
        )
    }

    // ─── NCAA Casablanca Fallback ─────────────────────────────────────────────

    private fun tryFetchNCAA(): TournamentStructure? = runCatching {
        val url = "https://data.ncaa.com/casablanca/bracket/basketball-men/d1/$CURRENT_YEAR/bracket.json"
        val body = get(url) ?: return null
        parseNCAABracket(body)
    }.getOrElse {
        Log.e(TAG, "NCAA error: ${it.message}")
        null
    }

    private fun parseNCAABracket(body: String): TournamentStructure? {
        val root   = json.parseToJsonElement(body).jsonObject
        val rounds = root["bracket"]?.jsonArray ?: return null

        val roundNameMap = mapOf(
            "First Round"   to TournamentRound.ROUND_64,
            "Second Round"  to TournamentRound.ROUND_32,
            "Sweet 16"      to TournamentRound.SWEET_16,
            "Elite Eight"   to TournamentRound.ELITE_8,
            "Final Four"    to TournamentRound.FINAL_FOUR,
            "Championship"  to TournamentRound.CHAMPIONSHIP
        )

        val games = mutableListOf<BracketGame>()

        rounds.forEachIndexed { roundIdx, roundEl ->
            val roundObj  = roundEl.jsonObject
            val roundName = roundObj["bracketRound"]?.jsonPrimitive?.content ?: ""
            val roundEnum = roundNameMap.entries.firstOrNull {
                roundName.contains(it.key, ignoreCase = true)
            }?.value ?: TournamentRound.ordered.getOrNull(roundIdx) ?: return@forEachIndexed

            val roundGames = roundObj["games"]?.jsonArray ?: return@forEachIndexed

            roundGames.forEachIndexed { gameIdx, gameEl ->
                val game   = gameEl.jsonObject["game"]?.jsonObject ?: return@forEachIndexed
                val region = game["region"]?.jsonPrimitive?.content ?: "Unknown"
                val state  = game["gameState"]?.jsonPrimitive?.content ?: ""

                fun parseNCAATeam(side: JsonObject): Team {
                    val seed     = side["seed"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val names    = side["names"]?.jsonObject
                    val fullName = names?.get("full")?.jsonPrimitive?.content
                        ?: names?.get("char6")?.jsonPrimitive?.content ?: "TBD"
                    val short    = names?.get("char6")?.jsonPrimitive?.content ?: fullName
                    return Team(fullName, short, seed, region)
                }

                val away = game["away"]?.jsonObject ?: return@forEachIndexed
                val home = game["home"]?.jsonObject ?: return@forEachIndexed

                val team1 = parseNCAATeam(away)
                val team2 = parseNCAATeam(home)
                val isComplete = state.equals("final", ignoreCase = true)
                val winner: Team? = when {
                    !isComplete -> null
                    away["winner"]?.jsonPrimitive?.booleanOrNull == true -> team1
                    home["winner"]?.jsonPrimitive?.booleanOrNull == true -> team2
                    else -> null
                }

                games += BracketGame(
                    gameId     = buildLegacyGameId(roundEnum, gameIdx + 1),
                    round      = roundEnum.name,
                    team1      = team1,
                    team2      = team2,
                    winner     = winner,
                    isComplete = isComplete
                )
            }
        }

        if (games.isEmpty()) return null
        return TournamentStructure(CURRENT_YEAR, games, LocalDateTime.now().toString())
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun get(url: String): String? = try {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout    = 15_000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; bracket-scorer/1.0)")
        connection.setRequestProperty("Accept", "application/json")
        if (connection.responseCode in 200..299) connection.inputStream.bufferedReader().readText()
        else null
    } catch (e: Exception) {
        Log.e(TAG, "HTTP error for $url: ${e.message}")
        null
    }

    private fun regionGameId(region: String, round: TournamentRound, gameNumber: Int): String =
        "${region.uppercase()}_${round.shortName}_G$gameNumber"

    private fun buildLegacyGameId(round: TournamentRound, gameNumber: Int): String = when (round) {
        TournamentRound.CHAMPIONSHIP -> "CHAMP"
        TournamentRound.FINAL_FOUR   -> "FF_G$gameNumber"
        else -> "${round.shortName}_G$gameNumber"
    }

    private fun bracketPosition(round: TournamentRound, seed1: Int, seed2: Int): Int {
        val minSeed = minOf(seed1, seed2).takeIf { it in 1..16 }
            ?: maxOf(seed1, seed2).takeIf { it in 1..16 }
            ?: return 99

        return when (round) {
            TournamentRound.ROUND_64 -> mapOf(
                1 to 0, 8 to 1, 5 to 2, 4 to 3, 6 to 4, 3 to 5, 7 to 6, 2 to 7
            )[minSeed] ?: 99
            TournamentRound.ROUND_32 -> when (minSeed) {
                in setOf(1, 8, 9, 16)    -> 0
                in setOf(4, 5, 12, 13)   -> 1
                in setOf(3, 6, 11, 14)   -> 2
                in setOf(2, 7, 10, 15)   -> 3
                else -> 99
            }
            TournamentRound.SWEET_16 -> {
                val seeds = listOf(seed1, seed2).filter { it in 1..16 }
                when {
                    seeds.any { it in setOf(1, 4, 5, 8, 9, 12, 13, 16) } -> 0
                    seeds.any { it in setOf(2, 3, 6, 7, 10, 11, 14, 15) } -> 1
                    else -> 99
                }
            }
            else -> 0
        }
    }

    private fun headlineToRound(headline: String): TournamentRound? = when {
        headline.contains("1st Round",             ignoreCase = true) -> TournamentRound.ROUND_64
        headline.contains("2nd Round",             ignoreCase = true) -> TournamentRound.ROUND_32
        headline.contains("Sweet 16",              ignoreCase = true) -> TournamentRound.SWEET_16
        headline.contains("Elite 8",               ignoreCase = true) -> TournamentRound.ELITE_8
        headline.contains("Final Four",            ignoreCase = true) -> TournamentRound.FINAL_FOUR
        headline.contains("National Championship", ignoreCase = true) -> TournamentRound.CHAMPIONSHIP
        else -> null
    }

    private fun extractRegion(headline: String): String {
        val regions = listOf("South", "East", "Midwest", "West")
        return regions.firstOrNull { headline.contains(it, ignoreCase = true) } ?: "National"
    }
}
