package app.gamenative

import android.os.StrictMode
import androidx.navigation.NavController
import app.gamenative.events.EventDispatcher
import app.gamenative.service.GOG.GOGServiceChaquopy
import app.gamenative.utils.IntentLaunchManager
import com.google.android.play.core.splitcompat.SplitCompatApplication
import com.winlator.inputcontrols.InputControlsManager
import com.winlator.widget.InputControlsView
import com.winlator.widget.TouchpadView
import com.winlator.widget.XServerView
import com.winlator.xenvironment.XEnvironment
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import okhttp3.OkHttpClient
import javax.inject.Inject

// Add PostHog imports
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

typealias NavChangedListener = NavController.OnDestinationChangedListener

@HiltAndroidApp
class PluviaApp : SplitCompatApplication() {

    @Inject
    lateinit var httpClient: OkHttpClient

    override fun onCreate() {
        super.onCreate()

        // Allows to find resource streams not closed within GameNative and JavaSteam
        if (BuildConfig.DEBUG) {
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build(),
            )

            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }

        // Init our custom crash handler.
        CrashHandler.initialize(this)

        // Init our datastore preferences.
        PrefManager.init(this)

        // Clear any stale temporary config overrides from previous app sessions
        try {
            IntentLaunchManager.clearAllTemporaryOverrides()
            Timber.d("[PluviaApp]: Cleared temporary config overrides from previous session")
        } catch (e: Exception) {
            Timber.e(e, "[PluviaApp]: Failed to clear temporary config overrides")
        }

        // Initialize PostHog Analytics
        val postHogConfig = PostHogAndroidConfig(
            apiKey = BuildConfig.POSTHOG_API_KEY,
            host = BuildConfig.POSTHOG_HOST,
        )
        PostHogAndroid.setup(this, postHogConfig)

        // Initialize GOG Service
        try {
            GOGServiceChaquopy.initialize(this)
            GOGServiceChaquopy.setHttpClient(httpClient)
            Timber.i("[PluviaApp]: GOG Service initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "[PluviaApp]: Failed to initialize GOG Service")
        }
    }

    companion object {
        internal val events: EventDispatcher = EventDispatcher()
        internal var onDestinationChangedListener: NavChangedListener? = null

        // TODO: find a way to make this saveable, this is terrible (leak that memory baby)
        internal var xEnvironment: XEnvironment? = null
        internal var xServerView: XServerView? = null
        var inputControlsView: InputControlsView? = null
        var inputControlsManager: InputControlsManager? = null
        var touchpadView: TouchpadView? = null
    }
}
