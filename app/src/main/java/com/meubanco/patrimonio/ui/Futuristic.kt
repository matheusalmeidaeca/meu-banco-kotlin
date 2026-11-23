package com.meubanco.patrimonio.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection

/** Formas e movimento do visual futurista: cantos chanfrados, borda neon e transições entre telas. */
val NeonCyan = Color(0xFF22D3EE)

/** Retângulo com dois cantos cortados (superior esquerdo e inferior direito). */
class CutShape(private val cut: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        return Outline.Generic(Path().apply {
            moveTo(c, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height - c)
            lineTo(size.width - c, size.height); lineTo(0f, size.height); lineTo(0f, c); close()
        })
    }
}

@Composable
fun neonBrush(strong: Boolean = false): Brush =
    Brush.linearGradient(listOf(P.brand.copy(alpha = if (strong) 0.95f else 0.55f), P.line, NeonCyan.copy(alpha = if (strong) 0.85f else 0.4f)))

@Composable
fun Modifier.neonBorder(shape: Shape, strong: Boolean = false): Modifier = border(1.dp, neonBrush(strong), shape)

/** Liga/desliga animações (Configurações). Também respeita a escala de animação do Android (desligada = sem movimento). */
object Fx {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("admoney_flags", Context.MODE_PRIVATE)
    fun userOn(ctx: Context) = prefs(ctx).getBoolean("motion", true)
    fun setUserOn(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("motion", on).apply()
    fun systemOn(ctx: Context) = try { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f } catch (e: Exception) { true }
}

@Composable
fun motionEnabled(): Boolean { val ctx = LocalContext.current; return remember { Fx.userOn(ctx) && Fx.systemOn(ctx) } }

/** Troca de tela com deslize, escala e esmaecer. A direção segue a ordem da lista (avançar entra pela direita). */
@Composable
fun <T> ScreenTransition(target: T, order: List<T>, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    if (!motionEnabled()) { content(target); return }
    AnimatedContent(
        targetState = target, modifier = modifier, label = "tela",
        transitionSpec = {
            val dir = if (order.indexOf(targetState) >= order.indexOf(initialState)) 1 else -1
            (slideInHorizontally(tween(360, easing = FastOutSlowInEasing)) { it / 7 * dir } +
                fadeIn(tween(300, delayMillis = 70)) + scaleIn(tween(360, easing = FastOutSlowInEasing), initialScale = 0.95f)) togetherWith
                (slideOutHorizontally(tween(260, easing = FastOutLinearInEasing)) { -it / 9 * dir } + fadeOut(tween(160)) + scaleOut(tween(260), targetScale = 0.97f)) using
                SizeTransform(clip = false) { _, _ -> snap() }
        },
    ) { content(it) }
}
