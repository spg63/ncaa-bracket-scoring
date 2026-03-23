@file:OptIn(ExperimentalMaterial3Api::class)

package com.bracket.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bracket.models.PlayerScore
import com.bracket.models.RoundScore
import com.bracket.models.TournamentRound
import com.bracket.ui.AppState
import com.bracket.ui.ScoringMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    state: AppState,
    onRefresh: () -> Unit,
    onSetScoringMode: (ScoringMode) -> Unit,
    onClearError: () -> Unit,
    onClearSuccessMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        if (state.error != null) {
            snackbarHostState.showSnackbar(state.error)
            onClearError()
        }
    }

    LaunchedEffect(state.successMessage) {
        if (state.successMessage != null) {
            snackbarHostState.showSnackbar(state.successMessage)
            onClearSuccessMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Leaderboard") },
                actions = {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Scoring mode toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = state.scoringMode == ScoringMode.TRADITIONAL,
                        onClick  = { onSetScoringMode(ScoringMode.TRADITIONAL) },
                        shape    = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("Traditional") }
                    SegmentedButton(
                        selected = state.scoringMode == ScoringMode.CUSTOM,
                        onClick  = { onSetScoringMode(ScoringMode.CUSTOM) },
                        shape    = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("Custom") }
                }
            }

            if (state.structure == null && !state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No tournament data",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Fetch the latest bracket from ESPN to get started.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = onRefresh) { Text("Fetch Data") }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(state.scores) { index, score ->
                        PlayerScoreCard(
                            rank        = index + 1,
                            score       = score,
                            scoringMode = state.scoringMode
                        )
                    }
                    if (state.scores.isEmpty() && state.structure != null) {
                        item {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No brackets on file yet.",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerScoreCard(rank: Int, score: PlayerScore, scoringMode: ScoringMode) {
    var expanded by remember { mutableStateOf(false) }

    val total = if (scoringMode == ScoringMode.TRADITIONAL) score.totalTraditional else score.totalCustom
    val max   = if (scoringMode == ScoringMode.TRADITIONAL) score.maxPossibleTraditional else score.maxPossibleCustom

    Card(
        onClick   = { expanded = !expanded },
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Rank badge
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = when (rank) {
                        1    -> MaterialTheme.colorScheme.tertiary
                        2    -> MaterialTheme.colorScheme.secondaryContainer
                        3    -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text       = "#$rank",
                            fontWeight = FontWeight.Bold,
                            fontSize   = 13.sp
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text       = score.playerName,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.weight(1f)
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text       = "$total pts",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text  = "max $max",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Round summary pills
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TournamentRound.ordered.forEach { round ->
                    val rs = score.roundBreakdown.find { it.round == round }
                    val pts = if (scoringMode == ScoringMode.TRADITIONAL) rs?.traditionalPoints ?: 0
                              else rs?.customPoints ?: 0
                    RoundPill(label = round.shortName, points = pts)
                }
            }

            // Expanded: per-round breakdown
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                score.roundBreakdown.forEach { rs ->
                    RoundBreakdownRow(rs, scoringMode)
                }
            }
        }
    }
}

@Composable
private fun RoundPill(label: String, points: Int) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.width(44.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 3.dp)
        ) {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Medium)
            Text("$points", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RoundBreakdownRow(rs: RoundScore, scoringMode: ScoringMode) {
    val pts = if (scoringMode == ScoringMode.TRADITIONAL) rs.traditionalPoints else rs.customPoints
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text     = rs.round.displayName,
            style    = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text      = "${rs.correct}/${rs.gamesWithResults}",
            style     = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier  = Modifier.width(48.dp)
        )
        Text(
            text      = "$pts pts",
            style     = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier  = Modifier.width(56.dp)
        )
    }
}
