@file:OptIn(ExperimentalMaterial3Api::class)

package com.bracket.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bracket.models.*
import com.bracket.ui.BracketEntryViewModel

// ─── Dimensions ───────────────────────────────────────────────────────────────

private val CARD_WIDTH       = 148.dp
private val CARD_HEIGHT      = 76.dp
private val BASE_SLOT_HEIGHT = 84.dp  // card + 4dp top/bottom padding
private val COL_GAP          = 12.dp

// ─── Entry Screen ─────────────────────────────────────────────────────────────

@Composable
fun BracketEntryScreen(
    structure: TournamentStructure?,
    entryViewModel: BracketEntryViewModel,
    onBracketSaved: () -> Unit,
    loadBracket: (String) -> PlayerBracket?,
    listPlayers: () -> List<String>,
    modifier: Modifier = Modifier
) {
    val entryState by entryViewModel.state.collectAsState()

    LaunchedEffect(entryState.savedBracket) {
        if (entryState.savedBracket != null) {
            onBracketSaved()
            entryViewModel.reset()
        }
    }

    if (structure == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No tournament data. Refresh on the Leaderboard tab first.")
        }
        return
    }

    if (entryState.playerName.isEmpty()) {
        PlayerSelectionScreen(
            modifier      = modifier,
            listPlayers   = listPlayers,
            loadBracket   = loadBracket,
            onPlayerStart = { name, existing ->
                entryViewModel.startEntry(name, structure, existing)
            }
        )
    } else {
        BracketEditScreen(
            modifier       = modifier,
            structure      = structure,
            entryState     = entryState,
            resolveTeam    = entryViewModel::resolveTeam,
            onPick         = entryViewModel::pick,
            onSave         = entryViewModel::save,
            onCancel       = entryViewModel::reset
        )
    }
}

// ─── Player Selection ─────────────────────────────────────────────────────────

@Composable
private fun PlayerSelectionScreen(
    modifier: Modifier,
    listPlayers: () -> List<String>,
    loadBracket: (String) -> PlayerBracket?,
    onPlayerStart: (String, PlayerBracket?) -> Unit
) {
    var customName by remember { mutableStateOf("") }
    val knownPlayers   = remember { listPlayers() }
    val defaultPlayers = listOf("Chelsey", "Damian", "Karen", "Joe", "Sean", "AI")
    val allPlayers     = (knownPlayers + defaultPlayers).distinct()

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Enter / Edit Bracket") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor    = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary
            )
        )
        LazyColumn(
            modifier        = Modifier.weight(1f),
            contentPadding  = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(allPlayers) { name ->
                val existing = loadBracket(name)
                PlayerRow(name = name, hasFile = existing != null, onEnter = { onPlayerStart(name, existing) })
            }
            item {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value         = customName,
                    onValueChange = { customName = it },
                    label         = { Text("Or enter a new name…") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true
                )
                if (customName.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick  = { onPlayerStart(customName.trim(), null) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Start bracket for ${customName.trim()}") }
                }
            }
        }
    }
}

@Composable
private fun PlayerRow(name: String, hasFile: Boolean, onEnter: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    if (hasFile) "Bracket on file" else "No bracket yet",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasFile) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onEnter) { Text(if (hasFile) "Edit" else "Enter") }
        }
    }
}

// ─── Bracket Edit ─────────────────────────────────────────────────────────────

@Composable
private fun BracketEditScreen(
    modifier: Modifier,
    structure: TournamentStructure,
    entryState: com.bracket.ui.EntryState,
    resolveTeam: (Team, String?, Map<String, Team>) -> Team,
    onPick: (String, Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val tabs   = listOf("South", "East", "Midwest", "West", "National")
    var tabIdx by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier,
        topBar   = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(entryState.playerName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${entryState.pickCount} / 63 picks",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = onCancel) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onPrimary)
                        }
                        Button(
                            onClick  = onSave,
                            enabled  = !entryState.isSaving,
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onPrimary,
                                contentColor   = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            if (entryState.isSaving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Text("Save")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor    = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                ScrollableTabRow(
                    selectedTabIndex = tabIdx,
                    containerColor   = MaterialTheme.colorScheme.primaryContainer,
                    contentColor     = MaterialTheme.colorScheme.onPrimaryContainer,
                    edgePadding      = 8.dp
                ) {
                    tabs.forEachIndexed { i, label ->
                        Tab(selected = tabIdx == i, onClick = { tabIdx = i }, text = { Text(label) })
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .horizontalScroll(rememberScrollState())
                .verticalScroll(rememberScrollState())
        ) {
            if (tabIdx < 4) {
                val region = tabs[tabIdx]
                RegionBracketView(
                    region         = region,
                    structure      = structure,
                    pickedWinners  = entryState.pickedWinners,
                    resolveTeam    = resolveTeam,
                    onPick         = onPick
                )
            } else {
                NationalBracketView(
                    structure     = structure,
                    pickedWinners = entryState.pickedWinners,
                    resolveTeam   = resolveTeam,
                    onPick        = onPick
                )
            }
        }
    }
}

// ─── Regional Bracket ─────────────────────────────────────────────────────────

@Composable
private fun RegionBracketView(
    region: String,
    structure: TournamentStructure,
    pickedWinners: Map<String, Team>,
    resolveTeam: (Team, String?, Map<String, Team>) -> Team,
    onPick: (String, Int) -> Unit
) {
    val regionalRounds = listOf(
        TournamentRound.ROUND_64,
        TournamentRound.ROUND_32,
        TournamentRound.SWEET_16,
        TournamentRound.ELITE_8
    )

    Row(
        modifier            = Modifier.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(COL_GAP)
    ) {
        regionalRounds.forEachIndexed { roundIdx, round ->
            val games = structure.games.filter {
                it.round == round.name &&
                it.gameId.startsWith(region.uppercase())
            }
            BracketColumn(
                round         = round,
                roundIndex    = roundIdx,
                games         = games,
                pickedWinners = pickedWinners,
                resolveTeam   = resolveTeam,
                onPick        = onPick
            )
        }
    }
}

@Composable
private fun BracketColumn(
    round: TournamentRound,
    roundIndex: Int,
    games: List<BracketGame>,
    pickedWinners: Map<String, Team>,
    resolveTeam: (Team, String?, Map<String, Team>) -> Team,
    onPick: (String, Int) -> Unit
) {
    // Slot height doubles each round so games are centered on their source games
    val slotHeight = BASE_SLOT_HEIGHT * (1 shl roundIndex)

    Column(modifier = Modifier.width(CARD_WIDTH)) {
        // Round header
        Text(
            text     = round.shortName,
            style    = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color    = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .width(CARD_WIDTH)
                .padding(bottom = 4.dp),
        )
        games.forEach { game ->
            Box(
                modifier           = Modifier
                    .width(CARD_WIDTH)
                    .height(slotHeight),
                contentAlignment   = Alignment.Center
            ) {
                BracketGameCard(
                    game          = game,
                    pickedWinners = pickedWinners,
                    resolveTeam   = resolveTeam,
                    onPick        = onPick
                )
            }
        }
    }
}

// ─── National Bracket (FF + Champ) ────────────────────────────────────────────

@Composable
private fun NationalBracketView(
    structure: TournamentStructure,
    pickedWinners: Map<String, Team>,
    resolveTeam: (Team, String?, Map<String, Team>) -> Team,
    onPick: (String, Int) -> Unit
) {
    val ffGames    = structure.games.filter { it.round == TournamentRound.FINAL_FOUR.name }
    val champGame  = structure.games.firstOrNull { it.gameId == "CHAMP" }
    val slotHeight = BASE_SLOT_HEIGHT * 2

    Row(
        modifier              = Modifier.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(COL_GAP),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        // Left semifinal
        Column(modifier = Modifier.width(CARD_WIDTH)) {
            Text("Final Four", style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp))
            ffGames.getOrNull(0)?.let { game ->
                Box(Modifier.width(CARD_WIDTH).height(slotHeight), Alignment.Center) {
                    BracketGameCard(game, pickedWinners, resolveTeam, onPick)
                }
            }
        }

        // Championship
        Column(modifier = Modifier.width(CARD_WIDTH)) {
            Text("Championship", style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp))
            champGame?.let { game ->
                Box(Modifier.width(CARD_WIDTH).height(slotHeight), Alignment.Center) {
                    BracketGameCard(game, pickedWinners, resolveTeam, onPick)
                }
            }
        }

        // Right semifinal
        Column(modifier = Modifier.width(CARD_WIDTH)) {
            Text("Final Four", style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp))
            ffGames.getOrNull(1)?.let { game ->
                Box(Modifier.width(CARD_WIDTH).height(slotHeight), Alignment.Center) {
                    BracketGameCard(game, pickedWinners, resolveTeam, onPick)
                }
            }
        }
    }
}

// ─── Game Card ────────────────────────────────────────────────────────────────

@Composable
private fun BracketGameCard(
    game: BracketGame,
    pickedWinners: Map<String, Team>,
    resolveTeam: (Team, String?, Map<String, Team>) -> Team,
    onPick: (String, Int) -> Unit
) {
    val team1  = resolveTeam(game.team1, game.sourceGame1Id, pickedWinners)
    val team2  = resolveTeam(game.team2, game.sourceGame2Id, pickedWinners)
    val pick   = pickedWinners[game.gameId]
    val locked = false

    // Only show real-result indicators when the resolved team actually appeared
    // in the live game. If the player picked an upset in an earlier round the
    // downstream slot shows their projection, but there's no real result for it.
    val team1InGame = team1.name == game.team1.name || team1.name == game.team2.name
    val team2InGame = team2.name == game.team1.name || team2.name == game.team2.name

    Card(
        modifier  = Modifier.width(CARD_WIDTH).height(CARD_HEIGHT),
        elevation = CardDefaults.cardElevation(2.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TeamSlot(
                team     = team1,
                isPicked = pick?.name == team1.name || pick?.name == team1.shortName,
                isWinner = game.isComplete && team1InGame && game.winner?.name == team1.name,
                isLoser  = game.isComplete && team1InGame && game.winner != null && game.winner.name != team1.name,
                locked   = locked,
                modifier = Modifier.weight(1f),
                onClick  = { if (!locked) onPick(game.gameId, 0) }
            )
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            TeamSlot(
                team     = team2,
                isPicked = pick?.name == team2.name || pick?.name == team2.shortName,
                isWinner = game.isComplete && team2InGame && game.winner?.name == team2.name,
                isLoser  = game.isComplete && team2InGame && game.winner != null && game.winner.name != team2.name,
                locked   = locked,
                modifier = Modifier.weight(1f),
                onClick  = { if (!locked) onPick(game.gameId, 1) }
            )
        }
    }
}

@Composable
private fun TeamSlot(
    team: Team,
    isPicked: Boolean,
    isWinner: Boolean,
    isLoser: Boolean,
    locked: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val bg = when {
        isWinner -> MaterialTheme.colorScheme.primaryContainer
        isPicked -> MaterialTheme.colorScheme.secondaryContainer
        else     -> Color.Transparent
    }
    val textAlpha = if (isLoser) 0.35f else 1f

    Surface(
        onClick          = onClick,
        modifier         = modifier.fillMaxWidth(),
        color            = bg,
        enabled          = !locked
    ) {
        Row(
            modifier          = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Seed
            Text(
                text      = if (team.seed > 0) "${team.seed}" else "?",
                style     = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha),
                modifier  = Modifier.width(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            // Team name (truncated)
            Text(
                text     = team.shortName,
                style    = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha),
                modifier = Modifier.weight(1f)
            )
            // Result icon
            when {
                isWinner -> Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary,
                                 modifier = Modifier.size(12.dp))
                isLoser  -> Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error,
                                 modifier = Modifier.size(12.dp))
                isPicked -> Box(
                    modifier = Modifier
                        .size(6.dp)
                        .padding(0.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(6.dp)) {}
                }
            }
        }
    }
}
