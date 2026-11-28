package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.download.EggikuCoolEgg
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/** Eggiku: what each settings page is set to, shown right on the main settings list */
internal object EggikuSettingsSummaries {

    @Composable
    fun appearance(): String {
        val prefs = remember { Injekt.get<UiPreferences>() }
        val theme by prefs.appTheme().collectAsState()
        val mode by prefs.themeMode().collectAsState()
        val name = theme.titleRes?.let { stringResource(it) } ?: theme.name
        return stringResource(KMR.strings.theme_picker_entry_summary, name, stringResource(themeModeLabel(mode)))
    }

    @Composable
    fun reader(): String {
        val prefs = remember { Injekt.get<ReaderPreferences>() }
        val idle by prefs.eggikuIdleCutoffMinutes().collectAsState()
        return if (idle > 0) {
            stringResource(KMR.strings.settings_reader_summary_idle, idle)
        } else {
            stringResource(MR.strings.pref_reader_summary)
        }
    }

    @Composable
    fun downloads(): String {
        val cool by remember { EggikuCoolEgg.enabled() }.collectAsState()
        return if (cool) {
            stringResource(KMR.strings.settings_downloads_summary_cool)
        } else {
            stringResource(MR.strings.pref_downloads_summary)
        }
    }

    @Composable
    fun backup(): String {
        val prefs = remember { Injekt.get<BackupPreferences>() }
        val last by prefs.lastAutoBackupTimestamp().collectAsState()
        return if (last > 0) {
            stringResource(KMR.strings.settings_backup_summary_last, relativeTimeSpanString(last))
        } else {
            stringResource(MR.strings.pref_backup_summary)
        }
    }

    @Composable
    fun privacy(): String {
        val prefs = remember { Injekt.get<SecurityPreferences>() }
        val lock by prefs.useAuthenticator().collectAsState()
        return if (lock) {
            stringResource(KMR.strings.settings_privacy_summary_locked)
        } else {
            stringResource(MR.strings.pref_security_summary)
        }
    }
}
// KMK <--
