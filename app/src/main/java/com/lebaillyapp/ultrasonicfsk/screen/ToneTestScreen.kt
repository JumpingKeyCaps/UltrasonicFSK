package com.lebaillyapp.ultrasonicfsk.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lebaillyapp.ultrasonicfsk.viewmodel.ToneTestViewModel
import kotlinx.coroutines.delay
import kotlin.math.sin

@Composable
fun ToneTestScreen(
    navController: NavController,
    viewModel: ToneTestViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val freq0 by viewModel.freq0.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(56.dp))
        Text("Fréquence 0 : ${freq0.toInt()} Hz", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = freq0.toFloat(),
            onValueChange = { viewModel.updateFreq0(it.toDouble()) },
            valueRange = 20f..20000f,
            steps = 50
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { viewModel.playTone() },
            enabled = !isPlaying,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Jouer la fréquence 3s")
        }

        Spacer(modifier = Modifier.height(24.dp))

        FrequencyVisualizer(freq = freq0, isPlaying = isPlaying)
    }
}

@Composable
fun FrequencyVisualizer(freq: Double, isPlaying: Boolean) {
    var phase by remember { mutableStateOf(0.0) }

    LaunchedEffect(isPlaying, freq) {
        while (isPlaying) {
            phase += 0.1
            delay(16)
        }
        if (!isPlaying) phase = 0.0
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        val waveLengthPx = ((44100f / freq).toFloat() * 20f).coerceAtLeast(1f)
        val amplitude = height / 3

        val path = Path()

        // Premier point
        path.moveTo(0f, centerY)

        for (x in 0..width.toInt()) {
            val y = centerY + amplitude * sin((x / waveLengthPx + phase) * 2 * Math.PI).toFloat()
            path.lineTo(x.toFloat(), y)
        }

        drawPath(
            path = path,
            color = Color.DarkGray,
            style = Stroke(width = 1f, cap = StrokeCap.Round)
        )
    }
}