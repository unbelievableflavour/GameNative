package app.gamenative.ui.screen.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.gamenative.ui.theme.PluviaTheme
import timber.log.Timber

class GOGOAuthActivity : ComponentActivity() {
    
    companion object {
        const val EXTRA_AUTH_CODE = "auth_code"
        const val EXTRA_ERROR = "error"
        
        // GOG OAuth URLs (same as Heroic Games Launcher)
        const val GOG_CLIENT_ID = "46899977096215655"
        const val GOG_REDIRECT_URI = "https://embed.gog.com/on_login_success?origin=client"
        const val GOG_AUTH_URL = "https://auth.gog.com/auth?" +
                "client_id=$GOG_CLIENT_ID" +
                "&redirect_uri=https%3A%2F%2Fembed.gog.com%2Fon_login_success%3Forigin%3Dclient" +
                "&response_type=code" +
                "&layout=galaxy"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        

        
        setContent {
            PluviaTheme {
                GOGOAuthScreen(
                    onAuthComplete = { authCode ->
                        val resultIntent = Intent().apply {
                            putExtra(EXTRA_AUTH_CODE, authCode)
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    },
                    onError = { error ->
                        val resultIntent = Intent().apply {
                            putExtra(EXTRA_ERROR, error)
                        }
                        setResult(Activity.RESULT_CANCELED, resultIntent)
                        finish()
                    },
                    onCancel = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }
    

    
    private fun extractAuthCode(url: String): String? {
        return try {
            val uri = Uri.parse(url)
            uri.getQueryParameter("code")
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract auth code from URL: $url")
            null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GOGOAuthScreen(
    onAuthComplete: (String) -> Unit,
    onError: (String) -> Unit,
    onCancel: () -> Unit
) {
    var authCode by remember { mutableStateOf("") }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val authCodeFocusRequester = remember { FocusRequester() }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GOG Login") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        val scrollState = rememberScrollState()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .imePadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Connect your GOG account",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Log in to access your GOG library",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Primary login button
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GOGOAuthActivity.GOG_AUTH_URL))
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Log in with GOG")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Authorization code input field (always visible)
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Authorization Code",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = authCode,
                    onValueChange = { newValue -> 
                        authCode = newValue
                        Timber.d("Auth code changed: $newValue")
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(authCodeFocusRequester),
                                                placeholder = {
                                Text(
                                    "Paste the full URL or just the code",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            if (authCode.isNotBlank()) {
                                onAuthComplete(authCode.trim())
                            }
                        }
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Continue button
            Button(
                onClick = {
                    if (authCode.isNotBlank()) {
                        onAuthComplete(authCode.trim())
                    }
                },
                enabled = authCode.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Instructions
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "How it works:",
                        style = MaterialTheme.typography.titleSmall
                    )
                                                Text(
                                text = "1. Click 'Log in with GOG' above\n" +
                                      "2. Complete login in your browser (refresh if blank)\n" +
                                      "3. Copy the full URL from the final page (or just the code part)\n" +
                                      "4. Paste it above and click Continue",
                                style = MaterialTheme.typography.bodyMedium
                            )
                }
            }
        }
    }

}



private fun extractAuthCode(url: String): String? {
    return try {
        val uri = Uri.parse(url)
        uri.getQueryParameter("code")
    } catch (e: Exception) {
        Timber.e(e, "Failed to extract auth code from URL: $url")
        null
    }
}
