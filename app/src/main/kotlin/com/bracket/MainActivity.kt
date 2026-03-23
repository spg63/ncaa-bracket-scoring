package com.bracket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bracket.storage.Storage
import com.bracket.ui.BracketEntryViewModel
import com.bracket.ui.MainViewModel
import com.bracket.ui.screens.BracketEntryScreen
import com.bracket.ui.screens.LeaderboardScreen
import com.bracket.ui.screens.PlayerPicksScreen
import com.bracket.ui.theme.BracketTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BracketTheme {
                BracketApp()
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    LEADERBOARD("Leaderboard", Icons.Default.EmojiEvents),
    ENTER("Bracket",   Icons.Default.Edit),
    PICKS("Picks",     Icons.Default.Person)
}

@Composable
private fun BracketApp() {
    val mainVm: MainViewModel          = viewModel()
    val entryVm: BracketEntryViewModel = viewModel()
    val state by mainVm.state.collectAsState()

    var selectedTab by remember { mutableStateOf(Tab.LEADERBOARD) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick  = { selectedTab = tab },
                        icon     = { Icon(tab.icon, contentDescription = tab.label) },
                        label    = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        val ctx = androidx.compose.ui.platform.LocalContext.current

        when (selectedTab) {
            Tab.LEADERBOARD -> LeaderboardScreen(
                state                 = state,
                onRefresh             = { mainVm.refresh() },
                onSetScoringMode      = { mainVm.setScoringMode(it) },
                onClearError          = { mainVm.clearError() },
                onClearSuccessMessage = { mainVm.clearSuccessMessage() },
                modifier              = Modifier.padding(innerPadding)
            )
            Tab.ENTER -> BracketEntryScreen(
                structure       = state.structure,
                entryViewModel  = entryVm,
                onBracketSaved  = { mainVm.reloadBrackets() },
                loadBracket     = { Storage.loadBracket(ctx, it) },
                listPlayers     = { Storage.listBracketPlayers(ctx) },
                modifier        = Modifier.padding(innerPadding)
            )
            Tab.PICKS -> PlayerPicksScreen(
                structure     = state.structure,
                listPlayers   = { Storage.listBracketPlayers(ctx) },
                loadBracket   = { Storage.loadBracket(ctx, it) },
                modifier      = Modifier.padding(innerPadding)
            )
        }
    }
}
