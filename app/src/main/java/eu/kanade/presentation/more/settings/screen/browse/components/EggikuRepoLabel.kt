package eu.kanade.presentation.more.settings.screen.browse.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

/** Eggiku: a label you give each extension repo, so you can tell what it's for at a glance */
enum class EggikuRepoLabel(val title: StringResource) {
    MANGA(KMR.strings.repo_label_manga),
    COMICS(KMR.strings.repo_label_comics),
    SELF_HOSTED(KMR.strings.repo_label_self_hosted),
    OTHER(KMR.strings.repo_label_other),
    ;

    companion object {
        /** Entries look like "indexUrl\tLABEL" */
        fun preference() = Injekt.get<PreferenceStore>().getStringSet("eggiku_repo_labels", emptySet())

        fun parse(entries: Set<String>): Map<String, EggikuRepoLabel> = entries.mapNotNull { entry ->
            val url = entry.substringBefore('\t')
            val label = entries(entry.substringAfter('\t')) ?: return@mapNotNull null
            url to label
        }.toMap()

        private fun entries(name: String) = EggikuRepoLabel.entries.firstOrNull { it.name == name }

        /** Unlabelled → Manga → Comics → Self-hosted → Other → unlabelled */
        fun cycle(indexUrl: String) {
            val pref = preference()
            val labels = parse(pref.get()).toMutableMap()
            val next = when (val current = labels[indexUrl]) {
                null -> EggikuRepoLabel.entries.first()
                EggikuRepoLabel.entries.last() -> null
                else -> EggikuRepoLabel.entries[current.ordinal + 1]
            }
            if (next == null) labels.remove(indexUrl) else labels[indexUrl] = next
            pref.set(labels.map { (url, label) -> "$url\t${label.name}" }.toSet())
        }
    }
}

@Composable
fun EggikuRepoLabelChip(indexUrl: String) {
    val entries by EggikuRepoLabel.preference().collectAsState()
    val label = EggikuRepoLabel.parse(entries)[indexUrl]
    SuggestionChip(
        onClick = { EggikuRepoLabel.cycle(indexUrl) },
        label = { Text(stringResource(label?.title ?: KMR.strings.repo_label_add)) },
        modifier = Modifier.padding(start = 8.dp),
    )
}
// KMK <--
