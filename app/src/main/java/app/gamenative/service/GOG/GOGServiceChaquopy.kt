package app.gamenative.service.GOG

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import app.gamenative.data.GOGGame
import app.gamenative.data.GOGCredentials
import com.chaquo.python.Kwarg
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.*
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GOGServiceChaquopy @Inject constructor() : Service() {

    companion object {
        private var instance: GOGServiceChaquopy? = null
        private var appContext: Context? = null
        private var isInitialized = false
        private var python: Python? = null
        
        // Constants
        private const val GOG_CLIENT_ID = "46899977096215655"

        fun getInstance(): GOGServiceChaquopy? = instance

        /**
         * Initialize the GOG service with Chaquopy Python
         */
        fun initialize(context: Context): Boolean {
            if (isInitialized) return true

            try {
                // Store the application context
                appContext = context.applicationContext
                
                Timber.i("Initializing GOG service with Chaquopy...")
                
                // Initialize Python if not already started
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(context))
                }
                python = Python.getInstance()
                
                isInitialized = true
                Timber.i("GOG service initialized successfully with Chaquopy")
                
                return isInitialized
            } catch (e: Exception) {
                Timber.e(e, "Exception during GOG service initialization")
                return false
            }
        }

        /**
         * Execute GOGDL command using Chaquopy
         */
        suspend fun executeCommand(vararg args: String): Result<String> = withContext(Dispatchers.IO) {
            try {
                if (!isInitialized) {
                    return@withContext Result.failure(Exception("GOG service not initialized"))
                }

                val python = python ?: return@withContext Result.failure(Exception("Python not available"))

                Timber.d("Executing GOGDL command with ${args.size} arguments: ${args.joinToString(" ")}")
                if (args.isEmpty()) {
                    Timber.w("WARNING: GOGDL called with no arguments!")
                }

                try {
                    // First import Android compatibility patches
                    python.getModule("android_compat")
                    
                    // Import GOGDL modules
                    val gogdlArgs = python.getModule("gogdl.args")
                    val gogdlCli = python.getModule("gogdl.cli")
                    
                    // Create a temporary sys.argv for the command
                    val sys = python.getModule("sys")
                    val originalArgv = sys.get("argv")
                    
                    // Set up arguments for GOGDL (skip the program name for argparse)
                    val argsList = args.toList()
                    Timber.d("Setting GOGDL arguments for argparse: ${argsList.joinToString(" ")}")
                    
                    // Set sys.argv to include program name + our arguments
                    val fullArgsList = listOf("gogdl") + argsList
                    val newArgv = python.builtins.callAttr("list", fullArgsList.toTypedArray())
                    sys.put("argv", newArgv)
                    
                    // Debug: Print what sys.argv contains
                    val currentArgv = sys.get("argv")
                    Timber.d("sys.argv set to: $currentArgv")
                    
                    try {
                        // Add some Python debugging code to see what's happening
                        val builtins = python.getModule("builtins")
                        builtins.callAttr("print", "DEBUG: sys.argv before main: $currentArgv")
                        builtins.callAttr("print", "DEBUG: len(sys.argv): ${currentArgv?.callAttr("__len__")}")
                        
                        // Capture both stdout and stderr to get GOGDL output
                        val io = python.getModule("io")
                        val sys = python.getModule("sys")
                        val originalStdout = sys.get("stdout")
                        val originalStderr = sys.get("stderr")
                        val capturedStdout = io.callAttr("StringIO")
                        val capturedStderr = io.callAttr("StringIO")
                        sys.put("stdout", capturedStdout)
                        sys.put("stderr", capturedStderr)
                        
                        try {
                            // Call GOGDL main function
                            val result = gogdlCli.callAttr("main")
                            
                            // Get the captured output
                            val stdoutOutput = capturedStdout.callAttr("getvalue")?.toString() ?: ""
                            val stderrOutput = capturedStderr.callAttr("getvalue")?.toString() ?: ""
                            val combinedOutput = if (stdoutOutput.isNotBlank() && stderrOutput.isNotBlank()) {
                                "STDOUT: $stdoutOutput\nSTDERR: $stderrOutput"
                            } else if (stdoutOutput.isNotBlank()) {
                                stdoutOutput
                            } else if (stderrOutput.isNotBlank()) {
                                stderrOutput
                            } else {
                                ""
                            }
                            
                            Timber.d("GOGDL stdout: $stdoutOutput")
                            Timber.d("GOGDL stderr: $stderrOutput")
                            
                            // GOGDL typically prints to stdout, so we'll return success with output
                            Timber.i("GOGDL command executed successfully")
                            Result.success(combinedOutput)
                        } finally {
                            // Restore original stdout and stderr
                            sys.put("stdout", originalStdout)
                            sys.put("stderr", originalStderr)
                        }
                    } catch (e: Exception) {
                        // Handle SystemExit and other Python exceptions
                        val errorMessage = when {
                            e.message?.contains("SystemExit") == true -> {
                                // Extract exit code from SystemExit
                                val exitCode = try {
                                    e.message?.substringAfter("SystemExit: ")?.substringBefore(" ")?.toIntOrNull() ?: -1
                                } catch (ex: Exception) { -1 }
                                
                                when (exitCode) {
                                    0 -> {
                                        // Exit code 0 means success, don't treat as error
                                        Timber.i("GOGDL completed successfully with exit code 0")
                                        return@withContext Result.success("Command completed successfully")
                                    }
                                    2 -> "Invalid arguments provided to GOGDL. Please check the command parameters."
                                    1 -> "GOGDL execution failed. Please check your authentication and network connection."
                                    else -> "GOGDL exited with code $exitCode"
                                }
                            }
                            else -> e.message ?: "Unknown GOGDL error"
                        }
                        
                        Timber.w("GOGDL execution issue: $errorMessage")
                        throw Exception(errorMessage)
                    } finally {
                        // Restore original argv
                        sys.put("argv", originalArgv)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "GOGDL command failed")
                    Result.failure(e)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to execute GOGDL command")
                Result.failure(e)
            }
        }

        /**
         * Read and parse auth credentials from file
         */
        private fun readAuthCredentials(authConfigPath: String): Result<Pair<String, String>> {
            return try {
                val authFile = File(authConfigPath)
                Timber.d("Checking auth file at: ${authFile.absolutePath}")
                Timber.d("Auth file exists: ${authFile.exists()}")
                
                if (!authFile.exists()) {
                    return Result.failure(Exception("No authentication found. Please log in first."))
                }
                
                val authContent = authFile.readText()
                Timber.d("Auth file content: $authContent")
                
                val authJson = JSONObject(authContent)
                
                // GOGDL stores credentials nested under client ID
                val credentialsJson = if (authJson.has(GOG_CLIENT_ID)) {
                    authJson.getJSONObject(GOG_CLIENT_ID)
                } else {
                    // Fallback: try to read from root level
                    authJson
                }
                
                val accessToken = credentialsJson.optString("access_token", "")
                val userId = credentialsJson.optString("user_id", "")
                
                Timber.d("Parsed access_token: ${if (accessToken.isNotEmpty()) "${accessToken.take(20)}..." else "EMPTY"}")
                Timber.d("Parsed user_id: $userId")
                
                if (accessToken.isEmpty() || userId.isEmpty()) {
                    Timber.e("Auth data validation failed - accessToken empty: ${accessToken.isEmpty()}, userId empty: ${userId.isEmpty()}")
                    return Result.failure(Exception("Invalid authentication data. Please log in again."))
                }
                
                Result.success(Pair(accessToken, userId))
            } catch (e: Exception) {
                Timber.e(e, "Failed to read auth credentials")
                Result.failure(e)
            }
        }

        /**
         * Parse full GOGCredentials from auth file
         */
        private fun parseFullCredentials(authConfigPath: String): GOGCredentials {
            return try {
                val authFile = File(authConfigPath)
                if (authFile.exists()) {
                    val authContent = authFile.readText()
                    val authJson = JSONObject(authContent)
                    
                    // GOGDL stores credentials nested under client ID
                    val credentialsJson = if (authJson.has(GOG_CLIENT_ID)) {
                        authJson.getJSONObject(GOG_CLIENT_ID)
                    } else {
                        // Fallback: try to read from root level
                        authJson
                    }
                    
                    GOGCredentials(
                        accessToken = credentialsJson.optString("access_token", ""),
                        refreshToken = credentialsJson.optString("refresh_token", ""),
                        userId = credentialsJson.optString("user_id", ""),
                        username = credentialsJson.optString("username", "GOG User")
                    )
                } else {
                    // Return dummy credentials for successful auth
                    GOGCredentials(
                        accessToken = "authenticated_${System.currentTimeMillis()}",
                        refreshToken = "refresh_${System.currentTimeMillis()}",
                        userId = "user_123",
                        username = "GOG User"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse auth result")
                // Return dummy credentials as fallback
                GOGCredentials(
                    accessToken = "fallback_token",
                    refreshToken = "fallback_refresh",
                    userId = "fallback_user",
                    username = "GOG User"
                )
            }
        }

        /**
         * Create GOGCredentials from JSON output
         */
        private fun createCredentialsFromJson(outputJson: JSONObject): GOGCredentials {
            return GOGCredentials(
                accessToken = outputJson.optString("access_token", ""),
                refreshToken = outputJson.optString("refresh_token", ""),
                userId = outputJson.optString("user_id", ""),
                username = "GOG User" // We don't have username in the token response
            )
        }

        /**
         * Authenticate with GOG using authorization code
         */
        suspend fun authenticateWithCode(authConfigPath: String, authorizationCode: String): Result<GOGCredentials> {
            return try {
                Timber.i("Starting GOG authentication with authorization code...")
                
                // Extract the actual authorization code from URL if needed
                val actualCode = if (authorizationCode.startsWith("http")) {
                    // Extract code parameter from URL
                    val codeParam = authorizationCode.substringAfter("code=", "")
                    if (codeParam.isEmpty()) {
                        return Result.failure(Exception("Invalid authorization URL: no code parameter found"))
                    }
                    // Remove any additional parameters after the code
                    val cleanCode = codeParam.substringBefore("&")
                    Timber.d("Extracted authorization code from URL: ${cleanCode.take(20)}...")
                    cleanCode
                } else {
                    authorizationCode
                }
                
                // Create auth config directory
                val authFile = File(authConfigPath)
                val authDir = authFile.parentFile
                if (authDir != null && !authDir.exists()) {
                    authDir.mkdirs()
                    Timber.d("Created auth config directory: ${authDir.absolutePath}")
                }
                
                // Execute GOGDL auth command with the authorization code
                Timber.d("Authenticating with auth config path: $authConfigPath, code: ${actualCode.take(10)}...")
                Timber.d("Full auth command: --auth-config-path $authConfigPath auth --code ${actualCode.take(20)}...")
                
                val result = executeCommand("--auth-config-path", authConfigPath, "auth", "--code", actualCode)
                
                if (result.isSuccess) {
                    val gogdlOutput = result.getOrNull() ?: ""
                    Timber.i("GOGDL command completed, checking authentication result...")
                    Timber.d("GOGDL output for auth: $gogdlOutput")
                    
                    // First, check if GOGDL output indicates success
                    try {
                        val outputJson = JSONObject(gogdlOutput.trim())
                        
                        // Check if the response indicates an error
                        if (outputJson.has("error") && outputJson.getBoolean("error")) {
                            val errorMsg = outputJson.optString("error_description", "Authentication failed")
                            Timber.e("GOG authentication failed: $errorMsg")
                            return Result.failure(Exception("GOG authentication failed: $errorMsg"))
                        }
                        
                        // Check if we have the required fields for successful auth
                        val accessToken = outputJson.optString("access_token", "")
                        val userId = outputJson.optString("user_id", "")
                        
                        if (accessToken.isEmpty() || userId.isEmpty()) {
                            Timber.e("GOG authentication incomplete: missing access_token or user_id in output")
                            return Result.failure(Exception("Authentication incomplete: missing required data"))
                        }
                        
                        // GOGDL output looks good, now check if auth file was created
                        val authFile = File(authConfigPath)
                        if (authFile.exists()) {
                            // Parse authentication result from file
                            val authData = parseFullCredentials(authConfigPath)
                            Timber.i("GOG authentication successful for user: ${authData.username}")
                            Result.success(authData)
                        } else {
                            Timber.w("GOGDL returned success but no auth file created, using output data")
                            // Create credentials from GOGDL output
                            val credentials = createCredentialsFromJson(outputJson)
                            Result.success(credentials)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to parse GOGDL output")
                        // Fallback: check if auth file exists
                        val authFile = File(authConfigPath)
                        if (authFile.exists()) {
                            try {
                                val authData = parseFullCredentials(authConfigPath)
                                Timber.i("GOG authentication successful (fallback) for user: ${authData.username}")
                                Result.success(authData)
                            } catch (ex: Exception) {
                                Timber.e(ex, "Failed to parse auth file")
                                Result.failure(Exception("Failed to parse authentication result: ${ex.message}"))
                            }
                        } else {
                            Timber.e("GOG authentication failed: no auth file created and failed to parse output")
                            Result.failure(Exception("Authentication failed: no credentials available"))
                        }
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Authentication failed"
                    Timber.e("GOG authentication command failed: $error")
                    Result.failure(Exception(error))
                }
            } catch (e: Exception) {
                Timber.e(e, "GOG authentication exception")
                Result.failure(e)
            }
        }

        /**
         * Authenticate with GOG (legacy method for compatibility)
         */
        suspend fun authenticate(authConfigPath: String): Result<GOGCredentials> {
            // This method should not be called directly anymore
            // The UI should use the OAuth flow and then call authenticateWithCode
            return Result.failure(Exception("Use OAuth flow instead. This method is deprecated."))
        }

        /**
         * Get GOG library
         */
        suspend fun getLibrary(authConfigPath: String): Result<List<GOGGame>> {
            return try {
                Timber.i("Getting GOG library...")
                
                // Read auth credentials using extracted function
                val credentialsResult = readAuthCredentials(authConfigPath)
                if (credentialsResult.isFailure) {
                    return Result.failure(credentialsResult.exceptionOrNull()!!)
                }
                
                val (accessToken, userId) = credentialsResult.getOrThrow()
                
                // Use Python requests to call GOG Galaxy API
                val python = Python.getInstance()
                val requests = python.getModule("requests")
                
                val url = "https://embed.gog.com/user/data/games"
                
                // Convert Kotlin Map to Python dictionary to avoid LinkedHashMap issues
                val pyDict = python.builtins.callAttr("dict")
                pyDict.callAttr("__setitem__", "Authorization", "Bearer $accessToken")
                pyDict.callAttr("__setitem__", "User-Agent", "GOGGalaxyClient/2.0.45.61 (Windows_x86_64)")
                
                Timber.d("Making GOG API request to: $url")
                Timber.d("Request headers: Authorization=Bearer ${accessToken.take(20)}..., User-Agent=GOGGalaxyClient/2.0.45.61")
                
                // Make the request with headers - pass as separate arguments
                val response = requests.callAttr("get", url, 
                    Kwarg("headers", pyDict),
                    Kwarg("timeout", 30))
                
                val statusCode = response.get("status_code")?.toInt() ?: 0
                Timber.d("GOG API response status: $statusCode")
                
                if (statusCode == 200) {
                    val responseJson = response.callAttr("json")
                    Timber.d("GOG API response JSON: $responseJson")
                    
                    // Try different ways to access the owned array
                    val ownedGames = try {
                        responseJson?.callAttr("get", "owned")
                    } catch (e: Exception) {
                        Timber.w("Failed to get owned with callAttr: ${e.message}")
                        try {
                            responseJson?.get("owned")
                        } catch (e2: Exception) {
                            Timber.w("Failed to get owned with get: ${e2.message}")
                            null
                        }
                    }
                    
                    Timber.d("GOG API owned games: $ownedGames")
                    
                    // Count the owned game IDs
                    val gameCount = ownedGames?.callAttr("__len__")?.toInt() ?: 0
                    Timber.i("GOG library retrieved: $gameCount game IDs found")
                    
                    // Convert Python list to Kotlin list of game IDs
                    val gameIds = mutableListOf<String>()
                    if (ownedGames != null && gameCount > 0) {
                        for (i in 0 until gameCount) {
                            try {
                                val gameId = ownedGames.callAttr("__getitem__", i)?.toString()
                                if (gameId != null) {
                                    gameIds.add(gameId)
                                }
                            } catch (e: Exception) {
                                Timber.w("Failed to get game ID at index $i: ${e.message}")
                            }
                        }
                    }
                    
                    Timber.i("Converted ${gameIds.size} game IDs")
                    
                    // Fetch game details for the first few games (limit to avoid overwhelming the API)
                    val maxGamesToFetch = 10 // Start with just 10 games for testing
                    val games = mutableListOf<GOGGame>()
                    
                    gameIds.take(maxGamesToFetch).forEach { gameId ->
                        try {
                            val gameDetails = fetchGameDetails(gameId, accessToken)
                            if (gameDetails != null) {
                                games.add(gameDetails)
                            }
                        } catch (e: Exception) {
                            Timber.w("Failed to fetch details for game $gameId: ${e.message}")
                        }
                    }
                    
                    Timber.i("Successfully fetched details for ${games.size} games")
                    Result.success(games)
                } else {
                    val errorText = response.callAttr("text")?.toString() ?: "Unknown error"
                    Timber.e("GOG API error: HTTP $statusCode - $errorText")
                    Result.failure(Exception("Failed to get library: HTTP $statusCode"))
                }
            } catch (e: Exception) {
                Timber.e(e, "GOG library exception")
                Result.failure(e)
            }
        }

        /**
         * Fetch detailed information for a specific GOG game
         */
        private suspend fun fetchGameDetails(gameId: String, accessToken: String): GOGGame? = withContext(Dispatchers.IO) {
            try {
                val python = Python.getInstance()
                val requests = python.getModule("requests")
                
                // Use the GOG API products endpoint to get game details
                val url = "https://api.gog.com/products/$gameId"
                
                // Create headers dictionary
                val pyDict = python.builtins.callAttr("dict")
                pyDict.callAttr("__setitem__", "Authorization", "Bearer $accessToken")
                pyDict.callAttr("__setitem__", "User-Agent", "GOGGalaxyClient/2.0.45.61 (Windows_x86_64)")
                
                Timber.d("Fetching GOG game details for ID: $gameId")
                
                val response = requests.callAttr("get", url, 
                    Kwarg("headers", pyDict),
                    Kwarg("timeout", 10))
                
                val statusCode = response.get("status_code")?.toInt() ?: 0
                
                if (statusCode == 200) {
                    val gameJson = response.callAttr("json")
                    
                    // Extract game information
                    val title = gameJson?.callAttr("get", "title")?.toString() ?: "Unknown Game"
                    val slug = gameJson?.callAttr("get", "slug")?.toString() ?: gameId
                    
                    // Get description - it might be nested
                    val description = try {
                        gameJson?.callAttr("get", "description")?.callAttr("get", "full")?.toString() 
                            ?: gameJson?.callAttr("get", "description")?.toString()
                            ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                    
                    // Get image URL
                    val imageUrl = try {
                        val images = gameJson?.callAttr("get", "images")
                        val logo = images?.callAttr("get", "logo")
                        logo?.toString() ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                    
                    // Get developer and publisher
                    val developer = try {
                        val developers = gameJson?.callAttr("get", "developers")
                        if (developers != null) {
                            val firstDev = developers.callAttr("__getitem__", 0)
                            firstDev?.toString() ?: ""
                        } else ""
                    } catch (e: Exception) {
                        ""
                    }
                    
                    val publisher = try {
                        val publishers = gameJson?.callAttr("get", "publishers")
                        if (publishers != null) {
                            val firstPub = publishers.callAttr("__getitem__", 0)
                            firstPub?.toString() ?: ""
                        } else ""
                    } catch (e: Exception) {
                        ""
                    }
                    
                    // Get release date
                    val releaseDate = try {
                        gameJson?.callAttr("get", "release_date")?.toString() ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                    
                    Timber.d("Successfully fetched details for game: $title")
                    
                    GOGGame(
                        id = gameId,
                        title = title,
                        slug = slug,
                        description = description,
                        imageUrl = imageUrl,
                        developer = developer,
                        publisher = publisher,
                        releaseDate = releaseDate
                    )
                } else {
                    Timber.w("Failed to fetch game details for $gameId: HTTP $statusCode")
                    null
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception fetching game details for $gameId")
                null
            }
        }

        /**
         * Download a GOG game
         */
        suspend fun downloadGame(gameId: String, installPath: String, authConfigPath: String): Result<Unit> {
            return try {
                val result = executeCommand("--auth-config-path", authConfigPath, "download", 
                                          "--install-path", installPath, gameId)
                if (result.isSuccess) {
                    Result.success(Unit)
                } else {
                    Result.failure(result.exceptionOrNull() ?: Exception("Download failed"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        /**
         * Get game info including download size
         */
        suspend fun getGameInfo(gameId: String, authConfigPath: String): Result<GOGGame> {
            return try {
                val result = executeCommand("--auth-config-path", authConfigPath, "info", gameId)
                if (result.isSuccess) {
                    // For now, return a dummy game - would need to parse actual output
                    val gameInfo = GOGGame(
                        id = gameId,
                        title = "GOG Game",
                        slug = gameId
                    )
                    Result.success(gameInfo)
                } else {
                    Result.failure(result.exceptionOrNull() ?: Exception("Failed to get game info"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        /**
         * Launch a GOG game
         */
        suspend fun launchGame(installPath: String, authConfigPath: String): Result<Unit> {
            return try {
                val result = executeCommand("--auth-config-path", authConfigPath, "launch", installPath)
                if (result.isSuccess) {
                    Result.success(Unit)
                } else {
                    Result.failure(result.exceptionOrNull() ?: Exception("Launch failed"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        // Parsing helper functions - simplified since extraction functions handle the logic
        private fun parseAuthResult(authConfigPath: String): GOGCredentials {
            return parseFullCredentials(authConfigPath)
        }

        fun hasStoredCredentials(context: Context): Boolean {
            val authFile = File(context.filesDir, "gog_auth.json")
            return authFile.exists()
        }

        fun getStoredCredentials(context: Context): Result<GOGCredentials> {
            return try {
                val authConfigPath = "${context.filesDir}/gog_auth.json"
                val credentials = parseFullCredentials(authConfigPath)
                Result.success(credentials)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load stored credentials")
                Result.failure(e)
            }
        }

        fun clearStoredCredentials(context: Context): Boolean {
            return try {
                val authFile = File(context.filesDir, "gog_auth.json")
                if (authFile.exists()) {
                    authFile.delete()
                } else {
                    true
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear GOG credentials")
                false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
