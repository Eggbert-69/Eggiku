package eu.kanade.tachiyomi.data.achievements

import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Egg
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.presentation.more.settings.screen.EggikuHiddenThemes
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate

// KMK -->
/**
 * Eggiku egg hunt: six golden eggs hidden around the app. Each one is a faint little egg (or, in
 * the reader, a lucky one at the end of a chapter). Finding 3 unlocks a golden egg to hold, 5 a
 * basket hat, and all 6 the Easter Basket theme plus a golden egg background for your avatar.
 */
object EggikuEggHunt {

    enum class Egg(val hint: StringResource) {
        ABOUT(KMR.strings.egg_hunt_hint_about),
        STATS(KMR.strings.egg_hunt_hint_stats),
        DROPS(KMR.strings.egg_hunt_hint_drops),
        REPOS(KMR.strings.egg_hunt_hint_repos),
        GROUPS(KMR.strings.egg_hunt_hint_groups),
        CHAPTER_END(KMR.strings.egg_hunt_hint_chapter_end),
    }

    const val BADGE_THREE = "golden_three"
    const val BADGE_FIVE = "egg_basket"
    const val BADGE_ALL = "golden_basket"

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    fun foundPref() = preferenceStore.getStringSet("eggiku_egg_hunt_found", emptySet())

    fun found(): Set<Egg> = foundPref().get().mapNotNull { name -> Egg.entries.firstOrNull { it.name == name } }.toSet()

    fun isFound(egg: Egg) = egg.name in foundPref().get()

    /** Chapter-end eggs turn up now and then */
    fun luckyChapterEnd(chapterId: Long): Boolean =
        !isFound(Egg.CHAPTER_END) && ((chapterId * 2654435761L) ushr 8) % 12 == 0L

    fun find(context: Context, egg: Egg) {
        if (isFound(egg)) return
        foundPref().set(foundPref().get() + egg.name)
        val count = found().size
        val total = Egg.entries.size
        // Badges go straight in, so the avatar items unlock right away
        val today = LocalDate.now().toEpochDay()
        val earned = EggikuAchievements.earned().keys
        val newBadges = listOfNotNull(
            BADGE_THREE.takeIf { count >= 3 },
            BADGE_FIVE.takeIf { count >= 5 },
            BADGE_ALL.takeIf { count >= total },
        ).map { "O:$it" }.filter { it !in earned }
        if (newBadges.isNotEmpty()) {
            EggikuAchievements.earnedPref().set(EggikuAchievements.earnedPref().get() + newBadges.map { "$it:$today" })
        }
        val message = when {
            count >= total -> context.stringResource(KMR.strings.egg_hunt_all_found)
            count == 5 -> context.stringResource(KMR.strings.egg_hunt_five)
            count == 3 -> context.stringResource(KMR.strings.egg_hunt_three)
            else -> context.stringResource(KMR.strings.egg_hunt_found, count, total)
        }
        context.toast(message, android.widget.Toast.LENGTH_LONG)
        if (count >= total) EggikuHiddenThemes.grant(AppTheme.EASTER_BASKET)
    }
}

/** Egg hunt progress with a riddle for each egg still hidden */
@Composable
fun EggikuEggHuntCard(modifier: Modifier = Modifier) {
    val foundRaw by EggikuEggHunt.foundPref().collectAsState()
    val total = EggikuEggHunt.Egg.entries.size
    val found = EggikuEggHunt.Egg.entries.count { it.name in foundRaw }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "🥚 " + stringResource(KMR.strings.egg_hunt_title, found, total),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(if (found >= total) KMR.strings.egg_hunt_done else KMR.strings.egg_hunt_rewards),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EggikuEggHunt.Egg.entries.filter { it.name !in foundRaw }.forEach { egg ->
                Text("• " + stringResource(egg.hint), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** A faint little egg tucked into a screen; tap it to find it. Gone once found */
@Composable
fun EggikuHiddenEgg(egg: EggikuEggHunt.Egg, modifier: Modifier = Modifier, alignment: Alignment = Alignment.CenterEnd) {
    val found by EggikuEggHunt.foundPref().collectAsState()
    if (egg.name in found) return
    val context = LocalContext.current
    val view = LocalView.current
    Box(modifier = modifier.fillMaxWidth().padding(8.dp), contentAlignment = alignment) {
        Icon(
            imageVector = Icons.Outlined.Egg,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
            modifier = Modifier
                .size(14.dp)
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    EggikuEggHunt.find(context, egg)
                },
        )
    }
}
// KMK <--
