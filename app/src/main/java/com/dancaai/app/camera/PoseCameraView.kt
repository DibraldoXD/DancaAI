package com.dancaai.app.camera

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.dancaai.app.AngleCalculator
import com.dancaai.app.BuildConfig
import com.dancaai.app.OverlayView
import com.dancaai.app.PoseLandmarkerHelper
import com.dancaai.app.PostureResult
import com.dancaai.app.PostureValidator
import com.dancaai.app.StepCounter
import com.dancaai.app.WeightInfo
import com.dancaai.app.WeightLeg
import com.dancaai.app.analysis.MarkingTraceLogger
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * View autocontida que une CameraX + MediaPipe Pose + OverlayView (esqueleto,
 * ângulos e postura). Reaproveita o código de visão existente e é embutida na
 * tela de Treino (Compose) via AndroidView.
 *
 * Entrega o feed da câmera, o overlay de pose e os eventos dos módulos de análise
 * (postura, transferência de peso e o instante de cada transição) para o HUD.
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

    /**
     * Resultado do módulo de postura a cada frame, incluindo [PostureResult.Unknown]
     * quando não há pose válida. É o que alimenta o HUD e o acumulador da sessão.
     */
    var onPostureResult: ((PostureResult) -> Unit)? = null

    /**
     * Instante de cada troca de perna de apoio, na base de SystemClock.uptimeMillis,
     * carimbado na captura do frame — é a entrada do módulo de ritmo.
     */
    var onWeightTransition: ((leg: WeightLeg, atUptimeMs: Long) -> Unit)? = null

    /** Estado corrente do módulo de transferência de peso (contadores acumulados). */
    var onWeightInfo: ((WeightInfo) -> Unit)? = null

    /**
     * Um disparo por frame com pose válida, carimbado no instante da captura
     * (base de SystemClock.uptimeMillis). É o relógio da gravação de movimento:
     * amostrar pelo tempo do frame, e não pelo do callback, evita que o jitter
     * da inferência entre na grade temporal das amostras.
     */
    var onPoseFrame: ((frameTimeMs: Long) -> Unit)? = null

    /**
     * Um disparo por resultado do MediaPipe (com ou sem pessoa: a inferência rodou do
     * mesmo jeito): instante da entrega e latência da captura até o resultado, na base
     * de SystemClock.uptimeMillis. Alimenta o medidor de FPS e latência da sessão.
     */
    var onFrameTiming: ((deliveredAtMs: Long, latencyMs: Long) -> Unit)? = null

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
        // O painel de peso agora é desenhado pelo HUD em Compose; a OverlayView
        // cuida apenas do que precisa estar ancorado no corpo.
        stepCounter.onWeightInfoChanged = { info -> onWeightInfo?.invoke(info) }
        stepCounter.onWeightTransition = { leg, at -> onWeightTransition?.invoke(leg, at) }
        if (BuildConfig.DEBUG) startMarkingTrace()
    }

    private var traceLogger: MarkingTraceLogger? = null

    /**
     * Só no build debug: um CSV por sessão com o que o módulo de peso viu e decidiu em cada
     * frame (ver [MarkingTraceLogger]). Fica em `Android/data/<pacote>/files/marcacao/` e sai
     * do aparelho com `adb pull`. Falha aqui nunca derruba a câmera.
     */
    private fun startMarkingTrace() {
        runCatching {
            val dir = File(context.getExternalFilesDir(null), "marcacao").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val logger = MarkingTraceLogger(File(dir, "marcacao_$stamp.csv").bufferedWriter())
            traceLogger = logger
            stepCounter.onTrace = logger::onFrame
        }.onFailure { Log.w(MarkingTraceLogger.TAG, "Log de marcação indisponível", it) }
    }

    /** Batida do metrônomo no log de marcação (no-op fora do debug). */
    fun traceBeat(beatIndex: Int, audibleAtMs: Long) {
        traceLogger?.onBeat(beatIndex, audibleAtMs)
    }

    fun bind(owner: LifecycleOwner) {
        lifecycleOwner = owner
        startCamera()
    }

    /** Batida de pausa do metrônomo: o módulo de peso não avalia troca de perna na janela dela. */
    fun markPauseBeat(audibleAtMs: Long, intervalMs: Long, beatsPerBar: Int) =
        stepCounter.markPauseBeat(audibleAtMs, intervalMs, beatsPerBar)

    /** Metrônomo parado, pausado ou sem pausa no padrão: volta a regra geral de marcação. */
    fun clearPauseBeat() = stepCounter.clearPauseBeat()

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
            onFrameTiming?.invoke(resultBundle.deliveredAtMs, resultBundle.latencyMs)
            overlayView.setResults(
                poseLandmarkerResults = resultBundle.results,
                imageWidth = resultBundle.inputImageWidth,
                imageHeight = resultBundle.inputImageHeight,
                isFrontCamera = isFrontCamera,
            )
            // o instante da captura, e não o da inferência, é o que o ritmo precisa
            stepCounter.process(resultBundle.results, resultBundle.frameTimeMs)
            val landmarks = resultBundle.results.landmarks()
            val hasPerson = landmarks.isNotEmpty()
            onPersonDetected?.invoke(hasPerson)
            currentLandmarks = if (hasPerson) landmarks[0] else null

            // Sem pessoa no enquadramento o módulo não avalia nada, mas o HUD
            // precisa saber disso — daí o Unknown explícito em vez de silêncio.
            val posture = if (hasPerson) {
                val first = landmarks[0]
                AngleCalculator.compute(first)?.let { overlayView.updateAngles(it) }
                PostureValidator.validate(first)
            } else {
                PostureResult.Unknown
            }
            overlayView.updatePosture(posture)
            onPostureResult?.invoke(posture)
            // depois de currentLandmarks já estar atualizado: quem ouve este
            // callback captura o frame corrente, não o anterior
            if (hasPerson) onPoseFrame?.invoke(resultBundle.frameTimeMs)
        }
    }

    override fun onError(error: String) {
        // Erros de inferência são tolerados frame a frame; nada a fazer aqui.
    }

    fun release() {
        stepCounter.onTrace = null
        traceLogger?.close()
        traceLogger = null
        stepCounter.reset()
        runCatching {
            poseHelper?.clearPoseLandmarker()
            poseHelper = null
            if (!cameraExecutor.isShutdown) cameraExecutor.shutdown()
            ProcessCameraProvider.getInstance(context).get().unbindAll()
        }
    }
}
