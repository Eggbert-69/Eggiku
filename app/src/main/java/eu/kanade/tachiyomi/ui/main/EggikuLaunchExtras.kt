package eu.kanade.tachiyomi.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import eu.kanade.tachiyomi.data.eggikuupdate.EggikuUpdates
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.rememberReduceMotion
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

/**
 * Eggiku on launch, once the intro and any patch notes are out of the way: says when a newer
 * Eggiku build is out, then gives a new seasonal icon its little welcome.
 */
@Composable
fun EggikuLaunchExtras(isLaunch: Boolean, ready: Boolean, onOpenUpdates: () -> Unit) {
    val context = LocalContext.current
    val skipped = remember {
        Injekt.get<PreferenceStore>().getString(Preference.appStateKey("eggiku_update_launch_skipped"), "")
    }
    var update by remember { mutableStateOf<EggikuUpdates.Build?>(null) }
    var checked by rememberSaveable { mutableStateOf(!isLaunch) }
    var seasonIcon by remember { mutableStateOf<EggikuAppIcons.Icon?>(null) }

    LaunchedEffect(Unit) {
        if (checked) return@LaunchedEffect
        try {
            if (EggikuUpdates.autoCheck().get()) {
                val status = EggikuUpdates.check()
                val latest = status.latest
                if (status.updateAvailable && latest != null && skipped.get() != latest.tag) update = latest
            }
        } catch (_: Exception) {
            // Offline or GitHub's rate limit; the daily check will catch it
        }
        checked = true
        seasonIcon = try {
            EggikuAppIcons.pendingIntroduction(context)
        } catch (_: Exception) {
            null
        }
    }
    if (!ready || !checked) return

    val latest = update
    if (latest != null) {
        AlertDialog(
            onDismissRequest = { update = null },
            title = { Text(stringResource(KMR.strings.eggiku_update_launch_title)) },
            text = { Text(stringResource(KMR.strings.eggiku_update_launch_text, latest.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        update = null
                        onOpenUpdates()
                    },
                ) { Text(stringResource(KMR.strings.eggiku_update_launch_get)) }
            },
            dismissButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { update = null }) {
                        Text(stringResource(KMR.strings.eggiku_update_launch_later))
                    }
                    TextButton(
                        onClick = {
                            skipped.set(latest.tag)
                            update = null
                        },
                    ) { Text(stringResource(KMR.strings.eggiku_update_launch_skip)) }
                }
            },
        )
        return
    }

    val icon = seasonIcon ?: return
    EggikuSeasonPop(icon) {
        EggikuAppIcons.markIntroduced(icon)
        seasonIcon = null
    }
}

/** The new seasonal icon drops in with a little jiggle, then you tap it away */
@Composable
private fun EggikuSeasonPop(icon: EggikuAppIcons.Icon, onDone: () -> Unit) {
    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    val bitmap = remember(icon) {
        ContextCompat.getDrawable(context, icon.drawable)?.toBitmap(288, 288)?.asImageBitmap()
    }
    val scale = remember { Animatable(if (reduceMotion) 1f else 0.2f) }
    val wobble = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { fade.animateTo(1f, tween(200)) }
        if (!reduceMotion) {
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
            // Snappy settle with a small jiggle
            listOf(-9f, 7f, -4f, 2f, 0f).forEach { wobble.animateTo(it, tween(70)) }
        }
        delay(AUTO_DISMISS_MS)
        fade.animateTo(0f, tween(250))
        onDone()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                scope.launch {
                    fade.animateTo(0f, tween(180))
                    onDone()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = stringResource(icon.title),
                    modifier = Modifier
                        .size(128.dp)
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                            rotationZ = wobble.value
                        }
                        .clip(RoundedCornerShape(32.dp)),
                )
            }
            Text(
                text = stringResource(KMR.strings.icon_season_arrived, stringResource(icon.title)),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(KMR.strings.icon_season_arrived_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val AUTO_DISMISS_MS = 4_000L
// KMK <--
