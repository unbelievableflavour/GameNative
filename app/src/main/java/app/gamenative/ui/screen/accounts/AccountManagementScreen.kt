package app.gamenative.ui.screen.accounts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import java.io.File
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.hilt.navigation.compose.hiltViewModel
import app.gamenative.R
import app.gamenative.service.GOG.GOGServiceChaquopy
import app.gamenative.service.SteamService
import app.gamenative.ui.model.AccountManagementViewModel
import app.gamenative.ui.screen.PluviaScreen
import app.gamenative.ui.screen.auth.GOGOAuthActivity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManagementScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: AccountManagementViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // State for Steam
    val isSteamLoggedIn = SteamService.isLoggedIn
    val hasSteamCredentials = SteamService.hasStoredCredentials()
    
    // State for GOG
    var isGOGLoggedIn by remember { mutableStateOf(false) }
    var gogUsername by remember { mutableStateOf("") }
    var gogAuthInProgress by remember { mutableStateOf(false) }
    var gogError by remember { mutableStateOf<String?>(null) }
    var gogLibraryTestInProgress by remember { mutableStateOf(false) }
    var gogLibraryTestResult by remember { mutableStateOf<String?>(null) }
    
    // Check for existing GOG credentials on startup
    LaunchedEffect(Unit) {
        if (GOGServiceChaquopy.hasStoredCredentials(context)) {
            val credentialsResult = GOGServiceChaquopy.getStoredCredentials(context)
            if (credentialsResult.isSuccess) {
                val credentials = credentialsResult.getOrThrow()
                isGOGLoggedIn = true
                gogUsername = credentials.username
                gogError = null
            } else {
                gogError = "Failed to load stored credentials: ${credentialsResult.exceptionOrNull()?.message}"
            }
        }
    }
    
    // OAuth launcher for GOG authentication
    val gogOAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            android.app.Activity.RESULT_OK -> {
                val authCode = result.data?.getStringExtra(GOGOAuthActivity.EXTRA_AUTH_CODE)
                if (authCode != null) {
                    // Got authorization code, now authenticate with GOGDL
                    scope.launch {
                        gogAuthInProgress = true
                        gogError = null
                        
                        try {
                            val authConfigPath = "${context.filesDir}/gog_auth.json"
                            val authResult = GOGServiceChaquopy.authenticateWithCode(authConfigPath, authCode)
                            
                            if (authResult.isSuccess) {
                                val credentials = authResult.getOrThrow()
                                isGOGLoggedIn = true
                                gogUsername = credentials.username
                                gogError = null
                            } else {
                                gogError = authResult.exceptionOrNull()?.message ?: "Authentication failed"
                            }
                        } catch (e: Exception) {
                            gogError = e.message ?: "Authentication failed"
                        } finally {
                            gogAuthInProgress = false
                        }
                    }
                } else {
                    gogError = "No authorization code received"
                    gogAuthInProgress = false
                }
            }
            android.app.Activity.RESULT_CANCELED -> {
                val error = result.data?.getStringExtra(GOGOAuthActivity.EXTRA_ERROR)
                gogError = error ?: "Authentication cancelled"
                gogAuthInProgress = false
            }
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = "Account Management",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = "Manage your gaming platform accounts",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Divider()
        
        // Steam Account Section
        AccountSection(
            title = "Steam",
            description = "Access your Steam library and games",
            icon = Icons.Default.Games,
            isLoggedIn = isSteamLoggedIn,
            username = if (isSteamLoggedIn) "Steam User" else null,
            onLogin = {
                navController.navigate(PluviaScreen.LoginUser.route)
            },
            onLogout = {
                scope.launch {
                    SteamService.logOut()
                }
            }
        )
        
        Divider()
        
        // GOG Account Section
        AccountSection(
            title = "GOG",
            description = "Access your GOG library and DRM-free games",
            icon = Icons.Default.LibraryBooks,
            isLoggedIn = isGOGLoggedIn,
            username = if (isGOGLoggedIn) gogUsername else null,
            isLoading = gogAuthInProgress,
            error = gogError,
            onLogin = {
                // Launch GOG OAuth activity
                gogAuthInProgress = true
                gogError = null
                val intent = Intent(context, GOGOAuthActivity::class.java)
                gogOAuthLauncher.launch(intent)
            },
            onLogout = {
                scope.launch {
                    try {
                        // Clear stored credentials using the service method
                        GOGServiceChaquopy.clearStoredCredentials(context)
                        
                        isGOGLoggedIn = false
                        gogUsername = ""
                        gogError = null
                        gogLibraryTestResult = null
                    } catch (e: Exception) {
                        gogError = "Logout error: ${e.message}"
                    }
                }
            }
        )
        
        // GOG Library Test Section (only show when logged in)
        if (isGOGLoggedIn) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Sync GOG Library",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = "Sync your GOG games to show them in the main library",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Button(
                        onClick = {
                            scope.launch {
                                gogLibraryTestInProgress = true
                                gogLibraryTestResult = null
                                
                                viewModel.syncGOGLibraryAsync(context, clearExisting = true) { result ->
                                    if (result.isSuccess) {
                                        gogLibraryTestResult = "Background sync started! Games will appear in the library progressively. Check the main library screen to see them loading."
                                    } else {
                                        gogLibraryTestResult = "Failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
                                    }
                                    gogLibraryTestInProgress = false
                                }
                            }
                        },
                        enabled = !gogLibraryTestInProgress,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (gogLibraryTestInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (gogLibraryTestInProgress) "Starting..." else "Clear & Sync Library")
                    }
                    
                    // Show test result
                    gogLibraryTestResult?.let { result ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (result.startsWith("Success")) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = result,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
        
        Divider()
        
        // Info Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "About Accounts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Text(
                    text = "• You can use GameNative without logging into any accounts\n" +
                          "• Steam login enables downloading and playing Steam games\n" +
                          "• GOG login enables accessing your DRM-free GOG library\n" +
                          "• Both accounts can be used simultaneously",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back to Library")
            }
        }
    }
}

@Composable
private fun AccountSection(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isLoggedIn: Boolean,
    username: String?,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    error: String? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isLoggedIn) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = if (isLoggedIn) 
                        MaterialTheme.colorScheme.onPrimaryContainer 
                    else 
                        MaterialTheme.colorScheme.onSurface
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isLoggedIn) 
                            MaterialTheme.colorScheme.onPrimaryContainer 
                        else 
                            MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = if (isLoggedIn && username != null) 
                            "Logged in as $username" 
                        else 
                            description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isLoggedIn) 
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Status indicator
                Icon(
                    imageVector = if (isLoggedIn) Icons.Default.CheckCircle else Icons.Default.Circle,
                    contentDescription = if (isLoggedIn) "Connected" else "Not connected",
                    tint = if (isLoggedIn) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            // Error message
            if (error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            
            // Action button
            if (isLoggedIn) {
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Logout, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out")
                }
            } else {
                Button(
                    onClick = onLogin,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Login, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isLoading) "Signing In..." else "Sign In")
                }
            }
        }
    }
}
