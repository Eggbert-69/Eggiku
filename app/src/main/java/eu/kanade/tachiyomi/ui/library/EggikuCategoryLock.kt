package eu.kanade.tachiyomi.ui.library

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

/**
 * Eggiku: locks chosen library categories behind the phone's fingerprint, face or PIN.
 * Locked categories stay hidden until unlocked, and their entries are always left out of
 * Continue reading, reading recaps and shared recap cards.
 */
object EggikuCategoryLock {

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }

    /** Category ids, as strings */
    fun lockedCategories() = preferenceStore.getStringSet("eggiku_locked_categories", emptySet())

    /** Minutes a category stays unlocked; 0 = until you leave the app */
    fun unlockMinutes() = preferenceStore.getInt("eggiku_locked_categories_minutes", 0)

    private val unlockedUntil = MutableStateFlow(0L)

    /** Emits whenever the lock state may have changed */
    val unlockedState: StateFlow<Long> = unlockedUntil.asStateFlow()

    fun isLocked(categoryId: Long): Boolean = categoryId.toString() in lockedCategories().get()

    fun isUnlocked(now: Long = System.currentTimeMillis()): Boolean = now < unlockedUntil.value

    /** True when [categoryId] is locked and hasn't been unlocked */
    fun isHidden(categoryId: Long): Boolean = isLocked(categoryId) && !isUnlocked()

    /** True when any of the entry's categories is locked, whether or not it's unlocked right now */
    fun isPrivate(categoryIds: Collection<Long>): Boolean {
        val locked = lockedCategories().get()
        if (locked.isEmpty()) return false
        return categoryIds.any { it.toString() in locked }
    }

    fun unlock() {
        val minutes = unlockMinutes().get()
        unlockedUntil.value = if (minutes <= 0) {
            Long.MAX_VALUE
        } else {
            System.currentTimeMillis() + minutes * 60_000L
        }
    }

    fun lockNow() {
        unlockedUntil.value = 0L
    }

    /** Called when the app goes to the background */
    fun onAppStopped() {
        if (unlockMinutes().get() <= 0) lockNow()
    }
}
// KMK <--
