package app.gamenative.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data models for GamesDB API response
 */
@Serializable
data class GamesDBData(
    val id: String,
    val title: JsonElement? = null, // Can be string or object with language variants
    val slug: String? = null,
    @SerialName("background_image")
    val backgroundImage: String? = null,
    @SerialName("background_image_additional")
    val backgroundImageAdditional: String? = null,
    val screenshots: List<GamesDBScreenshot>? = null,
    val videos: List<GamesDBVideo>? = null,
    val artworks: List<GamesDBImage>? = null,
    val logo: JsonElement? = null,
    val icon: JsonElement? = null,
    @SerialName("square_icon")
    val squareIcon: JsonElement? = null,
    @SerialName("horizontal_artwork")
    val horizontalArtwork: String? = null,
    @SerialName("vertical_artwork")
    val verticalArtwork: String? = null,
    var etag: String? = null // Store ETag for caching
) {
    /**
     * Extract the title as a string from the potentially complex title field
     */
    fun getTitleString(): String? {
        return try {
            when {
                title == null -> null
                title is JsonObject -> {
                    // Handle object with language variants: {"*":"Game Name","en-US":"Game Name"}
                    val titleObj = title as JsonObject
                    // Try to get English title first, then fallback to default "*", then any available
                    titleObj["en-US"]?.jsonPrimitive?.content
                        ?: titleObj["*"]?.jsonPrimitive?.content
                        ?: titleObj.values.firstOrNull()?.jsonPrimitive?.content
                }
                else -> {
                    // Handle simple string case
                    title.jsonPrimitive.content
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extract icon URL from potentially complex icon field
     */
    fun getIconUrl(): String? {
        return extractUrlFromField(icon)
    }

    /**
     * Extract square icon URL from potentially complex square icon field
     */
    fun getSquareIconUrl(): String? {
        return extractUrlFromField(squareIcon)
    }

    /**
     * Extract logo URL from potentially complex logo field
     */
    fun getLogoUrl(): String? {
        return extractUrlFromField(logo)
    }

    /**
     * Helper method to extract URL from a field that can be either a string or an object
     */
    private fun extractUrlFromField(field: JsonElement?): String? {
        return try {
            val rawUrl = when {
                field == null -> null
                field is JsonObject -> {
                    val fieldObj = field as JsonObject
                    // Try url_format first, then url, then any field that looks like a URL
                    fieldObj["url_format"]?.jsonPrimitive?.content
                        ?: fieldObj["url"]?.jsonPrimitive?.content
                        ?: fieldObj.values.firstOrNull { 
                            try { 
                                val content = it.jsonPrimitive.content
                                content.startsWith("http") 
                            } catch (e: Exception) { false }
                        }?.jsonPrimitive?.content
                }
                else -> {
                    // Handle simple string case
                    field.jsonPrimitive.content
                }
            }
            
            // Process the URL to replace template variables
            rawUrl?.let { processGamesDbUrl(it) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Process GamesDB URLs to replace template variables like {formatter} and {ext}
     * Based on Heroic implementation pattern
     */
    private fun processGamesDbUrl(url: String, isBackground: Boolean = false): String {
        val format = if (isBackground) "webp" else "jpg"
        return url
            .replace("{formatter}", "") // Remove formatter placeholder
            .replace("{ext}", format) // Use appropriate format
    }
}

@Serializable
data class GamesDBScreenshot(
    val id: String? = null,
    val url: String? = null,
    val thumbnail: String? = null
)

@Serializable
data class GamesDBVideo(
    val id: String? = null,
    val name: String? = null,
    val url: String? = null,
    val thumbnail: String? = null
)

@Serializable
data class GamesDBImage(
    val id: String? = null,
    val url: String? = null,
    val type: String? = null
)

/**
 * Result wrapper for GamesDB API responses
 */
data class GamesDBResult(
    val isUpdated: Boolean,
    val data: GamesDBData? = null
)

/**
 * Cached GamesDB data with ETag for HTTP caching
 */
private data class CachedGamesDBData(
    val data: GamesDBData,
    val etag: String?
)

/**
 * Service for fetching enhanced game metadata from GamesDB API
 * Similar to the approach used in Heroic Games Launcher
 */
@Singleton
class GamesDBService @Inject constructor(
    private val httpClient: OkHttpClient
) {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    // Cache for API responses with ETags
    private val apiCache = ConcurrentHashMap<String, CachedGamesDBData>()
    
    /**
     * Fetch game metadata from GamesDB API
     * This endpoint doesn't require user authentication
     * 
     * @param store Indicates a store we have game_id from (e.g., "gog", "epic", "steam")
     * @param gameId ID of a game
     * @param forceUpdate Force data update check, ignoring cache
     * @return Result containing updated flag and game data
     */
    suspend fun getGamesdbData(
        store: String,
        gameId: String,
        forceUpdate: Boolean = false
    ): Result<GamesDBResult> = withContext(Dispatchers.IO) {
        try {
            val pieceId = "${store}_${gameId}"
            val cachedData = if (!forceUpdate) apiCache[pieceId] else null
            
            if (cachedData != null && !forceUpdate) {
                Timber.d("Using cached GamesDB data for $gameId")
                return@withContext Result.success(
                    GamesDBResult(isUpdated = false, data = cachedData.data)
                )
            }
            
            val url = "https://gamesdb.gog.com/platforms/$store/external_releases/$gameId"
            Timber.d("Fetching GamesDB data for $store game: $gameId")
            
            val requestBuilder = Request.Builder().url(url)
            
            // Add If-None-Match header if we have cached data with ETag
            cachedData?.etag?.let { etag ->
                requestBuilder.addHeader("If-None-Match", etag)
            }
            
            val response = httpClient.newCall(requestBuilder.build()).execute()
            
            response.use { resp ->
                when (resp.code) {
                    304 -> {
                        // Not Modified - use cached data
                        Timber.d("GamesDB data not modified for $gameId (304)")
                        Result.success(
                            GamesDBResult(isUpdated = false, data = cachedData?.data)
                        )
                    }
                    200 -> {
                        val responseBody = resp.body?.string()
                        if (responseBody.isNullOrEmpty()) {
                            Timber.w("Empty response from GamesDB for $gameId")
                            Result.success(GamesDBResult(isUpdated = false, data = null))
                        } else {
                            val responseEtag = resp.header("ETag")
                            
                            val data = json.decodeFromString<GamesDBData>(responseBody)
                            data.etag = responseEtag
                            
                            // Cache the response
                            apiCache[pieceId] = CachedGamesDBData(data, responseEtag)
                            
                            Timber.d("Successfully fetched GamesDB data for $gameId: ${data.getTitleString()}")
                            Result.success(
                                GamesDBResult(isUpdated = true, data = data)
                            )
                        }
                    }
                    404 -> {
                        Timber.d("Game $gameId not found in GamesDB")
                        Result.success(GamesDBResult(isUpdated = false, data = null))
                    }
                    else -> {
                        Timber.w("GamesDB API returned ${resp.code} for $gameId")
                        Result.failure(Exception("GamesDB API error: ${resp.code}"))
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse GamesDB response for $gameId")
            Result.failure(e)
        }
    }

    /**
     * Get the best available icon URL for a game from GamesDB data
     */
    fun getBestIconUrl(gamesDbData: GamesDBData): String {
        return gamesDbData.getSquareIconUrl()
            ?: gamesDbData.getIconUrl()
            ?: gamesDbData.getLogoUrl()
            ?: ""
    }

    /**
     * Get the best available background image URL for a game from GamesDB data
     */
    fun getBestBackgroundUrl(gamesDbData: GamesDBData): String? {
        val rawUrl = gamesDbData.backgroundImage
            ?: gamesDbData.backgroundImageAdditional
            ?: gamesDbData.horizontalArtwork
            ?: gamesDbData.verticalArtwork
            ?: gamesDbData.artworks?.firstOrNull { !it.url.isNullOrEmpty() }?.url
        
        return rawUrl?.let { processGamesDbUrlHelper(it, isBackground = true) }
    }

    /**
     * Helper method to process GamesDB URLs (accessible to service methods)
     */
    private fun processGamesDbUrlHelper(url: String, isBackground: Boolean = false): String {
        val format = if (isBackground) "webp" else "jpg"
        return url
            .replace("{formatter}", "") // Remove formatter placeholder
            .replace("{ext}", format) // Use appropriate format
    }

    /**
     * Clear cached data for a specific game
     */
    fun clearCache(store: String, gameId: String) {
        val pieceId = "${store}_${gameId}"
        apiCache.remove(pieceId)
        Timber.d("Cleared GamesDB cache for $pieceId")
    }
    
    /**
     * Clear all cached data
     */
    fun clearAllCache() {
        apiCache.clear()
        Timber.d("Cleared all GamesDB cache")
    }
    
    /**
     * Get cache size for debugging
     */
    fun getCacheSize(): Int = apiCache.size
}
