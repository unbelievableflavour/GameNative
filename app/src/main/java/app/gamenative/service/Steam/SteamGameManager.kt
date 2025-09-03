package app.gamenative.service.Steam

import android.content.Context
import app.gamenative.data.DownloadInfo
import app.gamenative.service.GameManager
import app.gamenative.service.SteamService
import app.gamenative.data.PostSyncInfo
import app.gamenative.enums.PathType
import app.gamenative.enums.SaveLocation
import kotlinx.coroutines.CoroutineScope
import app.gamenative.utils.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SteamGameManager @Inject constructor() : GameManager {

    data class InstallInfo(
        val downloadSize: String,
        val installSize: String,
        val availableSpace: String,
        val hasEnoughSpace: Boolean,
        val installPath: String
    )

    /**
     * Get install information for a Steam game
     */
    suspend fun getInstallInfo(appId: Int): Result<InstallInfo> = withContext(Dispatchers.IO) {
        try {
            val depots = SteamService.getDownloadableDepots(appId)
            Timber.i("There are ${depots.size} depots belonging to $appId")
            
            val storagePath = SteamService.defaultStoragePath
            if (storagePath.isEmpty()) {
                return@withContext Result.failure(Exception("Steam storage path is not available"))
            }
            
            val availableBytes = StorageUtils.getAvailableSpace(storagePath)
            val availableSpace = StorageUtils.formatBinarySize(availableBytes)
            
            // TODO: un-hardcode "public" branch
            val downloadSize = StorageUtils.formatBinarySize(
                depots.values.sumOf {
                    it.manifests["public"]?.download ?: 0
                },
            )
            val installBytes = depots.values.sumOf { it.manifests["public"]?.size ?: 0 }
            val installSize = StorageUtils.formatBinarySize(installBytes)
            val hasEnoughSpace = availableBytes >= installBytes
            
            val installInfo = InstallInfo(
                downloadSize = downloadSize,
                installSize = installSize,
                availableSpace = availableSpace,
                hasEnoughSpace = hasEnoughSpace,
                installPath = SteamService.getAppDirPath(appId)
            )
            
            Result.success(installInfo)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get Steam install info for app $appId")
            Result.failure(e)
        }
    }

    /**
     * Install a Steam game
     */
    override suspend fun installGame(context: Context, gameId: String, gameName: String): Result<DownloadInfo?> = withContext(Dispatchers.IO) {
        try {
            val appId = if (gameId.startsWith("steam_")) {
                gameId.removePrefix("steam_").toInt()
            } else {
                gameId.toInt() // fallback for legacy numeric IDs
            }
            val downloadInfo = SteamService.downloadApp(appId)
            if (downloadInfo != null) {
                Result.success(downloadInfo)
            } else {
                Result.failure(Exception("Failed to start Steam game download"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to install Steam game $gameId")
            Result.failure(e)
        }
    }

    /**
     * Delete a Steam game
     */
    override suspend fun deleteGame(context: Context, gameId: String, gameName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val appId = if (gameId.startsWith("steam_")) {
                gameId.removePrefix("steam_").toInt()
            } else {
                gameId.toInt() // fallback for legacy numeric IDs
            }
            val success = SteamService.deleteApp(appId)
            if (success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete Steam game files"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete Steam game $gameId")
            Result.failure(e)
        }
    }

    /**
     * Check if a Steam game is installed
     */
    override suspend fun isGameInstalled(context: Context, gameId: String, gameName: String): Boolean {
        return try {
            val appId = if (gameId.startsWith("steam_")) {
                gameId.removePrefix("steam_").toInt()
            } else {
                gameId.toInt() // fallback for legacy numeric IDs
            }
            SteamService.isAppInstalled(appId)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get download info for a Steam game if it's currently downloading
     */
    override fun getDownloadInfo(gameId: String): DownloadInfo? {
        return try {
            val appId = if (gameId.startsWith("steam_")) {
                gameId.removePrefix("steam_").toInt()
            } else {
                gameId.toInt() // fallback for legacy numeric IDs
            }
            SteamService.getAppDownloadInfo(appId)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if a Steam game has a partial download
     */
    override fun hasPartialDownload(gameId: String): Boolean {
        return try {
            val appId = if (gameId.startsWith("steam_")) {
                gameId.removePrefix("steam_").toInt()
            } else {
                gameId.toInt() // fallback for legacy numeric IDs
            }
            SteamService.hasPartialDownload(appId)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Resume a partially downloaded Steam game
     */
    override suspend fun resumeDownload(context: Context, gameId: String, gameName: String): Result<DownloadInfo?> = withContext(Dispatchers.IO) {
        try {
            val appId = if (gameId.startsWith("steam_")) {
                gameId.removePrefix("steam_").toInt()
            } else {
                gameId.toInt() // fallback for legacy numeric IDs
            }
            if (hasPartialDownload(gameId)) {
                val downloadInfo = SteamService.downloadApp(appId)
                if (downloadInfo != null) {
                    Result.success(downloadInfo)
                } else {
                    Result.failure(Exception("Failed to resume Steam game download"))
                }
            } else {
                Result.failure(Exception("No partial download found"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to resume Steam game download for $gameId")
            Result.failure(e)
        }
    }

    override suspend fun launchGameWithSaveSync(
        context: Context,
        gameId: String,
        gameName: String,
        parentScope: CoroutineScope,
        ignorePendingOperations: Boolean,
        preferredSave: Int?
    ): PostSyncInfo = withContext(Dispatchers.IO) {
        try {
            val appId = if (gameId.startsWith("steam_")) {
                gameId.removePrefix("steam_").toInt()
            } else {
                gameId.toInt() // fallback for legacy numeric IDs
            }
            Timber.i("Starting Steam game launch with save sync for $gameName (appId: $appId)")
            
            // Use existing Steam save sync logic
            val prefixToPath: (String) -> String = { prefix ->
                PathType.from(prefix).toAbsPath(context, appId, SteamService.userSteamId!!.accountID)
            }
            
            // Convert Int? to SaveLocation
            val saveLocation = when (preferredSave) {
                0 -> SaveLocation.Local
                1 -> SaveLocation.Remote
                else -> SaveLocation.None
            }
            
            val postSyncInfo = SteamService.beginLaunchApp(
                appId = appId,
                prefixToPath = prefixToPath,
                ignorePendingOperations = ignorePendingOperations,
                preferredSave = saveLocation,
                parentScope = parentScope,
            ).await()
            
            Timber.i("Steam game save sync completed for $gameName")
            postSyncInfo
            
        } catch (e: Exception) {
            Timber.e(e, "Steam game launch with save sync failed for $gameId")
            PostSyncInfo(app.gamenative.enums.SyncResult.UnknownFail)
        }
    }
}
