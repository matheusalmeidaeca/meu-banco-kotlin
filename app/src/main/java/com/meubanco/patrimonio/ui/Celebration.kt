package com.meubanco.patrimonio.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.meubanco.patrimonio.domain.Celebration
import kotlin.random.Random

private class Piece(val x: Float, val speed: Float, val size: Float, val color: Color, val phase: Float)

@Composable
fun CelebrationDialog(c: Celebration, onDone: () -> Unit) {
    val pieces = remember { List(70) { Piece(Random.nextFloat(), 0.4f + Random.nextFloat() * 0.9f, 6f + Random.nextFloat() * 8f,
        Color(CHART_COLORS[Random.nextInt(CHART_COLORS.size)]), Random.nextFloat()) } }
    val t by rememberInfiniteTransition(label = "conf").animateFloat(0f, 1f, infiniteRepeatable(tween(3500, easing = LinearEasing)), label = "t")
    val pop = remember { Animatable(0.2f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 200f)) }
    Dialog(onDismissRequest = onDone) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(P.card), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                pieces.forEach { p ->
                    val y = ((t * p.speed + p.phase) % 1f) * size.height
                    drawRect(p.color, Offset(p.x * size.width, y), Size(p.size, p.size * 1.6f))
                }
            }
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text4(c.emoji, Modifier.scale(pop.value))
                Txt(c.title, style = T.title, bold = true, align = TextAlign.Center)
                Txt(c.text, align = TextAlign.Center)
                PrimaryButton("Valeu!") { onDone() }
            }
        }
    }
}

@Composable
private fun Text4(e: String, m: Modifier) = androidx.compose.material3.Text(e, m, fontSize = 64.sp)

