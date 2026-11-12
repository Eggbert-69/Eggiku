package eu.kanade.tachiyomi.data.duplicates

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

/** Eggiku: after library updates, says when there are possible duplicates to look at */
object EggikuDuplicatesNotifier {

    private const val ID = -1904

    suspend fun check(context: Context) {
        val groups = EggikuDuplicates.findGroups()
        val lastCount = Injekt.get<PreferenceStore>()
            .getInt(Preference.appStateKey("eggiku_duplicates_notified_count"), 0)
        val count = groups.size
        if (count == 0 || count == lastCount.get()) {
            lastCount.set(count)
            return
        }
        lastCount.set(count)
        val intent = PendingIntent.getActivity(
            context,
            ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        context.notify(ID, Notifications.CHANNEL_COMMON) {
            setSmallIcon(R.drawable.ic_komikku)
            setColor(ContextCompat.getColor(context, R.color.ic_launcher))
            setContentTitle(context.stringResource(KMR.strings.duplicates_title))
            setContentText(context.pluralStringResource(KMR.plurals.duplicates_found, count, count))
            setContentIntent(intent)
            setAutoCancel(true)
        }
    }
}
// KMK <--
