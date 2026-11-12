package eu.kanade.tachiyomi.data.stats

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.notify
import eu.kanade.tachiyomi.util.system.workManager
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

// KMK -->

/**
 * Eggiku: runs once each evening. On Sundays it sends the weekly recap, and if the nightly
 * reminder is on it nudges you when today hasn't counted toward your streak yet.
 */
class EggikuStatsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val log = EggikuReadingLog.all()
        val today = EggikuReadingLog.today()
        val streak = EggikuReadingLog.streak(log, today)

        if (EggikuReadingLog.nightlyReminder().get() && streak.current > 0 && !streak.readToday) {
            applicationContext.notify(ID_REMINDER, Notifications.CHANNEL_COMMON) {
                setSmallIcon(R.drawable.ic_komikku)
                setColor(ContextCompat.getColor(applicationContext, R.color.ic_launcher))
                setContentTitle(applicationContext.stringResource(KMR.strings.stats_reminder_title))
                setContentText(
                    applicationContext.pluralStringResource(
                        KMR.plurals.stats_reminder_text,
                        streak.current,
                        streak.current,
                    ),
                )
                setContentIntent(openApp())
                setAutoCancel(true)
            }
        }

        val sentWeek = Injekt.get<PreferenceStore>()
            .getString(Preference.appStateKey("eggiku_stats_recap_sent_week"), "")
        val weekStart = EggikuReadingLog.weekStart(today)
        if (EggikuReadingLog.weeklyRecap().get() &&
            today.dayOfWeek == DayOfWeek.SUNDAY &&
            sentWeek.get() != weekStart.toString()
        ) {
            val week = EggikuReadingLog.sum(log, weekStart, today)
            if (week.chapters > 0 || week.seconds > 0) {
                val previous = EggikuReadingLog.sum(log, weekStart.minusWeeks(1), weekStart.minusDays(1))
                val text = applicationContext.stringResource(
                    KMR.strings.stats_weekly_recap_text,
                    week.chapters,
                    week.seconds / 60,
                    previous.chapters,
                    streak.current,
                )
                applicationContext.notify(ID_WEEKLY_RECAP, Notifications.CHANNEL_COMMON) {
                    setSmallIcon(R.drawable.ic_komikku)
                    setColor(ContextCompat.getColor(applicationContext, R.color.ic_launcher))
                    setContentTitle(applicationContext.stringResource(KMR.strings.stats_weekly_recap_title))
                    setContentText(text)
                    setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
                    setContentIntent(openApp())
                    setAutoCancel(true)
                }
            }
            sentWeek.set(weekStart.toString())
        }
        return Result.success()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        applicationContext,
        ID_WEEKLY_RECAP,
        Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val TAG = "EggikuStats"
        private const val ID_REMINDER = -1901
        private const val ID_WEEKLY_RECAP = -1902

        /** Runs every day around 8 PM */
        fun setup(context: Context) {
            val now = LocalDateTime.now()
            var next = now.withHour(20).withMinute(0).withSecond(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMinutes()
            val request = PeriodicWorkRequestBuilder<EggikuStatsWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .addTag(TAG)
                .build()
            context.workManager.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
// KMK <--
