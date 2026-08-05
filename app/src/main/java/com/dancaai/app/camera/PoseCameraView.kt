package com.dancaai.app.camera

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.dancaai.app.AngleCalculator
import com.dancaai.app.OverlayView
import com.dancaai.app.PoseLandmarkerHelper
import com.dancaai.app.PostureValidator
import com.dancaai.app.StepCounter
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * View autocontida que une CameraX + MediaPipe Pose + OverlayView (esqueleto,
 * ângulos e postura). Reaproveita o código de visão existente e é embutida na
 * tela de Treino (Compose) via AndroidView.
 *
 * O cálculo de scores (Postura/Ritmo) ainda é mockado no HUD — esta view entrega
 * o feed real da câmera e o overlay de pose; a pontuação real virá depois.
 */
class PoseCameraView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs), PoseLandmarkerHelper.LandmarkerListener {

    private val previewView  = PreviewView(context)
    private val overlayView  = OverlayView(context, null)
    private val stepCounter  = StepCounter()
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var poseHelper: PoseLandmarkerHelper? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var isFrontCamera = false

    // ProcessCameraProvider.getInstance() é assíncrono (a 1ª chamada demora um
    // pouco pra resolver). Se switchCamera()/bind() for chamado de novo antes do
    // callback anterior rodar — típico de um toque duplo logo no início do treino,
    // antes da câmera terminar de abrir —, duas inicializações do PoseLandmarkerHelper
    // (que usa GPU) disparavam quase ao mesmo tempo e derrubavam o app. Esse contador
    // invalida qualquer callback que não seja mais o mais recente.
    private var bindGeneration = 0

    /** Notifica se há (ou não) uma pessoa detectada no enquadramento. */
    var onPersonDetected: ((Boolean) -> Unit)? = null

    /** Último frame de landmarks detectado; null quando nenhuma pessoa está no enquadramento. */
    var currentLandmarks: List<NormalizedLandmark>? = null
        private set

    /**
     * Chamado quando a detecção de pose não pôde ser inicializada — tipicamente
     * no emulador, onde o MediaPipe não tem biblioteca nativa para x86_64.
     * Nesse caso a câmera segue funcionando, só sem o esqueleto.
     */
    var onPoseUnavailable: (() -> Unit)? = null

    init {
        addView(previewView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(overlayView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        stepCounter.onWeightInfoChanged = { info -> overlayView.updateWeightInfo(info) }
    }

    fun bind(owner: LifecycleOwner) {
        lifecycleOwner = owner
        startCamera()
    }

    fun switchCamera() {
        isFrontCamera = !isFrontCamera
        startCamera()
    }

    private fun startCamera() {
        val owner = lifecycleOwner ?: return
        val myGeneration = ++bindGeneration
        poseHelper?.clearPoseLandmarker()

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            // uma chamada mais recente (outro toque em "trocar câmera") já assumiu — descarta esta.
            if (myGeneration != bindGeneration) return@addListener

            val cameraProvider = providerFuture.get()
            cameraProvider.unbindAll()

            // A inicialização do MediaPipe pode falhar por falta de lib nativa
            // (ex.: emulador x86_64). Tratamos para não derrubar o app — a câmera
            // continua, apenas sem o esqueleto de pose.
            poseHelper = try {
                PoseLandmarkerHelper(context = context, listener = this)
            } catch (t: Throwable) {
                onPoseUnavailable?.invoke()
                null
            }

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        poseHelper?.detectLiveStream(imageProxy = imageProxy, isFrontCamera = isFrontCamera)
                    }
                }

            val selector = if (isFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            cameraProvider.bindToLifecycle(owner, selector, preview, analyzer)
        }, ContextCompat.getMainExecutor(context))
    }

    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        post {
            overlayView.setResults(
                poseLandmarkerResults = resultBundle.results,
                imageWidth = resultBundle.inputImageWidth,
                imageHeight = resultBundle.inputImageHeight,
                isFrontCamera = isFrontCamera,
            )
            stepCounter.process(resultBundle.results)
            val landmarks = resultBundle.results.landmarks()
            val hasPerson = landmarks.isNotEmpty()
            onPersonDetected?.invoke(hasPerson)
            currentLandmarks = if (hasPerson) landmarks[0] else null
            if (hasPerson) {
                val first = landmarks[0]
                AngleCalculator.compute(first)?.let { overlayView.updateAngles(it) }
                overlayView.updatePosture(PostureValidator.validate(first))
            }
        }
    }

    override fun onError(error: String) {
        // Erros de inferência são tolerados frame a frame; nada a fazer aqui.
    }

    fun release() {
        stepCounter.reset()
        runCatching {
            poseHelper?.clearPoseLandmarker()
            poseHelper = null
            if (!cameraExecutor.isShutdown) cameraExecutor.shutdown()
            ProcessCameraProvider.getInstance(context).get().unbindAll()
        }
    }
}
