package app.gamenative.service.GOG

import android.content.Context
import app.gamenative.data.DownloadInfo
import app.gamenative.data.GOGGame
import app.gamenative.db.dao.GOGGameDao
import app.gamenative.service.GameManager
import app.gamenative.service.GOG.GOGServiceChaquopy
import app.gamenative.utils.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GOGGameManager @Inject constructor(
    private val gogGameDao: GOGGameDao
) : GameManager {

    data class InstallInfo(
        val downloadSize: String,
        val installSize: String,
        val availableSpace: String,
        val hasEnoughSpace: Boolean,
        val installPath: String
    )

    /**
     * Get the default install path for GOG games
     */
    fun getDefaultInstallPath(context: Context): String {
        return "${context.dataDir.path}/gog_games"
    }

    /**
     * Get install path for a specific GOG game
     */
    fun getGameInstallPath(context: Context, gameId: String, gameTitle: String): String {
        // Sanitize game title for use as directory name
        val sanitizedTitle = gameTitle.replace(Regex("[^a-zA-Z0-9\\s-_]"), "").trim()
        return "${getDefaultInstallPath(context)}/$sanitizedTitle"
    }

    /**
     * Get GOG game by ID from database
     */
    suspend fun getGameById(gameId: String): GOGGame? = withContext(Dispatchers.IO) {
        try {
            gogGameDao.getById(gameId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get GOG game by ID: $gameId")
            null
        }
    }

    /**
     * Get install information for a GOG game
     */
    suspend fun getInstallInfo(context: Context, game: GOGGame): Result<InstallInfo> = withContext(Dispatchers.IO) {
        try {
            val installPath = getGameInstallPath(context, game.id, game.title)
            val availableBytes = StorageUtils.getAvailableSpace(context.dataDir.path)
            val availableSpace = StorageUtils.formatBinarySize(availableBytes)
            
            // Use game data if available, otherwise show unknown
            val downloadSize = if (game.downloadSize > 0) {
                StorageUtils.formatBinarySize(game.downloadSize)
            } else {
                "Unknown"
            }
            
            val installSize = if (game.installSize > 0) {
                StorageUtils.formatBinarySize(game.installSize)
            } else {
                "Unknown"
            }
            
            val hasEnoughSpace = if (game.installSize > 0) {
                availableBytes >= game.installSize
            } else {
                true // Assume we have enough space if size is unknown
            }
            
            val installInfo = InstallInfo(
                downloadSize = downloadSize,
                installSize = installSize,
                availableSpace = availableSpace,
                hasEnoughSpace = hasEnoughSpace,
                installPath = installPath
            )
            
            Result.success(installInfo)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get GOG install info for game ${game.id}")
            Result.failure(e)
        }
    }

    /**
     * Get install information for a GOG game by ID
     */
    suspend fun getInstallInfoById(context: Context, gameId: String): Result<InstallInfo> = withContext(Dispatchers.IO) {
        try {
            val game = getGameById(gameId)
            if (game != null) {
                getInstallInfo(context, game)
            } else {
                Result.failure(Exception("GOG game not found: $gameId"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get GOG install info for game ID: $gameId")
            Result.failure(e)
        }
    }

    // Implement GameManager interface
    override suspend fun installGame(context: Context, gameId: String, gameTitle: String): Result<DownloadInfo?> = withContext(Dispatchers.IO) {
        try {
            // Check authentication first
            if (!GOGServiceChaquopy.hasStoredCredentials(context)) {
                return@withContext Result.failure(Exception("GOG authentication required. Please log in to your GOG account first."))
            }
            
            val installPath = getGameInstallPath(context, gameId, gameTitle)
            val authConfigPath = "${context.filesDir}/gog_auth.json"
            
            Timber.i("Starting GOG game installation: $gameTitle to $installPath")
            
            // Use the new download method that returns DownloadInfo
            val result = GOGServiceChaquopy.downloadGame(gameId, installPath, authConfigPath)
            
            if (result.isSuccess) {
                val downloadInfo = result.getOrNull()
                Timber.i("GOG game installation started successfully: $gameTitle")
                Result.success(downloadInfo)
            } else {
                val error = result.exceptionOrNull() ?: Exception("Unknown download error")
                Timber.e(error, "Failed to install GOG game: $gameTitle")
                Result.failure(error)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to install GOG game: $gameTitle")
            Result.failure(e)
        }
    }

    override suspend fun deleteGame(context: Context, gameId: String, gameName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val installPath = getGameInstallPath(context, gameId, gameName)
            val installDir = File(installPath)
            
            if (installDir.exists()) {
                val success = installDir.deleteRecursively()
                if (success) {
                    // Update database to mark as not installed
                    val game = getGameById(gameId)
                    if (game != null) {
                        val updatedGame = game.copy(
                            isInstalled = false,
                            installPath = ""
                        )
                        gogGameDao.update(updatedGame)
                    }
                    
                    Timber.i("GOG game $gameName deleted successfully")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to delete GOG game directory"))
                }
            } else {
                Timber.w("GOG game directory doesn't exist: $installPath")
                // Update database anyway to ensure consistency
                val game = getGameById(gameId)
                if (game != null) {
                    val updatedGame = game.copy(
                        isInstalled = false,
                        installPath = ""
                    )
                    gogGameDao.update(updatedGame)
                }
                Result.success(Unit) // Consider it already deleted
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete GOG game $gameId")
            Result.failure(e)
        }
    }

    override suspend fun resumeDownload(context: Context, gameId: String, gameName: String): Result<DownloadInfo?> {
        return Result.failure(Exception("Resume not supported for GOG games yet"))
    }

    override suspend fun isGameInstalled(context: Context, gameId: String, gameName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val installPath = getGameInstallPath(context, gameId, gameName)
            val installDir = File(installPath)
            val isInstalled = installDir.exists() && installDir.listFiles()?.isNotEmpty() == true
            
            // Update database if the install status has changed
            val game = getGameById(gameId)
            if (game != null && isInstalled != game.isInstalled) {
                val updatedGame = game.copy(
                    isInstalled = isInstalled,
                    installPath = if (isInstalled) installPath else ""
                )
                gogGameDao.update(updatedGame)
            }
            
            isInstalled
        } catch (e: Exception) {
            Timber.e(e, "Failed to check install status for GOG game $gameId")
            false
        }
    }

    override fun getDownloadInfo(gameId: String): DownloadInfo? {
        return null // GOG doesn't have DownloadInfo yet
    }

    override fun hasPartialDownload(gameId: String): Boolean {
        return false // GOG doesn't support partial downloads yet
    }

    /**
     * Check if a GOG game is installed by ID (legacy method)
     */
    suspend fun isGameInstalledById(context: Context, gameId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val game = getGameById(gameId)
            if (game != null) {
                isGameInstalled(context, gameId, game.title)
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check install status for GOG game ID: $gameId")
            false
        }
    }

    /**
     * Install a GOG game by ID (legacy method)
     */
    suspend fun installGameById(context: Context, gameId: String): Result<DownloadInfo?> = withContext(Dispatchers.IO) {
        try {
            val game = getGameById(gameId)
            if (game != null) {
                installGame(context, gameId, game.title)
            } else {
                Result.failure(Exception("GOG game not found: $gameId"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to install GOG game by ID: $gameId")
            Result.failure(e)
        }
    }

    /**
     * Delete a GOG game by ID (legacy method)
     */
    suspend fun deleteGameById(context: Context, gameId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val game = getGameById(gameId)
            if (game != null) {
                deleteGame(context, gameId, game.title)
            } else {
                Result.failure(Exception("GOG game not found: $gameId"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete GOG game by ID: $gameId")
            Result.failure(e)
        }
    }

    /**
     * Get the size of an installed GOG game
     */
    suspend fun getInstalledGameSize(context: Context, game: GOGGame): Long = withContext(Dispatchers.IO) {
        try {
            val installPath = getGameInstallPath(context, game.id, game.title)
            StorageUtils.getFolderSize(installPath)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get size for GOG game ${game.id}")
            0L
        }
    }
}
