package com.dancaai.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dancaai.app.camera.PoseCameraView
import com.dancaai.app.data.MockRepository
import com.dancaai.app.ui.components.DcaFilledButton
import com.dancaai.app.ui.components.DcaOutlinedButton
import com.dancaai.app.ui.theme.MonoFontFamily
import com.dancaai.app.ui.theme.Shapes
import com.dancaai.app.ui.theme.scoreColor
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TOTAL_SECONDS = 5 * 60

/**
 * Tela de Treino: feed real da câmera + esqueleto MediaPipe (PoseCameraView)
 * com HUD em Compose por cima. Scores e beat são mockados/animados — a pontuação
 * real (biomecânica/ritmo) será conectada quando implementada.
 */
@Composable
fun TrainingScreen(onEnd: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val bpm = MockRepository.defaultMusic.bpm

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    LaunchedEffect(Unit) { if (!hasPermission) launcher.launch(Manifest.permission.CAMERA) }

    var paused by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var beat by remember { mutableIntStateOf(0) }

    LaunchedEffect(paused) {
        while (!paused) {
            delay(1000); elapsed++; tick++
        }
    }
    LaunchedEffect(paused) {
        val interval = (60_000L / bpm).coerceAtLeast(1)
        while (!paused) {
            delay(interval); beat++
        }
    }

    val posture = (70 + (sin(tick * 0.6) * 10).roundToInt()).coerceIn(0, 100)
    val rhythm = (80 + (cos(tick * 0.7) * 8).roundToInt()).coerceIn(0, 100)

    val cameraViewRef = remember { mutableStateOf<PoseCameraView?>(null) }
    var poseUnavailable by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0A0A0C))) {
        if (hasPermission) {
            AndroidView(
                factory = { ctx ->
                    PoseCameraView(ctx).also {
                        it.onPoseUnavailable = { poseUnavailable = true }
                        it.bind(lifecycleOwner)
                        cameraViewRef.value = it
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            DisposableEffect(Unit) {
                onDispose { cameraViewRef.value?.release() }
            }
        } else {
            PermissionPrompt(onGrant = { launcher.launch(Manifest.permission.CAMERA) }, onBack = onBack)
        }

        // ── HUD superior: scores + timer ─────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ScoreHud("Postura", posture)
            TimerControl(elapsed, paused) { paused = !paused }
            ScoreHud("Ritmo", rhythm, alignEnd = true)
        }

        // botão alternar câmera
        if (hasPermission) {
            HudGlassButton(
                icon = Icons.Rounded.FlipCameraAndroid,
                contentDescription = "Alternar câmera",
                onClick = { cameraViewRef.value?.switchCamera() },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 92.dp, end = 16.dp),
            )
        }

        // aviso: pose indisponível (ex.: emulador sem lib nativa)
        if (poseUnavailable) {
            Text(
                "Detecção de pose indisponível neste dispositivo — use um celular físico.",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 88.dp, start = 24.dp, end = 24.dp)
                    .clip(Shapes.pill)
                    .background(Color(0xFF141418).copy(alpha = 0.82f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // toast contextual
        AnimatedVisibility(
            visible = !paused,
            modifier = Modifier.align(Alignment.Center).padding(top = 80.dp),
        ) {
            ContextualToast(tick)
        }

        // ── HUD inferior: ritmo + encerrar ───────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RhythmIndicator(beat = beat, bpm = bpm, paused = paused)
            EndButton(onEnd)
        }

        // veil de pausa
        if (paused) {
            PausedVeil(onResume = { paused = false }, onEnd = onEnd)
        }
    }
}

@Composable
private fun ScoreHud(label: String, value: Int, alignEnd: Boolean = false) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start, modifier = Modifier.width(96.dp)) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f),
        )
        Text(
            "$value",
            fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 44.sp,
            color = scoreColor(value),
        )
    }
}

@Composable
private fun TimerControl(elapsed: Int, paused: Boolean, onPause: () -> Unit) {
    val mm = (elapsed / 60).toString().padStart(2, '0')
    val ss = (elapsed % 60).toString().padStart(2, '0')
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HudGlassButton(
            icon = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
            contentDescription = if (paused) "Continuar" else "Pausar",
            onClick = onPause,
        )
        Text(
            "$mm:$ss",
            fontFamily = MonoFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            color = Color.White,
            modifier = Modifier.clip(Shapes.pill).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 10.dp, vertical = 3.dp),
        )
        Box(
            modifier = Modifier.width(84.dp).height(3.dp).clip(Shapes.pill).background(Color.White.copy(alpha = 0.15f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((elapsed.toFloat() / TOTAL_SECONDS).coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(scoreColor(80)),
            )
        }
    }
}

@Composable
private fun RhythmIndicator(beat: Int, bpm: Int, paused: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        Text("BEAT", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
        Box(
            modifier = Modifier
                .size(if (!paused && beat % 2 == 0) 16.dp else 12.dp)
                .clip(CircleShape)
                .background(scoreColor(80)),
        )
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(16) { i ->
                val lit = (beat + i) % 4 == 0
                Box(
                    Modifier
                        .weight(1f)
                        .height(if (lit) 14.dp else 6.dp)
                        .clip(Shapes.sm)
                        .background(if (lit) scoreColor(80) else Color.White.copy(alpha = 0.2f)),
                )
            }
        }
        Text("$bpm bpm", fontFamily = MonoFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
    }
}

private data class Toast(val icon: ImageVector, val text: String)

@Composable
private fun ContextualToast(tick: Int) {
    val toasts = remember {
        listOf(
            Toast(Icons.Rounded.FitnessCenter, "Eleve o quadril"),
            Toast(Icons.Rounded.Timer, "Acompanhe o tempo"),
            Toast(Icons.Rounded.RotateRight, "Joelho esquerdo +5°"),
        )
    }
    val t = toasts[(tick / 5) % toasts.size]
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .clip(Shapes.pill)
            .background(Color(0xFF141418).copy(alpha = 0.82f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), Shapes.pill)
            .padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Icon(t.icon, contentDescription = null, tint = scoreColor(80), modifier = Modifier.size(20.dp))
        Text(t.text, style = MaterialTheme.typography.bodyLarge, color = Color.White)
    }
}

@Composable
private fun EndButton(onEnd: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .height(52.dp)
            .clip(Shapes.pill)
            .background(Color(0xFF141418).copy(alpha = 0.82f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), Shapes.pill)
            .clickable(onClick = onEnd)
            .padding(horizontal = 28.dp),
    ) {
        Icon(Icons.Rounded.StopCircle, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(20.dp))
        Text("Encerrar sessão", style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}

@Composable
private fun PausedVeil(onResume: () -> Unit, onEnd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Pause, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Pausado", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DcaOutlinedButton("Encerrar", onClick = onEnd)
            DcaFilledButton("Continuar", onClick = onResume, leadingIcon = Icons.Rounded.PlayArrow, height = 48)
        }
    }
}

@Composable
private fun HudGlassButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0xFF141418).copy(alpha = 0.7f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
            .clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            "Precisamos da câmera para analisar sua dança em tempo real.",
            style = MaterialTheme.typography.bodyLarge, color = Color.White, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        DcaFilledButton("Permitir câmera", onClick = onGrant)
        Spacer(Modifier.height(8.dp))
        DcaOutlinedButton("Voltar", onClick = onBack)
    }
}
