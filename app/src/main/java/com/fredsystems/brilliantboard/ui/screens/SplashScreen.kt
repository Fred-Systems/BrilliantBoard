package com.fredsystems.brilliantboard.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fredsystems.brilliantboard.BuildConfig
import com.fredsystems.brilliantboard.R
import com.fredsystems.brilliantboard.ui.theme.Accent
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinish: () -> Unit) {
    var started by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (started) 1f else 0.65f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "splash-scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(800),
        label = "splash-alpha",
    )
    val pulse = rememberInfiniteTransition(label = "splash-pulse")
    val glow by pulse.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "glow",
    )
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        started = true
        repeat(20) { index ->
            progress = (index + 1) / 20f
            delay(80)
        }
        delay(150)
        onFinish()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF1B2430), Color(0xFF0B0C10), Color.Black),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.alpha(alpha),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher),
                contentDescription = "BrilliantBoard",
                modifier = Modifier.size((112 * scale * glow).dp),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "BrilliantBoard",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Accent,
            )
            Text(
                "Chess intelligence · Game review · Opponent prep",
                fontSize = 12.sp,
                color = Color(0xFF9AA0A6),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(32.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.width(180.dp).height(4.dp),
                color = Accent,
            )
            Spacer(Modifier.height(14.dp))
            Text("v${BuildConfig.VERSION_NAME}", color = Color(0xFF6B7280), fontSize = 11.sp)
        }
        CircularProgressIndicator(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp).size(18.dp),
            strokeWidth = 2.dp,
            color = Accent,
        )
    }
}
