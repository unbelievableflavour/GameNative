package app.gamenative.service

import android.content.Context
import app.gamenative.data.GameSource
import app.gamenative.data.LibraryItem
import app.gamenative.service.GOG.GOGGameManager
import app.gamenative.service.Steam.SteamGameManager
import app.gamenative.db.dao.GOGGameDao
import app.gamenative.data.GOGGame
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber
import app.gamenative.data.PostSyncInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameLaunchService @Inject constructor(
    private val steamGameManager: SteamGameManager,
    private val gogGameManager: GOGGameManager,
    private val gogGameDao: GOGGameDao
) {

    /**
     * Launch a game with appropriate save sync based on LibraryItem
     */
    suspend fun launchGameWithSaveSync(
        context: Context,
        libraryItem: LibraryItem,
        parentScope: CoroutineScope,
        ignorePendingOperations: Boolean = false,
        preferredSave: Int? = null
    ): PostSyncInfo {
        try {
            val (gameId, gameName) = when (libraryItem.gameSource) {
                GameSource.STEAM -> {
                    val steamGameId = libraryItem.appId.toString()
                    val steamGameName = libraryItem.name
                    Pair(steamGameId, steamGameName)
                }
                GameSource.GOG -> {
                    val gogGameId = libraryItem.gogGameId ?: run {
                        Timber.w("GOG game has null gogGameId: ${libraryItem.name}")
                        return PostSyncInfo(app.gamenative.enums.SyncResult.UnknownFail)
                    }
                    Pair(gogGameId, libraryItem.name)
                }
            }
            
            Timber.i("Launching ${libraryItem.gameSource} game: $gameName (ID: $gameId)")
            
            return when (libraryItem.gameSource) {
                GameSource.STEAM -> {
                    steamGameManager.launchGameWithSaveSync(
                        context = context,
                        gameId = gameId,
                        gameName = gameName,
                        parentScope = parentScope,
                        ignorePendingOperations = ignorePendingOperations,
                        preferredSave = preferredSave
                    )
                }
                GameSource.GOG -> {
                    gogGameManager.launchGameWithSaveSync(
                        context = context,
                        gameId = gameId,
                        gameName = gameName,
                        parentScope = parentScope,
                        ignorePendingOperations = ignorePendingOperations,
                        preferredSave = preferredSave
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch game: ${libraryItem.name} (${libraryItem.gameSource})")
            return PostSyncInfo(app.gamenative.enums.SyncResult.UnknownFail)
        }
    }
}
