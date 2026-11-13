package eu.kanade.presentation.avatar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.tachiyomi.data.avatar.EggikuAvatar
import eu.kanade.tachiyomi.data.avatar.EggikuAvatarCatalog
import eu.kanade.tachiyomi.data.avatar.EggikuAvatarCatalog.layers
import eu.kanade.tachiyomi.data.avatar.EggikuAvatarProgress
import eu.kanade.tachiyomi.data.avatar.EggikuAvatars
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

// KMK -->

const val EGGIKU_AVATAR_ASSETS = "file:///android_asset/avatar/"

fun eggikuColor(hex: String?, fallback: Color = Color(0xFFFFE3C2)): Color = try {
    if (hex == null) fallback else Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    fallback
}

@Composable
fun rememberEggikuAvatarCatalog(): EggikuAvatarCatalog.Catalog {
    val context = LocalContext.current
    return remember { EggikuAvatarCatalog.get(context.applicationContext) }
}

/** Your own avatar, updating when it's changed */
@Composable
fun rememberMyEggikuAvatar(): EggikuAvatar {
    val flow = remember { EggikuAvatars.changes() }
    val avatar by flow.collectAsState(initial = remember { EggikuAvatars.mine() })
    return avatar
}

/**
 * Draws an avatar from its layers. With [border], the rank border goes around it: the avatar sits
 * in the middle two thirds (80 of the border's 120 canvas).
 * [bounce] gives each newly changed layer a little pop, for the editor.
 */
@Composable
fun EggikuAvatarImage(
    avatar: EggikuAvatar,
    size: Dp,
    modifier: Modifier = Modifier,
    border: EggikuAvatarCatalog.Rank? = null,
    bounce: Boolean = false,
) {
    val catalog = rememberEggikuAvatarCatalog()
    val layers = remember(catalog, avatar) { catalog.layers(avatar) }
    val inner = if (border != null) size * (80f / 120f) else size
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(inner)
                .clip(CircleShape)
                .background(eggikuColor(avatar.bgColor)),
        ) {
            layers.forEach { path ->
                androidx.compose.runtime.key(path) {
                    AvatarLayer(path, bounce)
                }
            }
        }
        border?.files?.svg?.let { AvatarLayer(it, bounce = false) }
    }
}

@Composable
private fun AvatarLayer(path: String, bounce: Boolean) {
    val context = LocalContext.current
    val request = remember(path) {
        ImageRequest.Builder(context)
            .data(EGGIKU_AVATAR_ASSETS + path)
            .memoryCacheKey("eggiku-avatar:$path")
            .crossfade(false)
            .build()
    }
    val scale = remember { Animatable(if (bounce) 0.8f else 1f) }
    if (bounce) {
        LaunchedEffect(path) {
            scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
    )
}

/** Small avatar + name + rank, for lists */
@Composable
fun EggikuAvatarChip(
    avatar: EggikuAvatar,
    name: String,
    progress: EggikuAvatarProgress,
    modifier: Modifier = Modifier,
) {
    val catalog = rememberEggikuAvatarCatalog()
    val rank = progress.border(catalog, avatar)
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = modifier) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EggikuAvatarImage(avatar = avatar, size = 40.dp, border = rank)
            Column {
                Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    text = rankName(rank),
                    style = MaterialTheme.typography.labelSmall,
                    color = eggikuColor(rank?.accent, MaterialTheme.colorScheme.onSurfaceVariant),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun rankName(rank: EggikuAvatarCatalog.Rank?): String =
    rank?.name ?: stringResource(KMR.strings.avatar_rank_unknown)
// KMK <--
