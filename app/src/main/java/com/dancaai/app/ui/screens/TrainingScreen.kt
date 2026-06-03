package com.dancaai.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import android.widget.Toast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dancaai.app.camera.PoseCameraView
import com.dancaai.app.ui.components.DcaFilledButton
import com.dancaai.app.ui.components.DcaOutlinedButton
import com.dancaai.app.ui.theme.DcaTheme
import com.dancaai.app.ui.theme.MonoFontFamily
import com.dancaai.app.ui.theme.Shapes
import kotlinx.coroutines.delay

private const val TOTAL_SECONDS = 5 * 60

private data class LandmarkXYZ(val x: Float, val y: Float, val z: Float)

private data class DebugSnapshot(
    val number:         Int,
    val nose:           LandmarkXYZ,
    val leftEar:        LandmarkXYZ,
    val rightEar:       LandmarkXYZ,
    val leftShoulder:   LandmarkXYZ,
    val rightShoulder:  LandmarkXYZ,
    val leftHip:        LandmarkXYZ,
    val rightHip:       LandmarkXYZ,
    val leftKnee:       LandmarkXYZ,
    val rightKnee:      LandmarkXYZ,
    val leftAnkle:      LandmarkXYZ,
    val rightAnkle:     LandmarkXYZ,
    // métricas derivadas
    val shoulderSpan: Float,
    val zDiff:        Float,  // avgShoulderZ − avgHipZ
)

private fun List<DebugSnapshot>.toClipboardText(): String {
    val lines = mutableListOf("DancaAI — Dados de Debug ($size captura${if (size != 1) "s" else ""})", "")
    forEach { snap ->
        lines += "Captura #${snap.number}"
        for ((label, xyz) in listOf(
            "NAR  " to snap.nose,
            "ORE-E" to snap.leftEar,       "ORE-D" to snap.rightEar,
            "OMB-E" to snap.leftShoulder,  "OMB-D" to snap.rightShoulder,
            "QDR-E" to snap.leftHip,       "QDR-D" to snap.rightHip,
            "JOE-E" to snap.leftKnee,      "JOE-D" to snap.rightKnee,
            "TRN-E" to snap.leftAnkle,     "TRN-D" to snap.rightAnkle,
        )) {
            lines += "  $label  x=%.3f  y=%.3f  z=%.3f".format(xyz.x, xyz.y, xyz.z)
        }
        lines += "  CALC   span=%.3f  Zdiff=%.3f  thr=-0.23".format(
            snap.shoulderSpan, snap.zDiff)
        lines += ""
    }
    return lines.joinToString("\n")
}

private fun List<NormalizedLandmark>.toSnapshot(n: Int): DebugSnapshot {
    fun at(i: Int): LandmarkXYZ {
        val lm = getOrNull(i)
        return LandmarkXYZ(lm?.x() ?: 0f, lm?.y() ?: 0f, lm?.z() ?: 0f)
    }
    val lS = getOrNull(11); val rS = getOrNull(12)
    val lH = getOrNull(23); val rH = getOrNull(24)
    val span  = if (lS != null && rS != null) kotlin.math.abs(rS.x() - lS.x()) else 0f
    val zDiff = if (lS != null && rS != null && lH != null && rH != null)
        (lS.z() + rS.z()) / 2f - (lH.z() + rH.z()) / 2f else 0f
    return DebugSnapshot(
        number        = n,
        nose          = at(0),
        leftEar       = at(7),  rightEar      = at(8),
        leftShoulder  = at(11), rightShoulder = at(12),
        leftHip       = at(23), rightHip      = at(24),
        leftKnee      = at(25), rightKnee     = at(26),
        leftAnkle     = at(27), rightAnkle    = at(28),
        shoulderSpan  = span,
        zDiff         = zDiff,
    )
}

/**
 * Tela de Treino: feed real da câmera + esqueleto MediaPipe (PoseCameraView)
 * com HUD em Compose por cima. Scores e beat são mockados/animados — a pontuação
 * real (biomecânica/ritmo) será conectada quando implementada.
 */
@Composable
fun TrainingScreen(onEnd: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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

    LaunchedEffect(paused) {
        while (!paused) {
            delay(1000); elapsed++
        }
    }

    val cameraViewRef = remember { mutableStateOf<PoseCameraView?>(null) }
    var poseUnavailable by remember { mutableStateOf(false) }
    val snapshots = remember { mutableStateListOf<DebugSnapshot>() }
    var showDebugResults by remember { mutableStateOf(false) }

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

        // ── HUD superior: timer ──────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            TimerControl(elapsed, paused) { paused = !paused }
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

        // ── HUD inferior: registrar + encerrar ──────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RegisterButton(
                count = snapshots.size,
                onClick = {
                    cameraViewRef.value?.currentLandmarks?.let { lm ->
                        snapshots.add(lm.toSnapshot(snapshots.size + 1))
                    }
                },
            )
            EndButton {
                if (snapshots.isEmpty()) onEnd()
                else showDebugResults = true
            }
        }

        // veil de pausa
        if (paused) {
            PausedVeil(onResume = { paused = false }, onEnd = onEnd)
        }

        // resultados de debug (substitui a tela após encerrar)
        if (showDebugResults) {
            DebugResultsOverlay(snapshots = snapshots, onClose = onEnd)
        }
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
                    .background(DcaTheme.colors.accent),
            )
        }
    }
}

@Composable
private fun RegisterButton(count: Int, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(44.dp)
            .clip(Shapes.pill)
            .background(DcaTheme.colors.accent.copy(alpha = 0.9f))
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp),
    ) {
        Text(
            if (count == 0) "Registrar" else "Registrar  ($count)",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
    }
}

@Composable
private fun DebugResultsOverlay(snapshots: List<DebugSnapshot>, onClose: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context   = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF0141418))
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            "Dados Registrados — ${snapshots.size} captura${if (snapshots.size != 1) "s" else ""}",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            snapshots.forEach { snap -> SnapshotCard(snap) }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            DcaOutlinedButton(
                "Copiar tudo",
                onClick = {
                    clipboard.setText(AnnotatedString(snapshots.toClipboardText()))
                    Toast.makeText(context, "Copiado!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
            )
            DcaFilledButton(
                "Encerrar",
                onClick = onClose,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SnapshotCard(snap: DebugSnapshot) {
    val entries = listOf(
        "NAR  " to snap.nose,
        "ORE-E" to snap.leftEar,      "ORE-D" to snap.rightEar,
        "OMB-E" to snap.leftShoulder, "OMB-D" to snap.rightShoulder,
        "QDR-E" to snap.leftHip,      "QDR-D" to snap.rightHip,
        "JOE-E" to snap.leftKnee,     "JOE-D" to snap.rightKnee,
        "TRN-E" to snap.leftAnkle,    "TRN-D" to snap.rightAnkle,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Shapes.md)
            .background(Color(0xFF1E1E26))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "Captura #${snap.number}",
            fontWeight = FontWeight.Bold,
            color = DcaTheme.colors.accent,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        entries.forEach { (label, xyz) ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 12.sp, color = Color.Cyan, modifier = Modifier.width(48.dp))
                Text("x=%.3f  y=%.3f  z=%.3f".format(xyz.x, xyz.y, xyz.z),
                    fontFamily = MonoFontFamily, fontSize = 12.sp, color = Color.White)
            }
        }
        // linha de métricas derivadas
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("CALC ", fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, color = Color.Yellow, modifier = Modifier.width(48.dp))
            Text("span=%.3f  Zdiff=%.3f  thr=-0.23".format(
                    snap.shoulderSpan, snap.zDiff),
                fontFamily = MonoFontFamily, fontSize = 12.sp, color = Color.White)
        }
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
