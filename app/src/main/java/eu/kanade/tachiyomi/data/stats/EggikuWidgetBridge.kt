package eu.kanade.tachiyomi.data.stats

import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.presentation.widget.eggiku.EggikuReadingWidget
import tachiyomi.presentation.widget.eggiku.EggikuWidgetData

// KMK -->

/** Eggiku: keeps the home screen reading widget's numbers and covers up to date */
object EggikuWidgetBridge {

    fun updateStats() {
        launchIO {
            try {
                val log = EggikuReadingLog.all()
                val today = EggikuReadingLog.today()
                val streak = EggikuReadingLog.streak(log, today)
                val week = EggikuReadingLog.sum(log, EggikuReadingLog.weekStart(today), today)
                EggikuWidgetData.streak().set(streak.current)
                EggikuWidgetData.readToday().set(streak.readToday)
                EggikuWidgetData.weekChapters().set(week.chapters)
                EggikuWidgetData.weekGoal().set(EggikuReadingLog.weeklyGoalChapters().get())
                EggikuReadingWidget.refresh()
            } catch (_: Exception) {
                // The widget is a nice-to-have; never let it break reading
            }
        }
    }

    fun updateContinue(mangaIds: List<Long>) {
        val value = mangaIds.take(4).joinToString(",")
        if (EggikuWidgetData.continueIds().get() == value) return
        EggikuWidgetData.continueIds().set(value)
        launchIO { EggikuReadingWidget.refresh() }
    }
}
// KMK <--
