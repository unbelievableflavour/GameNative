package app.gamenative.data

import app.gamenative.Constants

/**
 * Data class for the Library list
 */
data class LibraryItem(
    val index: Int = 0,
    val appId: String = "0", // Changed to String for platform-prefixed IDs (steam_123, gog_456)
    val name: String = "",
    val iconHash: String = "",
    val isShared: Boolean = false,
    val gameSource: GameSource = GameSource.STEAM,
    val gogGameId: String? = null,
    val imageUrl: String = "",
) {
    // Helper to get numeric Steam app ID for backward compatibility
    val steamAppId: Int
        get() = if (gameSource == GameSource.STEAM && appId.startsWith("steam_")) {
            appId.removePrefix("steam_").toIntOrNull() ?: 0
        } else 0

    val clientIconUrl: String
        get() = when (gameSource) {
            GameSource.STEAM -> Constants.Library.ICON_URL + "${steamAppId}/$iconHash.ico" // Use numeric ID for Steam icon URLs
            GameSource.GOG -> imageUrl.ifEmpty { 
                // Fallback GOG icon URL if no image is provided
                "https://images.gog-statics.com/games/${gogGameId}_icon.jpg"
            }
        }
}
