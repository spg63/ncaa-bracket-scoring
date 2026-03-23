package com.bracket.storage

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

object Storage {

    private const val BRACKETS_DIR = "brackets"
    private const val CACHE_DIR    = ".cache"
    private const val STRUCTURE_FILE = "tournament_structure.json"

    init {
        File(BRACKETS_DIR).mkdirs()
        File(CACHE_DIR).mkdirs()
    }

    // ─── Player Brackets ──────────────────────────────────────────────────────

    fun saveBracket(bracket: PlayerBracket) {
        val file = bracketFile(bracket.playerName)
        file.writeText(json.encodeToString(bracket))
        println("  ✓ Saved bracket for ${bracket.playerName} → ${file.path}")
    }

    fun loadBracket(playerName: String): PlayerBracket? {
        val file = bracketFile(playerName)
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<PlayerBracket>(file.readText()) }
            .getOrElse {
                println("  ⚠ Could not read bracket for $playerName: ${it.message}")
                null
            }
    }

    fun loadAllBrackets(): List<PlayerBracket> {
        val dir = File(BRACKETS_DIR)
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f ->
                runCatching { json.decodeFromString<PlayerBracket>(f.readText()) }.getOrNull()
            } ?: emptyList()
    }

    fun bracketExists(playerName: String): Boolean = bracketFile(playerName).exists()

    fun listBracketPlayers(): List<String> {
        val dir = File(BRACKETS_DIR)
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.extension == "json" }
            ?.map { it.nameWithoutExtension } ?: emptyList()
    }

    private fun bracketFile(playerName: String): File {
        val safe = playerName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        return File("$BRACKETS_DIR/$safe.json")
    }

    // ─── Tournament Structure Cache ───────────────────────────────────────────

    fun saveTournamentStructure(structure: TournamentStructure) {
        File("$CACHE_DIR/$STRUCTURE_FILE").writeText(json.encodeToString(structure))
    }

    fun loadCachedTournamentStructure(): TournamentStructure? {
        val file = File("$CACHE_DIR/$STRUCTURE_FILE")
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<TournamentStructure>(file.readText()) }.getOrNull()
    }

    fun clearTournamentCache() {
        File("$CACHE_DIR/$STRUCTURE_FILE").delete()
        println("  ✓ Cleared tournament structure cache.")
    }
}
