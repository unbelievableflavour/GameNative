package app.gamenative.utils.launchdependencies

import android.content.Context
import app.gamenative.data.GameSource
import app.gamenative.utils.LaunchSteps
import com.winlator.container.Container
import java.io.File
import timber.log.Timber

/**
 * Installs NVIDIA PhysX redistributables from the game's _CommonRedist\PhysX and redist/Redist folders
 * by contributing msiexec commands to the main Wine cmd /c chain.
 */
object PhysXLaunchStep : LaunchStep {

    override val runOnce: Boolean = true

    override fun appliesTo(container: Container, appId: String, gameSource: GameSource): Boolean = true

    override fun run(
        context: Context,
        appId: String,
        container: Container,
        stepRunner: StepRunner,
        gameSource: GameSource,
    ): Boolean {
        return try {
            val gameDir = getGameDir(container) ?: return false
            val gameDirPath = gameDir.absolutePath

            val searchDirs = listOf(
                File(gameDirPath, "_CommonRedist/PhysX"),
                File(gameDirPath, "redist"),
                File(gameDirPath, "Redist"),
            ).filter { it.exists() && it.isDirectory }

            if (searchDirs.isEmpty()) {
                Timber.tag("PhysXLaunchStep").i("No PhysX search directories found for game at $gameDirPath")
                return false
            }

            val parts = mutableListOf<String>()

            for (dir in searchDirs) {
                Timber.tag("PhysXLaunchStep").i("Searching for PhysX installers under ${dir.absolutePath}")
                dir.walkTopDown()
                    .filter { file ->
                        file.isFile &&
                            file.name.startsWith("PhysX", ignoreCase = true) &&
                            (file.name.endsWith(".msi", ignoreCase = true) ||
                                file.name.endsWith(".exe", ignoreCase = true))
                    }
                    .forEach { installerFile ->
                        val relativePath = installerFile
                            .relativeTo(File(gameDirPath))
                            .path
                            .replace('/', '\\')
                        val winePath = "A:\\$relativePath"
                        Timber.tag("PhysXLaunchStep").i("Queued PhysX installer: $winePath")

                        val command =
                            if (installerFile.name.endsWith(".msi", ignoreCase = true)) {
                                "msiexec /i $winePath /quiet /norestart"
                            } else {
                                "$winePath /quiet /norestart"
                            }
                        parts.add(command)
                    }
            }

            val content = if (parts.isEmpty()) null else parts.joinToString(" & ")
            if (content.isNullOrBlank()) return false
            val wrapped = LaunchSteps.wrapInWinHandler(content)
            stepRunner.runStepContent(wrapped)
            return true
        } catch (e: Exception) {
            Timber.tag("PhysXLaunchStep").e(e, "Error preparing PhysX launch step")
            false
        }
    }
}
