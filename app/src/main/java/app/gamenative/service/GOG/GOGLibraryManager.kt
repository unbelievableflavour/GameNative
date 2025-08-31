package app.gamenative.service.GOG

import android.content.Context
import app.gamenative.db.dao.GOGGameDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GOGLibraryManager @Inject constructor(
    private val gogGameDao: GOGGameDao
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
            
            // Get library from GOG service
            val libraryResult = GOGServiceChaquopy.getLibrary(authConfigPath)
            
            if (libraryResult.isSuccess) {
                val games = libraryResult.getOrThrow()
                Timber.i("Syncing ${games.size} GOG games to database")
                
                // Insert/update games in database
                gogGameDao.insertAll(games)
                
                Timber.i("GOG library sync completed successfully")
                Result.success(Unit)
            } else {
                val error = libraryResult.exceptionOrNull()
                Timber.e(error, "Failed to sync GOG library")
                Result.failure(error ?: Exception("Unknown error during GOG sync"))
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
}
