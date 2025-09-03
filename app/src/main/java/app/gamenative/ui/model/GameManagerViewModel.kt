package app.gamenative.ui.model

import android.content.Context
import androidx.lifecycle.ViewModel
import app.gamenative.data.DownloadInfo
import app.gamenative.data.LibraryItem
import app.gamenative.data.GameSource
import app.gamenative.service.GameManager
import app.gamenative.service.GOG.GOGGameManager
import app.gamenative.service.Steam.SteamGameManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class GameManagerViewModel @Inject constructor(
    private val steamGameManager: SteamGameManager,
    private val gogGameManager: GOGGameManager
) : ViewModel() {

    // Simple mapping at the top of the file
    private fun getManagerForGame(game: LibraryItem): GameManager {
        return when (game.gameSource) {
            GameSource.STEAM -> steamGameManager
            GameSource.GOG -> gogGameManager
        }
    }

    private fun getGameId(game: LibraryItem): String {
        return when (game.gameSource) {
            GameSource.STEAM -> game.appId.toString()
            GameSource.GOG -> game.gogGameId ?: ""
        }
    }

    suspend fun installGame(context: Context, game: LibraryItem): DownloadInfo? {
        return getManagerForGame(game).installGame(context, getGameId(game), game.name).getOrNull()
    }

    suspend fun deleteGame(context: Context, game: LibraryItem): Boolean {
        return getManagerForGame(game).deleteGame(context, getGameId(game), game.name).isSuccess
    }

    suspend fun resumeDownload(context: Context, game: LibraryItem): DownloadInfo? {
        return getManagerForGame(game).resumeDownload(context, getGameId(game), game.name).getOrNull()
    }

    suspend fun isGameInstalled(context: Context, game: LibraryItem): Boolean {
        return getManagerForGame(game).isGameInstalled(context, getGameId(game), game.name)
    }

    fun getDownloadInfo(game: LibraryItem): DownloadInfo? {
        return getManagerForGame(game).getDownloadInfo(getGameId(game))
    }

    fun hasPartialDownload(game: LibraryItem): Boolean {
        return getManagerForGame(game).hasPartialDownload(getGameId(game))
    }
}
