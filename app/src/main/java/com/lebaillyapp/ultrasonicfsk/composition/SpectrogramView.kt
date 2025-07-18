package com.lebaillyapp.ultrasonicfsk.composition


import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SpectrogramView(history: List<List<Float>>) {
    // Fonction pour normaliser la teinte entre 0 et 360
    fun safeHue(hue: Float): Float {
        var h = hue % 360f
        if (h < 0f) h += 360f
        return h
    }

    Canvas(modifier = Modifier
        .fillMaxWidth()
        .height(120.dp)
    ) {
        val timeSteps = history.size
        val freqBins = history.firstOrNull()?.size ?: 0
        if (timeSteps == 0 || freqBins == 0) return@Canvas

        val cellWidth = size.width / timeSteps
        val cellHeight = size.height / freqBins

        history.forEachIndexed { t, fft ->
            fft.forEachIndexed { f, amp ->
                val rawHue = 200f - amp * 200f // teinte selon intensité
                val hue = safeHue(rawHue)     // normalisation
                val color = Color.hsv(
                    hue = hue,
                    saturation = 1f,
                    value = amp.coerceIn(0f, 1f)
                )
                drawRect(
                    color = color,
                    topLeft = Offset(t * cellWidth, size.height - (f + 1) * cellHeight),
                    size = Size(cellWidth, cellHeight)
                )
            }
        }
    }
}