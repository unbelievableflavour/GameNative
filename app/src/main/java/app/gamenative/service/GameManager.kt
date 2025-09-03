package app.gamenative.service

import android.content.Context
import app.gamenative.data.DownloadInfo

interface GameManager {
    suspend fun installGame(context: Context, gameId: String, gameName: String): Result<DownloadInfo?>
    suspend fun deleteGame(context: Context, gameId: String, gameName: String): Result<Unit>
    suspend fun resumeDownload(context: Context, gameId: String, gameName: String): Result<DownloadInfo?>
    suspend fun isGameInstalled(context: Context, gameId: String, gameName: String): Boolean
    fun getDownloadInfo(gameId: String): DownloadInfo?
    fun hasPartialDownload(gameId: String): Boolean
}
