package eu.kanade.presentation.browse.components

import android.text.format.DateUtils
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import eu.kanade.tachiyomi.data.library.EggikuSourceHealth
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

// KMK -->

/** Eggiku: a warning icon next to sources whose library updates keep failing */
@Composable
fun EggikuSourceHealthIcon(sourceId: Long) {
    val context = LocalContext.current
    val failing by EggikuSourceHealth.failing().collectAsState()
    val since = EggikuSourceHealth.parse(failing)[sourceId] ?: return

    IconButton(
        onClick = {
            val ago = DateUtils.getRelativeTimeSpanString(
                since,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            ).toString()
            context.toast(context.stringResource(KMR.strings.source_failing_message, ago))
        },
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = stringResource(KMR.strings.source_failing),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}
// KMK <--
