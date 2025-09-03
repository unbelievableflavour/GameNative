package app.gamenative.service.GOG

import android.content.Context
import app.gamenative.PluviaApp
import app.gamenative.data.GOGGame
import app.gamenative.db.dao.GOGGameDao
import app.gamenative.enums.LoginResult
import app.gamenative.events.SteamEvent
import app.gamenative.service.GamesDBService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class SyncProgress(
    val isRunning: Boolean = false,
    val totalGames: Int = 0,
    val syncedGames: Int = 0,
    val currentGame: String? = null,
    val error: String? = null
)

@Singleton
class GOGLibraryManager @Inject constructor(
    private val gogGameDao: GOGGameDao,
    private val gamesDBService: GamesDBService
) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Background sync progress state
    private val _syncProgress = MutableStateFlow(SyncProgress())
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()
    
    // Track if background sync is already running
    private var backgroundSyncInProgress = false
    
    // Batch size for progressive sync
    private val BATCH_SIZE = 10

    /**
     * Start background library sync that progressively syncs games in batches
     */
    fun startBackgroundSync(context: Context, clearExisting: Boolean = false) {
        if (backgroundSyncInProgress) {
            Timber.i("Background GOG sync already in progress, skipping")
            return
        }
        
        scope.launch {
            backgroundSyncInProgress = true
            syncLibraryInBackground(context, clearExisting)
            backgroundSyncInProgress = false
        }
    }
    
    /**
     * Clear all GOG games from the database
     */
    suspend fun clearLibrary(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Timber.i("Clearing GOG library from database")
            gogGameDao.deleteAll()
            Timber.i("GOG library cleared successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear GOG library")
            Result.failure(e)
        }
    }
    
    /**
     * Background sync implementation with true progressive syncing
     * Games appear in the library as soon as they're fetched from GOG API
     */
    private suspend fun syncLibraryInBackground(context: Context, clearExisting: Boolean = false) {
        try {
            Timber.i("Starting progressive background GOG library sync...")
            
            val authConfigPath = "${context.filesDir}/gog_auth.json"
            
            // Check if GOG credentials exist
            if (!GOGServiceChaquopy.hasStoredCredentials(context)) {
                Timber.w("No GOG credentials found, skipping background sync")
                return
            }
            
            _syncProgress.value = SyncProgress(isRunning = true)
            
            // Clear existing games if requested
            if (clearExisting) {
                Timber.i("Clearing existing GOG games before sync")
                clearLibrary()
                _syncProgress.value = _syncProgress.value.copy(currentGame = "Cleared existing games...")
            }
            
            // Try progressive sync first (if available), fallback to batch sync
            val progressiveResult = syncLibraryProgressively(authConfigPath)
            
            if (progressiveResult.isFailure) {
                Timber.w("Progressive sync not available, falling back to batch sync")
                // Fallback to original batch method
                syncLibraryBatch(authConfigPath)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Exception during background GOG sync")
            _syncProgress.value = _syncProgress.value.copy(
                isRunning = false,
                error = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * Attempt progressive sync - insert games one by one as they're fetched
     * This would require a modified Python script that yields games individually
     */
    private suspend fun syncLibraryProgressively(authConfigPath: String): Result<Unit> {
        return try {
            Timber.i("Attempting progressive GOG library sync...")
            
            // For now, this is a placeholder for future progressive implementation
            // The Python script would need to be modified to yield games one by one
            // rather than returning the complete list
            
            Result.failure(Exception("Progressive sync not yet implemented"))
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Batch sync method (current implementation) 
     * Insert all games at once, then enhance progressively
     */
    private suspend fun syncLibraryBatch(authConfigPath: String) {
        try {
            // Get library from GOG service (this is still the bottleneck)
            val libraryResult = GOGServiceChaquopy.getLibrary(authConfigPath, gogGameDao)
            
            if (libraryResult.isSuccess) {
                val games = libraryResult.getOrThrow()
                Timber.i("Background syncing ${games.size} GOG games to database")
                
                _syncProgress.value = _syncProgress.value.copy(totalGames = games.size)
                
                if (games.isNotEmpty()) {
                    // PHASE 1: Insert all games immediately with original GOG images (fast)
                    Timber.i("Phase 1: Inserting ${games.size} GOG games with original images for immediate display")
                    gogGameDao.insertAll(games)
                    
                    _syncProgress.value = _syncProgress.value.copy(
                        syncedGames = games.size,
                        currentGame = "Games loaded with original icons, enhancing with better metadata..."
                    )
                    
                    // PHASE 2: Enhance games with GamesDB data in background (slow, optional)
                    Timber.i("Phase 2: Starting background enhancement with GamesDB data for better icons")
                    
                    // Only enhance games that might benefit from better images
                    val gamesToEnhance = games.filter { game ->
                        game.imageUrl.isEmpty() || game.imageUrl.contains("gog-statics.com")
                    }
                    
                    Timber.i("Enhancing ${gamesToEnhance.size} games that could benefit from better icons")
                    
                    gamesToEnhance.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                        try {
                            // Enhance batch with GamesDB data (this is the slow part)
                            val enhancedBatch = enhanceGamesWithGamesDB(batch)
                            
                            // Update enhanced games in database
                            gogGameDao.insertAll(enhancedBatch)
                            
                            val enhancedCount = (batchIndex + 1) * BATCH_SIZE.coerceAtMost(batch.size)
                            val currentGame = batch.lastOrNull()?.title
                            
                            _syncProgress.value = _syncProgress.value.copy(
                                currentGame = "Enhanced $enhancedCount/${gamesToEnhance.size}: $currentGame"
                            )
                            
                            Timber.d("Background enhancement progress: $enhancedCount/${gamesToEnhance.size} games enhanced")
                            
                            // Small delay to prevent overwhelming the GamesDB API
                            delay(200)
                            
                        } catch (e: Exception) {
                            Timber.e(e, "Error enhancing batch $batchIndex")
                            // Continue with next batch - games will remain without enhancement
                        }
                    }
                    
                    Timber.i("Background GOG library sync completed successfully with ${games.size} games")
                } else {
                    Timber.i("No GOG games to sync in background")
                }
                
                _syncProgress.value = _syncProgress.value.copy(isRunning = false, currentGame = null)
                
            } else {
                val error = libraryResult.exceptionOrNull()
                Timber.e(error, "Background GOG library sync failed")
                _syncProgress.value = _syncProgress.value.copy(
                    isRunning = false,
                    error = error?.message ?: "Unknown sync error"
                )
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Exception during batch GOG sync")
            _syncProgress.value = _syncProgress.value.copy(
                isRunning = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Sync GOG library from the service to the local database (original blocking method)
     */
    suspend fun syncLibrary(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Timber.i("Starting GOG library sync...")
            
            val authConfigPath = "${context.filesDir}/gog_auth.json"
            
            // Check if GOG credentials exist
            if (!GOGServiceChaquopy.hasStoredCredentials(context)) {
                Timber.w("No GOG credentials found, skipping sync")
                return@withContext Result.success(Unit)
            }
            
            // Get library from GOG service with caching
            val libraryResult = GOGServiceChaquopy.getLibrary(authConfigPath, gogGameDao)
            
            if (libraryResult.isSuccess) {
                val games = libraryResult.getOrThrow()
                Timber.i("Syncing ${games.size} GOG games to database")
                
                if (games.isNotEmpty()) {
                    // Enhance games with GamesDB data
                    val enhancedGames = enhanceGamesWithGamesDB(games)
                    
                    // Insert/update games in database
                    gogGameDao.insertAll(enhancedGames)
                    
                    Timber.i("GOG library sync completed successfully with ${enhancedGames.size} games")
                } else {
                    Timber.i("No GOG games to sync")
                }
                
                Result.success(Unit)
            } else {
                val error = libraryResult.exceptionOrNull()
                val errorMessage = error?.message ?: "Unknown error during GOG sync"
                
                if (errorMessage.contains("authentication", ignoreCase = true)) {
                    Timber.w("GOG authentication issue: $errorMessage")
                    Result.failure(Exception("GOG authentication required: $errorMessage"))
                } else {
                    Timber.e(error, "Failed to sync GOG library: $errorMessage")
                    Result.failure(error ?: Exception(errorMessage))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception during GOG library sync")
            Result.failure(e)
        }
    }

    /**
     * Get the count of games in the local database
     */
    suspend fun getLocalGameCount(): Int = withContext(Dispatchers.IO) {
        try {
            gogGameDao.getAllAsList().size
        } catch (e: Exception) {
            Timber.e(e, "Failed to get local GOG game count")
            0
        }
    }



    /**
     * Enhance GOG games with GamesDB metadata for better icons and artwork
     */
    private suspend fun enhanceGamesWithGamesDB(games: List<GOGGame>): List<GOGGame> = withContext(Dispatchers.IO) {
        val enhancedGames = mutableListOf<GOGGame>()
        
        games.forEach { game ->
            try {
                Timber.d("Enhancing game ${game.title} (${game.id}) with GamesDB data")
                
                // Fetch GamesDB data for this GOG game
                val gamesDbResult = gamesDBService.getGamesdbData(
                    store = "gog",
                    gameId = game.id,
                    forceUpdate = false
                )
                
                val gamesDbData = gamesDbResult.getOrNull()?.data
                
                if (gamesDbData == null) {
                    // Handle both failure and null data cases
                    if (gamesDbResult.isFailure) {
                        Timber.w("Failed to fetch GamesDB data for ${game.title}: ${gamesDbResult.exceptionOrNull()?.message}")
                    } else {
                        Timber.d("No GamesDB data found for ${game.title}")
                    }
                    enhancedGames.add(game) // Add original game without enhancement
                    return@forEach // Early return from forEach
                }
                
                // Happy path - we have valid data
                Timber.d("Found GamesDB data for ${game.title}: ${gamesDbData.getTitleString()}")
                
                // Create enhanced game with GamesDB image URLs
                // Preserve original GOG image if GamesDB doesn't provide a better one
                val gamesDbImageUrl = gamesDBService.getBestIconUrl(gamesDbData)
                val enhancedGame = game.copy(
                    imageUrl = gamesDbImageUrl.ifEmpty { game.imageUrl }
                )
                
                enhancedGames.add(enhancedGame)
                Timber.d("Enhanced ${game.title} with GamesDB icons: icon=${gamesDbData.getIconUrl()}, squareIcon=${gamesDbData.getSquareIconUrl()}, logo=${gamesDbData.getLogoUrl()}")
            } catch (e: Exception) {
                Timber.e(e, "Exception enhancing ${game.title} with GamesDB data")
                enhancedGames.add(game) // Add original game without enhancement
            }
        }
        
        Timber.i("Enhanced ${enhancedGames.size} games with GamesDB data")
        enhancedGames
    }
}
