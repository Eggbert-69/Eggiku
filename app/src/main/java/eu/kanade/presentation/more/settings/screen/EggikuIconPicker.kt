package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import eu.kanade.tachiyomi.ui.main.EggikuAppIcons
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

// KMK -->
/** Eggiku: pick the app's home screen icon */
@Composable
fun EggikuIconPicker() {
    val context = LocalContext.current
    var current by remember { mutableStateOf(EggikuAppIcons.current(context)) }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(KMR.strings.icon_title), style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            EggikuAppIcons.Icon.entries.forEach { icon ->
                val unlocked = EggikuAppIcons.isUnlocked(icon)
                val bitmap = remember(icon) {
                    ContextCompat.getDrawable(context, icon.drawable)?.toBitmap(144, 144)?.asImageBitmap()
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            if (!unlocked) {
                                context.toast(context.stringResource(KMR.strings.icon_locked, EggikuAppIcons.GOLDEN_AT))
                            } else if (icon != current) {
                                EggikuAppIcons.set(context, icon)
                                current = EggikuAppIcons.current(context)
                                context.toast(context.stringResource(KMR.strings.icon_changed))
                            }
                        }
                        .padding(6.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        bitmap?.let {
                            Image(
                                bitmap = it,
                                contentDescription = stringResource(icon.title),
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(
                                        width = if (icon == current) 3.dp else 0.dp,
                                        color = if (icon == current) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                    .alpha(if (unlocked) 1f else 0.3f),
                            )
                        }
                        if (!unlocked) Text("🔒", fontSize = 20.sp)
                    }
                    Text(
                        text = stringResource(icon.title),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        Text(
            text = stringResource(KMR.strings.icon_hint, EggikuAppIcons.GOLDEN_AT),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
// KMK <--
