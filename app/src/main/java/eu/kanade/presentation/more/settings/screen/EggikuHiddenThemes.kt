package eu.kanade.presentation.more.settings.screen

import android.app.Activity
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.delay
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.rememberReduceMotion
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->

/** Eggiku: unlocking the hidden themes other than Glib Glorb (which has its rocket) */
object EggikuHiddenThemes {

    private const val ARCADE_WORDS = "insert coin"

    fun isArcadeSecret(query: String) = query.trim().equals(ARCADE_WORDS, ignoreCase = true)

    /** Unlocks [theme], switches to it and says so */
    fun unlock(context: Context, theme: AppTheme) {
        val uiPreferences = Injekt.get<UiPreferences>()
        uiPreferences.unlockedHiddenThemes().set(uiPreferences.unlockedHiddenThemes().get() + theme.name)
        uiPreferences.appTheme().set(theme)
        context.toast(
            context.stringResource(
                KMR.strings.hidden_theme_found,
                context.stringResource(theme.titleRes ?: KMR.strings.theme_custom),
            ),
        )
        (context as? Activity)?.let { ActivityCompat.recreate(it) }
    }
}

private val NeonPink = Color(0xFFFF3EA5)
private val NeonCyan = Color(0xFF00E5FF)
private val CoinGold = Color(0xFFFFC23D)

/**
 * Eggiku: shown when you search "insert coin" in settings. A blinking arcade prompt and a coin;
 * tap the coin and it drops into the slot, the screen flashes, and Midnight Arcade switches on.
 */
@Composable
fun EggikuSecretCoin(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion()
    var inserted by remember { mutableStateOf(false) }
    val drop = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    val blink = rememberInfiniteTransition(label = "arcadeBlink")
    val blinkAlpha by blink.animateFloat(
        initialValue = 1f,
        targetValue = if (reduceMotion) 1f else 0.15f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "blink",
    )

    LaunchedEffect(inserted) {
        if (!inserted) return@LaunchedEffect
        if (!reduceMotion) {
            drop.animateTo(1f, tween(520, easing = FastOutLinearInEasing))
            flash.animateTo(1f, tween(120))
            flash.animateTo(0f, tween(260))
            delay(120)
        }
        EggikuHiddenThemes.unlock(context, AppTheme.MIDNIGHT_ARCADE)
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = stringResource(KMR.strings.arcade_insert_coin),
                color = NeonPink,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                modifier = Modifier.graphicsLayer { alpha = if (inserted) 1f else blinkAlpha },
            )
            Box(contentAlignment = Alignment.TopCenter) {
                // Coin slot
                Canvas(modifier = Modifier.size(120.dp, 160.dp)) {
                    val slotTop = size.height * 0.62f
                    drawRoundRect(
                        color = NeonCyan,
                        topLeft = Offset(size.width * 0.18f, slotTop),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.64f, size.height * 0.3f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                        style = Stroke(2.dp.toPx()),
                    )
                    drawLine(
                        color = NeonCyan,
                        start = Offset(size.width * 0.5f, slotTop + 10.dp.toPx()),
                        end = Offset(size.width * 0.5f, slotTop + size.height * 0.3f - 10.dp.toPx()),
                        strokeWidth = 3.dp.toPx(),
                    )
                }
                // The coin
                Canvas(
                    modifier = Modifier
                        .size(56.dp)
                        .graphicsLayer {
                            translationY = drop.value * 96.dp.toPx()
                            scaleX = 1f - drop.value * 0.75f
                            alpha = 1f - (drop.value - 0.8f).coerceAtLeast(0f) * 5f
                        }
                        .clickable(enabled = !inserted) { inserted = true },
                ) {
                    drawCircle(CoinGold)
                    drawCircle(Color(0xFFB8860B), style = Stroke(3.dp.toPx()))
                    drawCircle(Color(0xFFB8860B), radius = size.minDimension * 0.28f, style = Stroke(2.dp.toPx()))
                }
            }
            Text(
                text = stringResource(KMR.strings.arcade_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        // Screen flash when the coin lands
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (flash.value > 0f) drawRect(NeonPink, alpha = flash.value * 0.6f)
        }
    }
}
// KMK <--
