package com.bracket.storage

import android.content.Context
import android.util.Log
import com.bracket.models.PlayerBracket
import com.bracket.models.TournamentStructure
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private const val TAG = "Storage"

object Storage {

    private fun bracketsDir(context: Context) =
        File(context.filesDir, "brackets").also { it.mkdirs() }

    private fun cacheDir(context: Context) =
        File(context.cacheDir, "bracket_cache").also { it.mkdirs() }

    private fun bracketFile(context: Context, playerName: String): File {
        val safe = playerName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        return File(bracketsDir(context), "$safe.json")
    }

    // ─── Player Brackets ──────────────────────────────────────────────────────

    fun saveBracket(context: Context, bracket: PlayerBracket) {
        runCatching {
            bracketFile(context, bracket.playerName).writeText(json.encodeToString(bracket))
            Log.d(TAG, "Saved bracket for ${bracket.playerName}")
        }.onFailure { Log.e(TAG, "Failed to save bracket for ${bracket.playerName}: ${it.message}") }
    }

    fun loadBracket(context: Context, playerName: String): PlayerBracket? {
        val file = bracketFile(context, playerName)
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<PlayerBracket>(file.readText()) }
            .getOrElse {
                Log.e(TAG, "Could not read bracket for $playerName: ${it.message}")
                null
            }
    }

    fun loadAllBrackets(context: Context): List<PlayerBracket> {
        return bracketsDir(context)
            .listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f ->
                runCatching { json.decodeFromString<PlayerBracket>(f.readText()) }.getOrNull()
            } ?: emptyList()
    }

    fun bracketExists(context: Context, playerName: String): Boolean =
        bracketFile(context, playerName).exists()

    fun listBracketPlayers(context: Context): List<String> =
        bracketsDir(context)
            .listFiles { f -> f.extension == "json" }
            ?.map { it.nameWithoutExtension } ?: emptyList()

    // ─── Tournament Structure Cache ───────────────────────────────────────────

    fun saveTournamentStructure(context: Context, structure: TournamentStructure) {
        runCatching {
            File(cacheDir(context), "tournament_structure.json")
                .writeText(json.encodeToString(structure))
        }.onFailure { Log.e(TAG, "Failed to save tournament structure: ${it.message}") }
    }

    fun loadCachedTournamentStructure(context: Context): TournamentStructure? {
        val file = File(cacheDir(context), "tournament_structure.json")
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<TournamentStructure>(file.readText()) }
            .getOrElse {
                Log.e(TAG, "Could not read tournament cache: ${it.message}")
                null
            }
    }

    fun clearTournamentCache(context: Context) {
        File(cacheDir(context), "tournament_structure.json").delete()
        Log.d(TAG, "Cleared tournament structure cache.")
    }
}
