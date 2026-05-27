package com.dancaai.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.dancaai.app.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), PoseLandmarkerHelper.LandmarkerListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    private val stepCounter = StepCounter()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else Toast.makeText(this, "Permissão de câmera necessária", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Configura callbacks do contador de passos
        stepCounter.onStageChanged = { stage, count ->
            runOnUiThread {
                binding.tvCounter.text = count.toString()
                binding.tvStage.text = stage
            }
        }
        stepCounter.onThreeRepsCompleted = {
            runOnUiThread {
                binding.tvMessage.text = "3 REPS CONCLUÍDAS!"
            }
        }

        // Solicita permissão de câmera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // CAMERA FRONTAL
   /* private fun startCamera() {
        poseLandmarkerHelper = PoseLandmarkerHelper(context = this, listener = this)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        poseLandmarkerHelper.detectLiveStream(
                            imageProxy = imageProxy,
                            // 1. Alterado de false para true aqui:
                            isFrontCamera = true
                        )
                    }
                }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                // 2. Alterado de DEFAULT_BACK_CAMERA para DEFAULT_FRONT_CAMERA aqui:
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                imageAnalyzer
            )
        }, ContextCompat.getMainExecutor(this))
    }*/

    private fun startCamera() {
        poseLandmarkerHelper = PoseLandmarkerHelper(context = this, listener = this)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        poseLandmarkerHelper.detectLiveStream(
                            imageProxy = imageProxy,
                            isFrontCamera = false
                        )
                    }
                }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalyzer
            )
        }, ContextCompat.getMainExecutor(this))
    }

    // Chamado a cada frame processado pelo MediaPipe
    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        runOnUiThread {
            // Atualiza o overlay com os landmarks desenhados
            binding.overlayView.setResults(
                poseLandmarkerResults = resultBundle.results,
                imageWidth = resultBundle.inputImageWidth,
                imageHeight = resultBundle.inputImageHeight
            )
            // Processa a lógica de contagem de passos
            stepCounter.process(resultBundle.results)
        }
    }

    override fun onError(error: String) {
        runOnUiThread {
            Toast.makeText(this, "Erro: $error", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        poseLandmarkerHelper.clearPoseLandmarker()
        cameraExecutor.shutdown()
    }
}
