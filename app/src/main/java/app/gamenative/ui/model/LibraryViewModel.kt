package app.gamenative.ui.model

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gamenative.PrefManager
import app.gamenative.data.Game
import app.gamenative.data.GameSource
import app.gamenative.data.GOGGameWrapper
import app.gamenative.data.LibraryItem
import app.gamenative.data.SteamGameWrapper
import app.gamenative.db.dao.GOGGameDao
import app.gamenative.db.dao.SteamAppDao
import app.gamenative.service.DownloadService
import app.gamenative.service.SteamService
import app.gamenative.ui.data.LibraryState
import app.gamenative.ui.enums.AppFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.EnumSet
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min

@HiltViewModel
class LibraryViewModel @Inject constructor(
private val steamAppDao: SteamAppDao,
private val gogGameDao: GOGGameDao,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    // Keep the library scroll state. This will last longer as the VM will stay alive.
    var listState: LazyListState by mutableStateOf(LazyListState(0, 0))

    // How many items loaded on one page of results
    private var paginationCurrentPage: Int = 0;
    private var lastPageInCurrentFilter: Int = 0;

    // Complete and unfiltered games from all sources
    private var allGames: List<Game> = emptyList()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // Combine Steam and GOG data flows
            kotlinx.coroutines.flow.combine(
                steamAppDao.getAllOwnedApps(),
                gogGameDao.getAll()
            ) { steamApps, gogGames ->
                Timber.tag("LibraryViewModel").d("Collecting ${steamApps.size} Steam apps and ${gogGames.size} GOG games")
                
                val games = mutableListOf<Game>()
                
                // Convert Steam apps to unified Game interface
                steamApps.forEach { steamApp ->
                    games.add(SteamGameWrapper(steamApp))
                }
                
                // Convert GOG games to unified Game interface
                gogGames.forEach { gogGame ->
                    games.add(GOGGameWrapper(gogGame))
                }
                
                games
            }.collect { games ->
                if (allGames.size != games.size) {
                    allGames = games
                    onFilterApps(paginationCurrentPage)
                }
            }
        }
    }

    fun onModalBottomSheet(value: Boolean) {
        _state.update { it.copy(modalBottomSheet = value) }
    }

    fun onIsSearching(value: Boolean) {
        _state.update { it.copy(isSearching = value) }
        if (!value) {
            onSearchQuery("")
        }
    }

    fun onSearchQuery(value: String) {
        _state.update { it.copy(searchQuery = value) }
        onFilterApps()
    }

    // TODO: include other sort types
    fun onFilterChanged(value: AppFilter) {
        _state.update { currentState ->
            val updatedFilter = EnumSet.copyOf(currentState.appInfoSortType)

            if (updatedFilter.contains(value)) {
                updatedFilter.remove(value)
            } else {
                updatedFilter.add(value)
            }

            PrefManager.libraryFilter = updatedFilter

            currentState.copy(appInfoSortType = updatedFilter)
        }

        onFilterApps()
    }

    fun onPageChange(pageIncrement: Int) {
        // Amount to change by
        var toPage = max(0, paginationCurrentPage + pageIncrement)
        toPage = min(toPage, lastPageInCurrentFilter)
        onFilterApps(toPage)
    }

    private fun onFilterApps(paginationPage: Int = 0) {
        // May be filtering 1000+ apps - in future should paginate at the point of DAO request
        Timber.tag("LibraryViewModel").d("onFilterApps")
        viewModelScope.launch {
            val currentState = _state.value
            val currentFilter = AppFilter.getAppType(currentState.appInfoSortType)

            // Apply filters to the unified games - SINGLE LOOP!
            val filteredGames = allGames
                .asSequence()
                .filter { game ->
                    // Platform filter
                    when {
                        currentState.appInfoSortType.contains(AppFilter.STEAM) && currentState.appInfoSortType.contains(AppFilter.GOG) -> true
                        currentState.appInfoSortType.contains(AppFilter.STEAM) -> game.source == GameSource.STEAM
                        currentState.appInfoSortType.contains(AppFilter.GOG) -> game.source == GameSource.GOG
                        else -> true
                    }
                }
                .filter { game ->
                    // Search filter
                    if (currentState.searchQuery.isNotEmpty()) {
                        game.name.contains(currentState.searchQuery, ignoreCase = true)
                    } else {
                        true
                    }
                }
                .filter { game ->
                    // Installation filter
                    if (currentState.appInfoSortType.contains(AppFilter.INSTALLED)) {
                        game.isInstalled
                    } else {
                        true
                    }
                }
                .filter { game ->
                    // Shared filter (only applies to Steam)
                    if (currentState.appInfoSortType.contains(AppFilter.SHARED)) {
                        true
                    } else {
                        !game.isShared
                    }
                }
                .filter { game ->
                    // Type filter (only applies to Steam games with types)
                    if (currentFilter.isNotEmpty() && game is SteamGameWrapper) {
                        currentFilter.contains(game.originalApp.type)
                    } else {
                        true
                    }
                }
                .sortedWith(
                    compareBy<Game> { it.source != GameSource.STEAM } // Steam games first
                        .thenBy { it.name.lowercase() } // Then alphabetical
                )
                .toList()

            // Convert to LibraryItems
            val libraryItems = filteredGames.mapIndexed { index, game ->
                game.toLibraryItem(index)
            }

            // Total count for the current filter
            val totalFound = libraryItems.size

            // Determine how many pages and slice the list for incremental loading
            val pageSize = PrefManager.itemsPerPage
            // Update internal pagination state
            paginationCurrentPage = paginationPage
            lastPageInCurrentFilter = if (totalFound > 0) (totalFound - 1) / pageSize else 0
            // Calculate how many items to show: (pagesLoaded * pageSize)
            val endIndex = min((paginationPage + 1) * pageSize, totalFound)
            val filteredListPage = libraryItems.take(endIndex)

            Timber.tag("LibraryViewModel").d("Filtered list size: ${totalFound}")
            _state.update {
                it.copy(
                    appInfoList = filteredListPage,
                    currentPaginationPage = paginationPage + 1, // visual display is not 0 indexed
                    lastPaginationPage = lastPageInCurrentFilter + 1,
                    totalAppsInFilter = totalFound,
                    )
            }
        }
    }
}
