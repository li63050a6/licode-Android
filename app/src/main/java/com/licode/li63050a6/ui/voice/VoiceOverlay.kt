package com.licode.li63050a6.ui.voice

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.licode.li63050a6.data.VoiceInputController

@Composable
fun VoiceOverlay(
    state: VoiceInputController.State,
    level: Float,
    onCancel: () -> Unit,
) {
    var offsetY by remember { mutableFloatStateOf(0f) }
    val cancelThreshold = -120f

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        if (offsetY <= cancelThreshold) onCancel()
                        offsetY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetY += dragAmount.y
                    },
                )
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, (offsetY * 0.4f).toInt()) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(120.dp))
            Text(
                if (offsetY > cancelThreshold) "松手发送，上滑取消" else "松手取消",
                color = if (offsetY > cancelThreshold) Color.White else Color(0xFFFF6B6B),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(24.dp))
            VoiceWaves(level = level)
            Spacer(Modifier.height(24.dp))
            if (state == VoiceInputController.State.识别中) {
                Text("识别中…", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
            }
            if (offsetY <= cancelThreshold) {
                Icon(Icons.Default.Close, contentDescription = "取消", tint = Color(0xFFFF6B6B), modifier = Modifier.size(40.dp))
            } else {
                Box(
                    modifier = Modifier.size(40.dp).background(Color.White.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center,
                    content = { Box(Modifier.size(16.dp).background(Color.Red, CircleShape)) },
                )
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
fun VoiceWaves(level: Float) {
    val transition = rememberInfiniteTransition(label = "wave")
    val base = 0.4f + level * 0.6f
    val wave1 by transition.animateFloat(
        initialValue = base, targetValue = base * 1.6f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "w1",
    )
    val wave2 by transition.animateFloat(
        initialValue = base, targetValue = base * 1.8f,
        animationSpec = infiniteRepeatable(tween(350), RepeatMode.Reverse), label = "w2",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CircleWave(scale = wave1, color = Color(0xFF4D6BFE))
        CircleWave(scale = wave2, color = Color(0xFF7C8CFE))
    }
}

@Composable
fun CircleWave(scale: Float, color: Color) {
    val size = (56 * scale).dp
    Box(Modifier.size(size).alpha(0.6f + scale * 0.3f).background(color, CircleShape))
}