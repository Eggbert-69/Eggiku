package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import eu.kanade.tachiyomi.ui.library.EggikuCategoryLock
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.authenticate
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.isAuthenticationSupported
import kotlinx.coroutines.launch
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.plus

// KMK -->

/** Eggiku: shown instead of a locked category's entries until it's unlocked */
@Composable
fun EggikuLockedCategory(
    categoryName: String,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val title = stringResource(KMR.strings.locked_category_unlock_title, categoryName)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding + PaddingValues(24.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(KMR.strings.locked_category_message, categoryName),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = {
                val activity = context as? FragmentActivity
                if (activity == null || !context.isAuthenticationSupported()) {
                    // No lock screen set up on the phone: nothing to check against
                    EggikuCategoryLock.unlock()
                    return@Button
                }
                scope.launch {
                    if (activity.authenticate(title = title, subtitle = null)) {
                        EggikuCategoryLock.unlock()
                    }
                }
            },
        ) {
            Text(stringResource(KMR.strings.locked_category_unlock))
        }
    }
}
// KMK <--
