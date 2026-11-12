package eu.kanade.tachiyomi.data.music

import android.service.notification.NotificationListenerService

// KMK -->

/**
 * Eggiku: Android only lets an app see which music is playing (and control it) when it has
 * notification access. This service exists only for that permission. It never reads, stores or
 * acts on the user's notifications.
 */
class EggikuMediaListenerService : NotificationListenerService()
// KMK <--
