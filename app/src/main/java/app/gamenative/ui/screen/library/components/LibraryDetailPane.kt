package app.gamenative.ui.screen.library.components

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import app.gamenative.PrefManager
import app.gamenative.data.GameSource
import app.gamenative.data.LibraryItem
import app.gamenative.service.GOG.SyncProgress
import app.gamenative.service.SteamService
import app.gamenative.ui.data.LibraryState
import app.gamenative.ui.enums.AppFilter
import androidx.hilt.navigation.compose.hiltViewModel
import app.gamenative.ui.model.GameManagerViewModel
import app.gamenative.ui.screen.library.AppScreen
import app.gamenative.ui.theme.PluviaTheme
import java.util.EnumSet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryDetailPane(
    game: LibraryItem,
    gameManagerViewModel: GameManagerViewModel,
    onClickPlay: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Surface {
        if (game.appId == SteamService.INVALID_APP_ID && game.gameSource == GameSource.STEAM) {
            // Simply use the regular LibraryListPane with empty data
            val listState = rememberLazyListState()
            val sheetState = rememberModalBottomSheetState()
            val emptyState = remember {
                LibraryState(
                    appInfoList = emptyList(),
                    // Use the same default filter as in PrefManager (GAME)
                    appInfoSortType = EnumSet.of(AppFilter.GAME)
                )
            }

            LibraryListPane(
                state = emptyState,
                gogSyncProgress = SyncProgress(),
                listState = listState,
                sheetState = sheetState,
                gameManagerViewModel = gameManagerViewModel,
                onFilterChanged = {},
                onPageChange = {},
                onModalBottomSheet = {},
                onIsSearching = {},
                onLogout = {},
                onNavigate = {},
                onSearchQuery = {},
                onNavigateRoute = {},
            )
        } else {
            AppScreen(
                game = game,
                gameManagerViewModel = gameManagerViewModel,
                onClickPlay = onClickPlay,
                onBack = onBack,
            )
        }
    }
}

/***********
 * PREVIEW *
 ***********/

@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES or android.content.res.Configuration.UI_MODE_TYPE_NORMAL)
@Preview(device = "spec:width=1920px,height=1080px,dpi=440") // Odin2 Mini
@Composable
private fun Preview_LibraryDetailPane() {
    PrefManager.init(LocalContext.current)
    PluviaTheme {
        LibraryDetailPane(
            game = LibraryItem(
                index = 0,
                appId = Int.MAX_VALUE,
                name = "Test Game",
                iconHash = "",
                isShared = false,
                gameSource = GameSource.STEAM
            ),
            gameManagerViewModel = hiltViewModel(),
            onClickPlay = { },
            onBack = { },
        )
    }
}
