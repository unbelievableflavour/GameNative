package app.gamenative.data

import app.gamenative.Constants

/**
 * Data class for the Library list
 */
data class LibraryItem(
    val index: Int = 0,
    val appId: Int = 0,
    val name: String = "",
    val iconHash: String = "",
    val isShared: Boolean = false,
    val gameSource: GameSource = GameSource.STEAM,
    val gogGameId: String? = null,
    val imageUrl: String = "",
) {
    val clientIconUrl: String
        get() = when (gameSource) {
            GameSource.STEAM -> Constants.Library.ICON_URL + "$appId/$iconHash.ico"
            GameSource.GOG -> imageUrl.ifEmpty { 
                // Fallback GOG icon URL if no image is provided
                "https://images.gog-statics.com/games/${gogGameId}_icon.jpg"
            }
        }
}
