package app.gamenative.ui.data

import app.gamenative.enums.AppTheme
import app.gamenative.ui.screen.PluviaScreen
import app.gamenative.data.LibraryItem
import com.materialkolor.PaletteStyle

data class MainState(
    val appTheme: AppTheme = AppTheme.NIGHT,
    val paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    val resettedScreen: PluviaScreen? = null,
    val currentScreen: PluviaScreen = PluviaScreen.LoginUser,
    val hasLaunched: Boolean = false,
    val loadingDialogVisible: Boolean = false,
    val loadingDialogProgress: Float = 0F,
    val annoyingDialogShown: Boolean = false,
    val hasCrashedLastStart: Boolean = false,
    val isSteamConnected: Boolean = false,
    val launchedAppId: Int = 0,
    val launchedLibraryItem: LibraryItem? = null,
    val bootToContainer: Boolean = false,
    val showBootingSplash: Boolean = false,
)
