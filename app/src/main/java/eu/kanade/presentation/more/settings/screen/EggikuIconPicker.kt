package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
/** Eggiku: pick the app's home screen icon, or let it change with the seasons */
@Composable
fun EggikuIconPicker() {
    val context = LocalContext.current
    var current by remember { mutableStateOf(EggikuAppIcons.current(context)) }
    var seasonal by remember { mutableStateOf(EggikuAppIcons.isSeasonal(context)) }

    fun pick(icon: EggikuAppIcons.Icon) {
        if (!EggikuAppIcons.isUnlocked(icon)) {
            context.toast(context.stringResource(KMR.strings.icon_locked, EggikuAppIcons.GOLDEN_AT))
            return
        }
        // Picking an icon yourself locks it, even if it's today's seasonal one
        EggikuAppIcons.set(context, icon)
        seasonal = false
        current = EggikuAppIcons.current(context)
        context.toast(context.stringResource(KMR.strings.icon_changed))
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(KMR.strings.icon_title), style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    seasonal = !seasonal
                    EggikuAppIcons.setSeasonal(context, seasonal)
                    current = EggikuAppIcons.current(context)
                }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(KMR.strings.icon_seasonal), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(
                        KMR.strings.icon_seasonal_summary,
                        stringResource(EggikuAppIcons.forDate().title),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = seasonal,
                onCheckedChange = {
                    seasonal = it
                    EggikuAppIcons.setSeasonal(context, it)
                    current = EggikuAppIcons.current(context)
                },
            )
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EggikuAppIcons.Icon.entries
                // The UFO stays a secret until it can land
                .filter { it != EggikuAppIcons.Icon.Ufo || EggikuAppIcons.isUnlocked(it) }
                .forEach { icon ->
                    IconChoice(
                        icon = icon,
                        selected = icon == current,
                        unlocked = EggikuAppIcons.isUnlocked(icon),
                        onClick = { pick(icon) },
                    )
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

@Composable
private fun IconChoice(icon: EggikuAppIcons.Icon, selected: Boolean, unlocked: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(icon) {
        ContextCompat.getDrawable(context, icon.drawable)?.toBitmap(144, 144)?.asImageBitmap()
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
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
                            width = if (selected) 3.dp else 0.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
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
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
// KMK <--
