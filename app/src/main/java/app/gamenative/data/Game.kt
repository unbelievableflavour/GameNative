package app.gamenative.data

import app.gamenative.Constants
import app.gamenative.service.DownloadService
import app.gamenative.service.SteamService

/**
 * Unified interface for all game types (Steam, GOG, etc.)
 */
interface Game {
    val id: String
    val name: String
    val source: GameSource
    val isInstalled: Boolean
    val isShared: Boolean
    val iconUrl: String
    
    fun toLibraryItem(index: Int): LibraryItem
}

/**
 * Steam game implementation
 */
data class SteamGameWrapper(
    private val steamApp: SteamApp
) : Game {
    override val id: String get() = steamApp.id.toString()
    override val name: String get() = steamApp.name
    override val source: GameSource get() = GameSource.STEAM
    
    override val isInstalled: Boolean get() {
        val downloadDirectoryApps = DownloadService.getDownloadDirectoryApps()
        return downloadDirectoryApps.contains(SteamService.getAppDirName(steamApp))
    }
    
    override val isShared: Boolean get() {
        val thisSteamId: Int = SteamService.userSteamId?.accountID?.toInt() ?: 0
        return thisSteamId != 0 && !steamApp.ownerAccountId.contains(thisSteamId)
    }
    
    override val iconUrl: String get() = 
        Constants.Library.ICON_URL + "${steamApp.id}/${steamApp.clientIconHash}.ico"
    
    override fun toLibraryItem(index: Int): LibraryItem = LibraryItem(
        index = index,
        appId = "steam_${steamApp.id}", // Platform-prefixed ID
        name = steamApp.name,
        iconHash = steamApp.clientIconHash,
        isShared = isShared,
        gameSource = GameSource.STEAM
    )
    
    // Access to original Steam app for type checking, etc.
    val originalApp: SteamApp get() = steamApp
}

/**
 * GOG game implementation
 */
data class GOGGameWrapper(
    private val gogGame: GOGGame
) : Game {
    override val id: String get() = gogGame.id
    override val name: String get() = gogGame.title
    override val source: GameSource get() = GameSource.GOG
    override val isInstalled: Boolean get() = gogGame.isInstalled
    override val isShared: Boolean get() = false // GOG games are never shared
    override val iconUrl: String get() = gogGame.imageUrl
    
    override fun toLibraryItem(index: Int): LibraryItem = LibraryItem(
        index = index,
        appId = "gog_${gogGame.id}", // Platform-prefixed ID
        name = gogGame.title,
        iconHash = "",
        isShared = false,
        gameSource = GameSource.GOG,
        gogGameId = gogGame.id,
        imageUrl = gogGame.imageUrl
    )
    
    // Access to original GOG game for additional properties
    val originalGame: GOGGame get() = gogGame
}
