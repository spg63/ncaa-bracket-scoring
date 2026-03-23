@file:OptIn(ExperimentalMaterial3Api::class)

package com.bracket.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bracket.models.PlayerBracket
import com.bracket.models.PlayerPick
import com.bracket.models.TournamentRound
import com.bracket.models.TournamentStructure

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerPicksScreen(
    structure: TournamentStructure?,
    listPlayers: () -> List<String>,
    loadBracket: (String) -> PlayerBracket?,
    modifier: Modifier = Modifier
) {
    val players = remember { listPlayers() }
    var selectedPlayer by remember { mutableStateOf(players.firstOrNull() ?: "") }
    var bracket by remember(selectedPlayer) { mutableStateOf(loadBracket(selectedPlayer)) }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("My Picks") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor    = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary
            )
        )

        if (players.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No brackets on file yet.")
            }
            return@Column
        }

        // Player selector
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded         = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier         = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value            = selectedPlayer,
                onValueChange    = {},
                readOnly         = true,
                label            = { Text("Player") },
                trailingIcon     = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier         = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                players.forEach { name ->
                    DropdownMenuItem(
                        text    = { Text(name) },
                        onClick = {
                            selectedPlayer = name
                            bracket = loadBracket(name)
                            expanded = false
                        }
                    )
                }
            }
        }

        if (bracket == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No bracket found for $selectedPlayer.")
            }
            return@Column
        }

        val b = bracket!!
        val gameMap = structure?.games?.associateBy { it.gameId }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Entered: ${b.enteredAt.take(16)}",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            TournamentRound.ordered.forEach { round ->
                val roundPicks = b.picks.filter { it.round == round.name }
                if (roundPicks.isNotEmpty()) {
                    item {
                        Text(
                            text     = round.displayName,
                            style    = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
                            color    = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(roundPicks) { pick ->
                        val game = gameMap?.get(pick.gameId)
                        PickRow(pick = pick, isComplete = game?.isComplete ?: false, winnerName = game?.winner?.name)
                    }
                }
            }
        }
    }
}

@Composable
private fun PickRow(pick: PlayerPick, isComplete: Boolean, winnerName: String?) {
    val isCorrect = isComplete && (
        pick.pickedTeamName.equals(winnerName, ignoreCase = true)
    )
    val isWrong = isComplete && winnerName != null && !isCorrect

    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(1.dp),
        colors    = CardDefaults.cardColors(
            containerColor = when {
                isCorrect -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                isWrong   -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                else      -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Seed badge
            Surface(
                shape    = MaterialTheme.shapes.extraSmall,
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("${pick.pickedSeed}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(pick.pickedTeamName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    pick.gameId,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ResultBadge(isComplete = isComplete, isCorrect = isCorrect, winnerName = winnerName)
        }
    }
}

@Composable
private fun ResultBadge(isComplete: Boolean, isCorrect: Boolean, winnerName: String?) {
    when {
        !isComplete -> Text(
            "—",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        isCorrect -> Text(
            "✓",
            style    = MaterialTheme.typography.labelLarge,
            color    = Color(0xFF2E7D32),
            fontWeight = FontWeight.Bold
        )
        else -> Column(horizontalAlignment = Alignment.End) {
            Text(
                "✗",
                style    = MaterialTheme.typography.labelLarge,
                color    = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
            if (winnerName != null) {
                Text(
                    winnerName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
