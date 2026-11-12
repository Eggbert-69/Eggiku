package eu.kanade.tachiyomi.ui.more

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.more.onboarding.OnboardingScreen
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.presentation.more.settings.screen.data.RestoreBackupScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class OnboardingScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val basePreferences = remember { Injekt.get<BasePreferences>() }
        val shownOnboardingFlow by basePreferences.shownOnboardingFlow().collectAsState()

        val finishOnboarding: () -> Unit = {
            basePreferences.shownOnboardingFlow().set(true)
            navigator.pop()
        }

        val restoreSettingKey = stringResource(SettingsDataScreen.restorePreferenceKeyString)

        // KMK -->
        val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            finishOnboarding()
            navigator.push(RestoreBackupScreen(uri.toString()))
        }
        // KMK <--

        BackHandler(enabled = !shownOnboardingFlow) {
            // Prevent exiting if onboarding hasn't been completed
        }

        OnboardingScreen(
            onComplete = finishOnboarding,
            onRestoreBackup = {
                // KMK -->
                // Eggiku: open the file picker straight away; Google Drive shows up there,
                // so a new phone can be restored from the Drive copy in one step
                try {
                    pickBackup.launch(arrayOf("*/*"))
                } catch (_: Exception) {
                    finishOnboarding()
                    SearchableSettings.highlightKey = restoreSettingKey
                    navigator.push(SettingsScreen(SettingsScreen.Destination.DataAndStorage))
                }
                // KMK <--
            },
        )
    }
}
