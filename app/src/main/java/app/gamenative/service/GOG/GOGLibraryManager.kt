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
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GOGLibraryManager @Inject constructor(
    private val gogGameDao: GOGGameDao,
    private val gamesDBService: GamesDBService
) {

    /**
     * Sync GOG library from the service to the local database
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
     * Clear all GOG games from the local database
     */
    suspend fun clearLibrary(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            gogGameDao.deleteAll()
            Timber.i("GOG library cleared from database")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear GOG library")
            Result.failure(e)
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
                val enhancedGame = game.copy(
                    imageUrl = gamesDBService.getBestIconUrl(gamesDbData)
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
