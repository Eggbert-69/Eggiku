package eu.kanade.tachiyomi.ui.reader.eggiku

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import androidx.lifecycle.LifecycleCoroutineScope
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/**
 * Eggiku live sharpening. A small GPU shader (an unsharp mask) runs over the whole reader area,
 * after zoom, so edges of line art look crisper at any zoom level. Needs Android 13 or newer;
 * older phones simply don't get it. Strength is saved separately for folded and unfolded.
 */
object EggikuSharpen {

    /** True while the "hold to compare" button is pressed, which shows the page without sharpening */
    val comparing = MutableStateFlow(false)

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    // Strength at 100% on the slider, and how wide (in screen pixels) the edges it looks for are.
    // Pages are usually stretched to fit the screen, so their edges are a few pixels wide; a
    // 1-pixel filter barely touches them, which is why the radius is wider than one pixel.
    private const val MAX_AMOUNT = 2.5f
    private const val RADIUS_PX = 2f

    // Unsharp mask: blur the page a little, then push every pixel away from its blurred self.
    // Tiny differences (scan grain) are left alone so noise doesn't get louder.
    private const val SHADER = """
        uniform shader content;
        uniform float amount;
        uniform float radius;
        uniform float2 size;

        half3 at(float2 p) {
            return content.eval(clamp(p, float2(0.5), size - float2(0.5))).rgb;
        }

        half3 ring(float2 c, float r) {
            half3 sides = at(c + float2(r, 0.0)) + at(c - float2(r, 0.0)) +
                at(c + float2(0.0, r)) + at(c - float2(0.0, r));
            half3 corners = at(c + float2(r, r)) + at(c + float2(-r, r)) +
                at(c + float2(r, -r)) + at(c + float2(-r, -r));
            return (sides * 2.0 + corners) / 12.0;
        }

        half4 main(float2 c) {
            half4 o = content.eval(c);
            half3 blur = o.rgb * 0.25 + ring(c, radius) * 0.5 + ring(c, radius * 2.0) * 0.25;
            half3 detail = o.rgb - blur;
            half3 grain = smoothstep(half3(0.004), half3(0.03), abs(detail));
            half3 sharp = o.rgb + detail * grain * amount;
            return half4(clamp(sharp, half3(0.0), half3(o.a)), o.a);
        }
    """

    fun attach(view: View, scope: LifecycleCoroutineScope) {
        if (!isSupported) return
        val preferences = Injekt.get<ReaderPreferences>()
        var current = 0
        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> apply(v, current) }
        combine(preferences.eggikuSharpen().changes(), comparing) { strength, compare ->
            if (compare) 0 else strength
        }
            .onEach {
                current = it
                apply(view, it)
            }
            .launchIn(scope)
    }

    private fun apply(view: View, strength: Int) {
        if (!isSupported) return
        applyEffect(view, strength)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun applyEffect(view: View, strength: Int) {
        if (strength <= 0 || view.width == 0 || view.height == 0) {
            view.setRenderEffect(null)
            return
        }
        val shader = RuntimeShader(SHADER).apply {
            setFloatUniform("amount", strength / 100f * MAX_AMOUNT)
            setFloatUniform("radius", RADIUS_PX)
            setFloatUniform("size", view.width.toFloat(), view.height.toFloat())
        }
        view.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "content"))
    }
}
// KMK <--
