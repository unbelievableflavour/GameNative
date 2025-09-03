package app.gamenative.ui.model

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gamenative.service.GOG.GOGLibraryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AccountManagementViewModel @Inject constructor(
    private val gogLibraryManager: GOGLibraryManager
) : ViewModel() {

    suspend fun syncGOGLibrary(context: Context): Result<Int> {
        return try {
            val syncResult = gogLibraryManager.syncLibrary(context)
            if (syncResult.isSuccess) {
                val gameCount = gogLibraryManager.getLocalGameCount()
                Timber.i("GOG library synced: $gameCount games")
                Result.success(gameCount)
            } else {
                Timber.e(syncResult.exceptionOrNull(), "Failed to sync GOG library")
                Result.failure(syncResult.exceptionOrNull() ?: Exception("Unknown sync error"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception during GOG sync")
            Result.failure(e)
        }
    }

    fun syncGOGLibraryAsync(context: Context, clearExisting: Boolean = true, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            // Clear existing games and start background sync
            if (clearExisting) {
                gogLibraryManager.clearLibrary()
            }
            gogLibraryManager.startBackgroundSync(context, clearExisting)
            
            // For UI feedback, return success immediately since sync runs in background
            val gameCount = gogLibraryManager.getLocalGameCount()
            onResult(Result.success(gameCount))
        }
    }
}
