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

    // Shader strength at 100% on the slider. Kept modest so grainy scans don't fall apart.
    private const val MAX_AMOUNT = 1.2f

    private const val SHADER = """
        uniform shader content;
        uniform float amount;
        uniform float2 size;

        half4 at(float2 p) {
            return content.eval(clamp(p, float2(0.5), size - float2(0.5)));
        }

        half4 main(float2 c) {
            half4 o = at(c);
            half4 n = at(c + float2(1.0, 0.0)) + at(c - float2(1.0, 0.0)) +
                at(c + float2(0.0, 1.0)) + at(c - float2(0.0, 1.0));
            half3 sharp = o.rgb + (o.rgb * 4.0 - n.rgb) * amount * 0.25;
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
            setFloatUniform("size", view.width.toFloat(), view.height.toFloat())
        }
        view.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "content"))
    }
}
// KMK <--
